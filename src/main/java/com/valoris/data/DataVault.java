package com.valoris.data;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.valoris.Config;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.events.TeamGuard;
import com.valoris.moderation.ModerationData;
import com.valoris.moderation.ModerationService;
import com.valoris.skill.Skill;
import com.valoris.skill.SkillAttachments;
import com.valoris.skill.SkillData;
import com.valoris.valoris2;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps everything important safe, outside the world folder, in <server>/valoris_data/:
 * - backups/<time>/: every backups.intervalMinutes (and on shutdown) a full copy of the Valoris data (cities,
 *   kingdoms, technologies, wars, truces, sieges, conquests...), the moderation data, every player's skills and the
 *   FTB Teams / FTB Chunks data - as .dat for restoring and .snbt to read; the last backups.keep are kept;
 * - archive/: a record of every city and kingdom that is removed, with its technologies and residents;
 * - skills.json: every player's skill XP, kept up to date; a player whose own data comes back lower (lost or reset)
 *   gets it back when they join.
 * City membership is mirrored in the city record too: a resident the FTB team lost is put back when they join.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class DataVault {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    private static ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Valoris data vault");
        t.setDaemon(true);
        return t;
    });

    /** player -> skill id -> XP (the best known values) */
    private static final Map<UUID, Map<String, Double>> SKILLS = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();
    private static final Set<UUID> ORPHANS_REPORTED = new HashSet<>();
    private static long lastBackup;

    private DataVault() {}

    public static Path dir() {
        return FMLPaths.GAMEDIR.get().resolve("valoris_data");
    }

    private static Path backups() { return dir().resolve("backups"); }
    private static Path archive() { return dir().resolve("archive"); }
    private static Path skillsFile() { return dir().resolve("skills.json"); }

    private static synchronized String stamp() {
        return STAMP.format(new Date());
    }

    // ------------------------------------------------------------------ files

    /** Writes through a temporary file, so a crash never leaves a half-written file behind. */
    private static void writeAtomic(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeNbt(Path dir, String name, CompoundTag tag) throws IOException {
        Files.createDirectories(dir);
        Path tmp = dir.resolve(name + ".dat.tmp");
        NbtIo.writeCompressed(tag, tmp);
        Files.move(tmp, dir.resolve(name + ".dat"), StandardCopyOption.REPLACE_EXISTING);
        writeAtomic(dir.resolve(name + ".snbt"), NbtUtils.prettyPrint(tag).getBytes(StandardCharsets.UTF_8));
    }

    public static CompoundTag readNbt(Path file) throws IOException {
        return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
    }

    private static void copyTree(Path from, Path to) {
        if (!Files.isDirectory(from)) return;
        try (Stream<Path> walk = Files.walk(from)) {
            walk.forEach(src -> {
                try {
                    Path dst = to.resolve(from.relativize(src).toString());
                    if (Files.isDirectory(src)) Files.createDirectories(dst);
                    else Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    valoris2.LOGGER.warn("Backup: could not copy {}", src, e);
                }
            });
        } catch (IOException e) {
            valoris2.LOGGER.warn("Backup: could not copy {}", from, e);
        }
    }

    // ------------------------------------------------------------------ skills

    /** Called whenever a player's skills change: remembers the newest values. */
    public static void noteSkills(ServerPlayer p, SkillData data) {
        Map<String, Double> m = new HashMap<>();
        for (Skill s : Skill.values()) m.put(s.id(), data.xp(s));
        synchronized (SKILLS) {
            SKILLS.put(p.getUUID(), m);
            NAMES.put(p.getUUID(), p.getGameProfile().getName());
        }
    }

    private static void loadSkills() {
        synchronized (SKILLS) {
            SKILLS.clear();
            NAMES.clear();
            Path f = skillsFile();
            if (!Files.exists(f)) return;
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                root.entrySet().forEach(e -> {
                    UUID id = UUID.fromString(e.getKey());
                    JsonObject o = e.getValue().getAsJsonObject();
                    Map<String, Double> m = new HashMap<>();
                    o.getAsJsonObject("xp").entrySet().forEach(x -> m.put(x.getKey(), x.getValue().getAsDouble()));
                    SKILLS.put(id, m);
                    if (o.has("name")) NAMES.put(id, o.get("name").getAsString());
                });
            } catch (IOException | RuntimeException e) {
                valoris2.LOGGER.error("Could not read {} - the skill copy starts empty, the old file is kept as .broken", f, e);
                try {
                    Files.copy(f, f.resolveSibling("skills.json.broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static byte[] skillsJson() {
        JsonObject root = new JsonObject();
        synchronized (SKILLS) {
            SKILLS.forEach((id, m) -> {
                JsonObject o = new JsonObject();
                o.addProperty("name", NAMES.getOrDefault(id, "?"));
                JsonObject xp = new JsonObject();
                m.forEach(xp::addProperty);
                o.add("xp", xp);
                root.add(id.toString(), o);
            });
        }
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    /** A player whose skill XP came back lower than the copy (their data was lost or reset) gets it back. */
    private static void restoreSkills(ServerPlayer p) {
        Map<String, Double> saved;
        synchronized (SKILLS) {
            saved = SKILLS.get(p.getUUID());
        }
        SkillData data = p.getData(SkillAttachments.SKILLS);
        if (saved != null) {
            List<String> restored = new ArrayList<>();
            for (Skill s : Skill.values()) {
                double copy = saved.getOrDefault(s.id(), 0.0);
                if (copy > data.xp(s) + 0.5) {
                    restored.add(s.title + " " + Math.round(data.xp(s)) + " -> " + Math.round(copy));
                    data.setXp(s, copy);
                }
            }
            if (!restored.isEmpty()) {
                p.setData(SkillAttachments.SKILLS, data);
                com.valoris.tech.TechService.cacheSkills(p);
                p.sendSystemMessage(Component.literal("Your skills were restored from the server's copy.").withStyle(ChatFormatting.GOLD));
                ModerationService.toStaff(p.getServer(), Component.literal("Restored the skills of "
                        + p.getGameProfile().getName() + ": " + String.join(", ", restored)).withStyle(ChatFormatting.GOLD));
            }
        }
        noteSkills(p, data);
    }

    // ------------------------------------------------------------------ backups

    /** Takes a full backup now. Collects everything on the server thread, writes it on the vault thread. */
    public static Path backup(MinecraftServer server, String reason, boolean wait) {
        String name = stamp() + (reason.isEmpty() ? "" : "_" + reason);
        Path target = backups().resolve(name);
        var registries = server.registryAccess();
        CompoundTag valoris = ValorisData.get(server).save(new CompoundTag(), registries);
        CompoundTag moderation = ModerationData.get(server).save(new CompoundTag(), registries);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) noteSkills(p, p.getData(SkillAttachments.SKILLS));
        byte[] skills = skillsJson();
        Path world = server.getWorldPath(LevelResource.ROOT);
        int keep = Config.BACKUP_KEEP.get();
        lastBackup = System.currentTimeMillis();

        Runnable job = () -> {
            try {
                writeNbt(target, "valoris_data", valoris);
                writeNbt(target, "moderation", moderation);
                writeAtomic(target.resolve("skills.json"), skills);
                writeAtomic(skillsFile(), skills);
                copyTree(world.resolve("ftbteams"), target.resolve("ftbteams"));
                copyTree(world.resolve("ftbchunks"), target.resolve("ftbchunks"));
                prune(keep);
                valoris2.LOGGER.info("Valoris backup written: {}", target);
            } catch (IOException | RuntimeException e) {
                valoris2.LOGGER.error("Valoris backup failed: {}", target, e);
            }
        };
        if (wait) job.run();
        else io.submit(job);
        return target;
    }

    private static void prune(int keep) throws IOException {
        if (!Files.isDirectory(backups())) return;
        List<Path> all;
        try (Stream<Path> s = Files.list(backups())) {
            all = s.filter(Files::isDirectory).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        for (int i = 0; i < all.size() - keep; i++) {
            try (Stream<Path> walk = Files.walk(all.get(i))) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    public static List<String> listBackups() {
        if (!Files.isDirectory(backups())) return List.of();
        try (Stream<Path> s = Files.list(backups())) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted(Comparator.reverseOrder()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Puts the Valoris data of a backup back in place (cities, kingdoms, technologies, wars...). */
    public static void restore(MinecraftServer server, String backup) throws IOException {
        Path file = backups().resolve(backup).resolve("valoris_data.dat");
        if (!Files.exists(file)) throw new IOException("No such backup: " + backup);
        backup(server, "before-restore", true); // the current state is never lost either
        CompoundTag tag = readNbt(file);
        ValorisData restored = ValorisData.load(tag, server.registryAccess());
        server.overworld().getDataStorage().set("valoris_data", restored);
        restored.setDirty();
    }

    // ------------------------------------------------------------------ archive

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** Cities archived during this server run (a removal can reach the archive twice: before and after FTB's own events). */
    private static final Set<UUID> ARCHIVED = new HashSet<>();

    /** Everything about a city that is about to be removed: kept forever in archive/. */
    public static void archiveCity(MinecraftServer server, City city, String why) {
        if (!ARCHIVED.add(city.teamId)) return;
        CompoundTag tag = ValorisData.saveCity(city);
        tag.putString("archivedBecause", why);
        tag.putLong("archivedAt", System.currentTimeMillis());
        ValorisData data = ValorisData.get(server);
        data.kingdomOfCity(city.teamId).ifPresent(k -> tag.putString("kingdom", k.name));
        ListTag names = new ListTag();
        Set<UUID> people = new HashSet<>(city.members);
        FTBTeamsAPI.api().getManager().getTeamByID(city.teamId).ifPresent(t -> people.addAll(t.getMembers()));
        for (UUID id : people) names.add(StringTag.valueOf(ModerationService.nameOf(server, id)));
        tag.put("memberNames", names);
        Path file = archive().resolve(stamp() + "_city_" + safe(city.name));
        io.submit(() -> {
            try {
                writeNbt(file.getParent(), file.getFileName().toString(), tag);
            } catch (IOException e) {
                valoris2.LOGGER.error("Could not archive the city {}", city.name, e);
            }
        });
        valoris2.LOGGER.info("Archived the city {} ({}): {} technologies", city.name, why, city.techs.size());
    }

    public static void archiveKingdom(Kingdom k, ValorisData data) {
        CompoundTag tag = ValorisData.saveKingdom(k);
        tag.putLong("archivedAt", System.currentTimeMillis());
        ListTag cities = new ListTag();
        for (UUID id : k.cities) data.byTeam(id).ifPresent(c -> cities.add(ValorisData.saveCity(c)));
        tag.put("cityRecords", cities);
        Path file = archive().resolve(stamp() + "_kingdom_" + safe(k.name));
        io.submit(() -> {
            try {
                writeNbt(file.getParent(), file.getFileName().toString(), tag);
            } catch (IOException e) {
                valoris2.LOGGER.error("Could not archive the kingdom {}", k.name, e);
            }
        });
    }

    public static List<String> listArchive() {
        if (!Files.isDirectory(archive())) return List.of();
        try (Stream<Path> s = Files.list(archive())) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".dat"))
                    .map(n -> n.substring(0, n.length() - 4)).sorted(Comparator.reverseOrder()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The technologies recorded in an archive entry (a city, or every city of a kingdom). */
    public static Set<String> archivedTechs(String entry) throws IOException {
        CompoundTag tag = readNbt(archive().resolve(entry + ".dat"));
        Set<String> out = new java.util.LinkedHashSet<>();
        ListTag techs = tag.getList("techs", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < techs.size(); i++) out.add(techs.getString(i));
        for (net.minecraft.nbt.Tag raw : tag.getList("cityRecords", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            out.addAll(ValorisData.loadCity((CompoundTag) raw).techs);
        }
        return out;
    }

    // ------------------------------------------------------------------ membership

    /** A city whose FTB team cannot be found: kept, and the staff is told (once per server run). */
    public static void reportOrphan(MinecraftServer server, City city) {
        if (!ORPHANS_REPORTED.add(city.teamId)) return;
        valoris2.LOGGER.error("The FTB team of the city {} ({}) is missing. The city and its technologies are kept; "
                + "restore world/ftbteams from valoris_data/backups (server stopped) to bring the team back.", city.name, city.teamId);
        ModerationService.toStaff(server, Component.literal("The FTB team of the city " + city.name + " is missing! "
                + "Nothing was deleted. Restore world/ftbteams from valoris_data/backups (with the server stopped).")
                .withStyle(ChatFormatting.RED));
    }

    /** Keeps the city's member list in step with its FTB team (they only differ if FTB lost someone). */
    public static void syncMembers(MinecraftServer server) {
        ValorisData data = ValorisData.get(server);
        for (City c : data.all()) {
            Team team = FTBTeamsAPI.api().getManager().getTeamByID(c.teamId).orElse(null);
            if (team == null) {
                if (c.founded) reportOrphan(server, c);
                continue;
            }
            if (c.members.addAll(team.getMembers())) data.touch();
        }
    }

    public static void onJoinedParty(MinecraftServer server, UUID teamId, UUID player) {
        ValorisData data = ValorisData.get(server);
        data.byTeam(teamId).ifPresent(c -> { if (c.members.add(player)) data.touch(); });
    }

    /** Only Valoris (or the console) can take someone out of a city team, so this is always a real leave. */
    public static void onLeftParty(MinecraftServer server, UUID teamId, UUID player) {
        ValorisData data = ValorisData.get(server);
        data.byTeam(teamId).ifPresent(c -> { if (c.members.remove(player)) data.touch(); });
    }

    /** A resident whom the FTB team lost (and who is in no other city) is put back into their city. */
    private static void repairMembership(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        ValorisData data = ValorisData.get(server);
        var manager = FTBTeamsAPI.api().getManager();
        for (City c : List.copyOf(data.all())) {
            if (!c.members.contains(p.getUUID())) continue;
            Team team = manager.getTeamByID(c.teamId).orElse(null);
            if (!(team instanceof PartyTeam party) || party.getMembers().contains(p.getUUID())) continue;
            if (manager.getTeamForPlayer(p).map(Team::isPartyTeam).orElse(false)) {
                ModerationService.toStaff(server, Component.literal(p.getGameProfile().getName() + " is listed in the city "
                        + c.name + " but is now in another team; left as it is.").withStyle(ChatFormatting.YELLOW));
                continue;
            }
            try {
                TeamGuard.allow(() -> party.join(p));
                p.sendSystemMessage(Component.literal("You were put back into your city, " + c.name + ".").withStyle(ChatFormatting.GOLD));
                ModerationService.toStaff(server, Component.literal("Put " + p.getGameProfile().getName()
                        + " back into the city " + c.name + " (the FTB team had lost them).").withStyle(ChatFormatting.GOLD));
            } catch (Exception e) {
                valoris2.LOGGER.warn("Could not put {} back into {}", p.getGameProfile().getName(), c.name, e);
            }
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (io.isShutdown()) {
            io = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Valoris data vault");
                t.setDaemon(true);
                return t;
            });
        }
        ORPHANS_REPORTED.clear();
        loadSkills();
        syncMembers(event.getServer());
        lastBackup = System.currentTimeMillis();
        backup(event.getServer(), "start", false);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 200 != 0) return;
        if (System.currentTimeMillis() - lastBackup >= Config.BACKUP_INTERVAL_MINUTES.get() * 60_000L) {
            backup(event.getServer(), "", false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStopping(ServerStoppingEvent event) {
        backup(event.getServer(), "stop", true);
        io.shutdown();
        try {
            io.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        restoreSkills(p);
        repairMembership(p);
        // a ruler whose city lost its team hears about it too
        ValorisData.get(p.getServer()).byRuler(p.getUUID()).ifPresent(c -> {
            if (FTBTeamsAPI.api().getManager().getTeamByID(c.teamId).isEmpty()) {
                reportOrphan(p.getServer(), c);
                p.sendSystemMessage(Component.literal("Your city's team data is missing. Nothing was deleted - "
                        + "the staff has been told and can restore it.").withStyle(ChatFormatting.RED));
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) noteSkills(p, p.getData(SkillAttachments.SKILLS));
    }
}
