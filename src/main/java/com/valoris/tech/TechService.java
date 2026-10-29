package com.valoris.tech;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.realm.Realms;
import com.valoris.skill.Skill;
import com.valoris.skill.SkillData;
import com.valoris.skill.SkillService;
import com.valoris.util.Errors;
import com.valoris.util.Msg;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Technologies belong to a realm: a kingdom, or a city that is in no kingdom. Every city of a kingdom always knows
 * the same technologies (the union of what they brought in), so a city that later leaves keeps all of them.
 * The realm's leader (the king, or the ruler of an independent city) starts a research once the realm has the
 * prerequisites and the best levels among all its members meet the skill requirements; it completes after
 * researchMinutes of real time. One research at a time per realm, held by the kingdom's capital (the "holder").
 * The end time is saved to disk right away, so a crash does not lose progress.
 */
public final class TechService {
    /**
     * Ordinals are sent to the client (TechRow): 0 locked, 1 needs skills, 2 available, 3 researching, 4 known,
     * 5 its era has not been opened by the admins yet.
     */
    public enum Status { LOCKED, NEEDS_SKILLS, AVAILABLE, RESEARCHING, DONE, ERA_CLOSED }

    private TechService() {}

    // ------------------------------------------------------------------ skills of a city

    /** Remembers a player's skill levels so offline members still count for their city. */
    public static void cacheSkills(ServerPlayer player) {
        SkillData data = SkillService.data(player);
        int[] levels = new int[Skill.values().length];
        for (Skill s : Skill.values()) levels[s.ordinal()] = data.level(s);
        ValorisData.get(player.getServer()).cacheSkills(player.getUUID(), levels);
    }

    /** Best level of each skill among all members of the city's realm (every city of its kingdom), online or not. */
    public static int[] cityLevels(MinecraftServer server, City city) {
        int[] best = new int[Skill.values().length];
        java.util.Arrays.fill(best, 1);
        ValorisData data = ValorisData.get(server);
        for (City c : realmCities(data, city)) {
            FTBTeamsAPI.api().getManager().getTeamByID(c.teamId).ifPresent(team -> {
                for (UUID member : team.getMembers()) {
                    int[] levels = data.cachedSkills(member);
                    if (levels == null) continue;
                    for (int i = 0; i < Math.min(levels.length, best.length); i++) best[i] = Math.max(best[i], levels[i]);
                }
            });
        }
        return best;
    }

    // ------------------------------------------------------------------ the realm

    /** The city that holds the realm's research: the capital of its kingdom, or the city itself. */
    public static City holder(ValorisData data, City city) {
        return data.kingdomOfCity(city.teamId).flatMap(k -> data.byTeam(k.capital)).orElse(city);
    }

    /** Every city of the realm: all cities of its kingdom, or just this one. */
    public static List<City> realmCities(ValorisData data, City city) {
        Optional<Kingdom> k = data.kingdomOfCity(city.teamId);
        if (k.isEmpty()) return List.of(city);
        List<City> out = new ArrayList<>();
        for (UUID id : k.get().cities) data.byTeam(id).ifPresent(out::add);
        if (out.isEmpty()) out.add(city);
        return out;
    }

    /** Gives every city of the kingdom the union of their technologies. Returns how many were added. */
    public static int syncKingdom(ValorisData data, Kingdom k) {
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        List<City> cities = new ArrayList<>();
        for (UUID id : k.cities) data.byTeam(id).ifPresent(cities::add);
        for (City c : cities) all.addAll(c.techs);
        int added = 0;
        for (City c : cities) {
            for (String t : all) if (c.techs.add(t)) added++;
        }
        if (added > 0) data.touch();
        return added;
    }

    /** A member city's own research goes to the kingdom if the kingdom is not researching, otherwise it ends. */
    private static void handOverResearch(MinecraftServer server, ValorisData data, City member, City holder) {
        if (member == holder || member.researching.isEmpty()) return;
        TechNode node = TechConfig.node(member.researching);
        String name = node == null ? member.researching : node.name();
        if (holder.researching.isEmpty() && node != null && !has(holder, node.id())) {
            holder.researching = member.researching;
            holder.researchStart = member.researchStart;
            holder.researchEnd = member.researchEnd;
            Msg.toTeam(member.teamId, Msg.text("The research of " + name + " continues for the whole kingdom.",
                    ChatFormatting.AQUA));
        } else {
            Msg.toTeam(member.teamId, Msg.text("The research of " + name + " ended: the kingdom researches together"
                    + " and is busy with something else.", ChatFormatting.GOLD));
        }
        member.researching = "";
        member.researchStart = member.researchEnd = 0;
        data.touch();
    }

    /** A city joined a kingdom (invited, asked or conquered): the city and the kingdom share their technologies. */
    public static void onCityJoined(MinecraftServer server, ValorisData data, Kingdom k, City city) {
        City capital = data.byTeam(k.capital).orElse(city);
        int cityBefore = city.techs.size();
        int kingdomBefore = capital.techs.size();
        syncKingdom(data, k);
        int learned = city.techs.size() - cityBefore;      // what the kingdom taught the city
        int brought = capital.techs.size() - kingdomBefore; // what the city taught the kingdom
        handOverResearch(server, data, city, capital);
        if (learned > 0) Msg.toTeam(city.teamId, Msg.text(city.name + " learned " + learned
                + " technologies from the Kingdom of " + k.name + ".", ChatFormatting.GREEN));
        if (brought > 0) Msg.toKingdom(k, Msg.text("The Kingdom of " + k.name + " learned " + brought
                + " technologies from " + city.name + ".", ChatFormatting.GREEN));
        for (City c : realmCities(data, city)) com.valoris.city.ClaimLimitHook.syncLimits(server, c);
        saveNow(server);
    }

    /** The crown moved to another city: the kingdom's research goes with it. */
    public static void moveResearch(ValorisData data, City from, City to) {
        if (from == to || from.researching.isEmpty()) return;
        if (to.researching.isEmpty()) {
            to.researching = from.researching;
            to.researchStart = from.researchStart;
            to.researchEnd = from.researchEnd;
        }
        from.researching = "";
        from.researchStart = from.researchEnd = 0;
        data.touch();
    }

    // ------------------------------------------------------------------ status

    public static boolean isStarting(String tech) {
        TechNode n = TechConfig.node(tech);
        return n != null && n.starting();
    }

    public static boolean has(City city, String tech) {
        return isStarting(tech) || city.techs.contains(tech);
    }

    /** The era the server's admins have opened (the first era, bronze, is always open). */
    public static boolean eraOpen(String era) {
        if (era == null || era.isEmpty() || era.equals(FIRST_ERA)) return true;
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && ValorisData.get(server).openEras.contains(era);
    }

    public static final String FIRST_ERA = "bronze";

    public static Status status(City city, TechNode node, int[] levels) {
        if (has(city, node.id())) return Status.DONE;
        if (node.id().equals(city.researching)) return Status.RESEARCHING;
        if (!node.starting() && !eraOpen(node.era())) return Status.ERA_CLOSED;
        for (String req : node.requires()) if (!has(city, req)) return Status.LOCKED;
        for (Map.Entry<Skill, Integer> e : node.skills().entrySet()) {
            if (levels[e.getKey().ordinal()] < e.getValue()) return Status.NEEDS_SKILLS;
        }
        return Status.AVAILABLE;
    }

    /** Seconds left of the city's current research (0 when none or finished). */
    public static int remainingSeconds(City city) {
        if (city.researching.isEmpty()) return 0;
        return (int) Math.max(0, (city.researchEnd - System.currentTimeMillis() + 999) / 1000);
    }

    /**
     * Finishes the realm's current research when its time is up. Returns true if a technology was completed.
     * Any city may be passed; the research of its realm is checked.
     */
    public static boolean update(MinecraftServer server, City member) {
        ValorisData data = ValorisData.get(server);
        City city = holder(data, member);
        handOverResearch(server, data, member, city); // a member city's own research (older save)
        if (!city.founded || city.researching.isEmpty()) return false;
        TechNode node = TechConfig.node(city.researching);
        if (node == null) {                       // technology removed from the config
            city.researching = "";
            ValorisData.get(server).touch();
            return false;
        }
        if (System.currentTimeMillis() < city.researchEnd) return false;
        List<City> realm = realmCities(data, city);
        for (City c : realm) c.techs.add(node.id());
        city.researching = "";
        city.researchStart = city.researchEnd = 0;
        saveNow(server);
        for (City c : realm) {
            announce(server, c, node);
            com.valoris.city.ClaimLimitHook.syncLimits(server, c); // the claim manager shows the new limit
        }
        return true;
    }

    /** Marks the data dirty and writes it to disk immediately. */
    private static void saveNow(MinecraftServer server) {
        ValorisData.get(server).touch();
        try {
            server.overworld().getDataStorage().save();
        } catch (RuntimeException e) {
            com.valoris.valoris2.LOGGER.warn("Could not save research progress right away", e);
        }
    }

    // ------------------------------------------------------------------ research

    /** The research holder of the realm the player leads (the king for a kingdom, the ruler for a free city). */
    private static City rulersCity(ServerPlayer player) throws CommandSyntaxException {
        City city = Roles.cityOfMember(player).orElseThrow(() -> Errors.of("You do not belong to a city."));
        if (!city.founded) throw Errors.of("Your city has not been founded yet.");
        ValorisData data = ValorisData.get(player.getServer());
        if (!Realms.isLeader(player)) {
            throw Errors.of(data.kingdomOfCity(city.teamId).isPresent()
                    ? "Research is managed by the king for the whole kingdom." : "Only the city ruler can manage research.");
        }
        Realms.requireActingLeader(player); // a fallen city awaiting its conqueror cannot research
        return holder(data, city);
    }

    /** True if the player decides the research of their realm. */
    public static boolean canResearch(ServerPlayer player) {
        return Roles.cityOfMember(player).map(c -> c.founded).orElse(false) && Realms.isLeader(player);
    }

    public static void startResearch(ServerPlayer player, String techId) throws CommandSyntaxException {
        City city = rulersCity(player);
        MinecraftServer server = player.getServer();
        update(server, city);
        TechNode node = TechConfig.node(techId);
        if (node == null) throw Errors.of("Unknown technology: " + techId);
        int[] levels = cityLevels(server, city);
        switch (status(city, node, levels)) {
            case DONE -> throw Errors.of("Your city already knows " + node.name() + ".");
            case RESEARCHING -> throw Errors.of(node.name() + " is already being researched.");
            case LOCKED -> throw Errors.of("Research " + missingPrereqs(city, node) + " first.");
            case NEEDS_SKILLS -> throw Errors.of("Your city needs " + missingSkills(node, levels) + ".");
            case ERA_CLOSED -> throw Errors.of("The " + TechConfig.eraNames().getOrDefault(node.era(), node.era())
                    + " has not begun on this server yet.");
            case AVAILABLE -> { }
        }
        if (!city.researching.isEmpty()) {
            TechNode current = TechConfig.node(city.researching);
            throw Errors.of("Your city is already researching " + (current == null ? city.researching : current.name())
                    + ". Cancel it first.");
        }
        long now = System.currentTimeMillis();
        city.researching = node.id();
        city.researchStart = now;
        city.researchEnd = now + node.researchMinutes() * 60_000L;
        saveNow(server);
        if (node.researchMinutes() == 0) {
            update(server, city);
            return;
        }
        for (City c : realmCities(ValorisData.get(server), city)) {
            Msg.toTeam(c.teamId, Msg.text("Research started: " + node.name() + " ("
                    + duration(node.researchMinutes() * 60) + ").", ChatFormatting.AQUA));
        }
    }

    public static void cancelResearch(ServerPlayer player) throws CommandSyntaxException {
        City city = rulersCity(player);
        if (city.researching.isEmpty()) throw Errors.of("Your city is not researching anything.");
        TechNode node = TechConfig.node(city.researching);
        city.researching = "";
        city.researchStart = city.researchEnd = 0;
        saveNow(player.getServer());
        for (City c : realmCities(ValorisData.get(player.getServer()), city)) {
            Msg.toTeam(c.teamId, Msg.text("Research cancelled: " + (node == null ? "?" : node.name())
                    + ". Progress is lost.", ChatFormatting.GOLD));
        }
    }

    /** Admin: completes the current research of the city's realm now. */
    public static boolean finishNow(MinecraftServer server, City member) {
        City city = holder(ValorisData.get(server), member);
        if (city.researching.isEmpty()) return false;
        city.researchEnd = System.currentTimeMillis();
        return update(server, city);
    }

    public static String duration(int seconds) {
        if (seconds <= 0) return "instant";
        int h = seconds / 3600, m = (seconds % 3600) / 60;
        if (h > 0) return h + "h" + (m > 0 ? " " + m + "m" : "");
        if (m > 0) return m + "m";
        return seconds + "s";
    }

    private static void announce(MinecraftServer server, City city, TechNode node) {
        Msg.toTeam(city.teamId, Msg.text("Research complete - new technology: " + node.name() + "!", ChatFormatting.GREEN));
        if (!node.description().isEmpty()) Msg.toTeam(city.teamId, Msg.text(node.description(), ChatFormatting.GRAY));
        FTBTeamsAPI.api().getManager().getTeamByID(city.teamId).ifPresent(team ->
                team.getOnlineMembers().forEach(p -> p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundSource.PLAYERS, 0.6F, 1.0F)));
    }

    /** Technologies that were known from the start before the tree rework; cities founded before keep them. */
    private static final List<String> FORMER_STARTING = List.of("bronze_weapons", "bronze_armor", "bronze_shields", "bronze_siege");
    private static final int TREE_VERSION = 3;

    /** Called every few seconds. */
    public static void tick(MinecraftServer server) {
        ValorisData data = ValorisData.get(server);
        if (data.techTreeVersion < TREE_VERSION) {
            for (City c : data.all()) {
                if (!c.founded) continue;
                // version 2: bronze gear was known from the start; version 3: bronze weapons were still known from the start
                c.techs.addAll(FORMER_STARTING);
                // version 3: tools got their own technologies; keep the tools a city could already make
                if (c.techs.contains("iron_weapons")) c.techs.add("iron_tools");
                if (c.techs.contains("steel_age")) c.techs.add("steel_tools");
            }
            data.techTreeVersion = TREE_VERSION;
            data.touch();
        }
        for (Kingdom k : List.copyOf(data.kingdoms())) syncKingdom(data, k);
        for (City city : List.copyOf(data.all())) {
            if (holder(data, city) == city) update(server, city);
            else handOverResearch(server, data, city, holder(data, city));
        }
    }

    // ------------------------------------------------------------------ claim limit

    /** Chunks the city may claim: the highest claimLimit among its technologies (Expand Domain I-IV). */
    public static int claimLimit(City city) {
        int limit = com.valoris.Config.CITY_CLAIM_LIMIT.get();
        for (TechNode node : TechConfig.nodes().values()) {
            if (node.claimLimit() > limit && has(city, node.id())) limit = node.claimLimit();
        }
        return limit;
    }

    // ------------------------------------------------------------------ texts

    public static String missingPrereqs(City city, TechNode node) {
        List<String> out = new ArrayList<>();
        for (String r : node.requires()) {
            if (has(city, r)) continue;
            TechNode n = TechConfig.node(r);
            out.add(n == null ? r : n.name());
        }
        return String.join(", ", out);
    }

    public static String missingSkills(TechNode node, int[] levels) {
        List<String> out = new ArrayList<>();
        node.skills().forEach((skill, lvl) -> {
            int have = levels[skill.ordinal()];
            if (have < lvl) out.add(skill.title + " " + lvl + " (best " + have + ")");
        });
        return String.join(", ", out);
    }

    public static String requirementText(TechNode node, int[] levels) {
        if (node.starting()) return "Known from the start";
        List<String> out = new ArrayList<>();
        node.skills().forEach((skill, lvl) -> {
            int have = levels[skill.ordinal()];
            out.add(skill.title + " " + lvl + (have >= lvl ? " ✔" : " (" + have + ")"));
        });
        return out.isEmpty() ? "No skill requirements" : String.join("  ", out);
    }

    // ------------------------------------------------------------------ crafting

    /** Message explaining why the player may not craft this, or empty if allowed. */
    public static Optional<String> blockedReason(ServerPlayer player, ItemStack result) {
        Optional<String> needed = TechRules.requiredFor(result);
        if (needed.isEmpty()) return Optional.empty();
        if (needed.get().equals(TechRules.NEVER)) return Optional.of("This item is disabled on this server.");
        if (isStarting(needed.get())) return Optional.empty();
        Optional<City> city = Roles.cityOfMember(player);
        if (city.isPresent() && has(city.get(), needed.get())) return Optional.empty();
        TechNode node = TechConfig.node(needed.get());
        String name = node == null ? needed.get() : node.name();
        return Optional.of(city.isEmpty() ? "Join a city that knows " + name + " to craft this."
                : "Your city needs the technology " + name + " to craft this.");
    }

    // ------------------------------------------------------------------ admin

    /** Admin: gives or takes a technology from the city's whole realm. */
    public static void grant(ValorisData data, City city, String tech, boolean add) throws CommandSyntaxException {
        if (TechConfig.node(tech) == null) throw Errors.of("Unknown technology: " + tech);
        for (City c : realmCities(data, city)) {
            if (add) c.techs.add(tech); else c.techs.remove(tech);
            if (tech.equals(c.researching)) c.researching = "";
        }
    }

    public static Component listLine(TechNode node, Status status, String detail) {
        ChatFormatting color = switch (status) {
            case DONE -> ChatFormatting.GREEN;
            case RESEARCHING -> ChatFormatting.AQUA;
            case AVAILABLE -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GRAY;
        };
        String text = switch (status) {
            case ERA_CLOSED -> "its era has not begun yet";
            case DONE -> "known";
            case RESEARCHING -> "researching";
            case AVAILABLE -> "can be researched (" + duration(node.researchMinutes() * 60) + ")";
            default -> detail;
        };
        return Msg.text(" - " + node.name() + " [" + node.id() + "]: " + text, color);
    }
}
