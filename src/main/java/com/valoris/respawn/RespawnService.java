package com.valoris.respawn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.valoris.Config;
import com.valoris.city.CityService;
import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.data.ValorisData.SiegePhase;
import com.valoris.net.RespawnGoPayload;
import com.valoris.net.RespawnOptionsPayload;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Respawn choice: after death the player picks either a random place in the world (inside the world border, or a
 * configured radius) or one of the city banners of their kingdom (or their own city's banner). A banner can be
 * used only after a cooldown counted from the death: longer when that city is under siege.
 * The choice is stored here, the client then sends the normal respawn request and the player is moved on respawn.
 * A player joining for the very first time gets the same choice (alive and unharmed meanwhile, see onLogin) and is
 * moved as soon as he picks.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class RespawnService {
    private RespawnService() {}

    private record Destination(String city) {} // city null = random place

    private static final Map<UUID, Long> DEATHS = new HashMap<>();
    private static final Map<UUID, String> DEATH_MESSAGES = new HashMap<>();
    private static final Map<UUID, Destination> PENDING = new HashMap<>();

    // ------------------------------------------------------------------ the very first join

    /** Players choosing where to begin (alive, shown the same choice as after death, unharmed meanwhile). */
    private static final java.util.Set<UUID> STARTING = new java.util.HashSet<>();
    private static final String STARTED = "valoris2_started";
    private static final String START_PENDING = "valoris2_start_pending";

    /** Kept through death (NeoForge copies the "PlayerPersisted" part of the persistent data). */
    private static net.minecraft.nbt.CompoundTag persisted(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        if (!data.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) {
            data.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        }
        return data.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    /**
     * A player joining for the very first time (no play time yet) chooses where to begin, like after a death.
     * Someone who left before choosing gets the choice again; players who played before this existed never see it.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.RESPAWN_CHOICE.get()) return;
        net.minecraft.nbt.CompoundTag tag = persisted(player);
        if (tag.getBoolean(STARTED)) return;
        boolean firstTime = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME)) == 0;
        if (!firstTime && !tag.getBoolean(START_PENDING)) {
            tag.putBoolean(STARTED, true); // played before this choice existed
            return;
        }
        tag.putBoolean(START_PENDING, true);
        STARTING.add(player.getUUID());
        sendOptions(player);
    }

    /** Nothing can hurt a player who is still choosing where to begin. */
    @SubscribeEvent
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && STARTING.contains(player.getUUID())) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ death

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.RESPAWN_CHOICE.get()) return;
        DEATHS.put(player.getUUID(), System.currentTimeMillis());
        DEATH_MESSAGES.put(player.getUUID(), event.getSource().getLocalizedDeathMessage(player).getString());
        PENDING.remove(player.getUUID());
        sendOptions(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        STARTING.remove(event.getEntity().getUUID()); // still pending: asked again next time
    }

    // ------------------------------------------------------------------ options

    /** Banners the player may respawn at: every city of their kingdom, or their own city. */
    private static List<City> banners(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ValorisData data = ValorisData.get(server);
        Optional<City> own = Roles.cityOfMember(player);
        if (own.isEmpty()) return List.of();
        List<City> list = new ArrayList<>();
        Optional<Kingdom> kingdom = data.kingdomOfCity(own.get().teamId);
        if (kingdom.isPresent()) {
            for (UUID id : kingdom.get().cities) data.byTeam(id).ifPresent(list::add);
        } else {
            list.add(own.get());
        }
        list.removeIf(c -> !c.founded || !c.hasBanner() || !CityService.bannerPresent(server, c));
        // own city first
        list.sort((a, b) -> a.teamId.equals(own.get().teamId) ? -1 : b.teamId.equals(own.get().teamId) ? 1 : a.name.compareToIgnoreCase(b.name));
        return list;
    }

    private static boolean besieged(ValorisData data, City c) {
        return data.siegeOfCity(c.teamId).map(s -> s.phase == SiegePhase.ACTIVE).orElse(false);
    }

    private static int cooldownSeconds(ValorisData data, City c) {
        return besieged(data, c) ? Config.RESPAWN_SIEGE_COOLDOWN.get() : Config.RESPAWN_COOLDOWN.get();
    }

    private static int secondsLeft(ServerPlayer player, ValorisData data, City c) {
        if (STARTING.contains(player.getUUID())) return 0; // no death to count from
        long died = DEATHS.computeIfAbsent(player.getUUID(), k -> System.currentTimeMillis());
        long passed = (System.currentTimeMillis() - died) / 1000L;
        return (int) Math.max(0, cooldownSeconds(data, c) - passed);
    }

    public static void sendOptions(ServerPlayer player) {
        ValorisData data = ValorisData.get(player.getServer());
        Optional<City> own = Roles.cityOfMember(player);
        List<RespawnOptionsPayload.Banner> list = new ArrayList<>();
        for (City c : banners(player)) {
            BlockPos pos = BlockPos.of(c.bannerPos);
            list.add(new RespawnOptionsPayload.Banner(c.name, pos.getX(), pos.getY(), pos.getZ(), besieged(data, c),
                    secondsLeft(player, data, c), own.isPresent() && own.get().teamId.equals(c.teamId)));
        }
        String why = own.isEmpty() ? "You do not belong to a city." : list.isEmpty() ? "No city banner is standing." : "";
        PacketDistributor.sendToPlayer(player, new RespawnOptionsPayload(
                DEATH_MESSAGES.getOrDefault(player.getUUID(), "You died!"), randomAreaText(player.getServer()),
                own.isPresent() && data.kingdomOfCity(own.get().teamId).isPresent(), why, list,
                STARTING.contains(player.getUUID())));
    }

    // ------------------------------------------------------------------ choice

    /** cityName empty = random place. */
    public static void choose(ServerPlayer player, String cityName) {
        if (STARTING.contains(player.getUUID())) {
            chooseStart(player, cityName);
            return;
        }
        if (!player.isDeadOrDying()) return;
        if (cityName.isEmpty()) {
            PENDING.put(player.getUUID(), new Destination(null));
            PacketDistributor.sendToPlayer(player, new RespawnGoPayload());
            return;
        }
        ValorisData data = ValorisData.get(player.getServer());
        City city = banners(player).stream().filter(c -> c.name.equals(cityName)).findFirst().orElse(null);
        if (city == null) {
            player.sendSystemMessage(Component.literal("You cannot respawn at that banner.").withStyle(ChatFormatting.RED));
            sendOptions(player);
            return;
        }
        if (secondsLeft(player, data, city) > 0) {
            sendOptions(player);
            return;
        }
        PENDING.put(player.getUUID(), new Destination(city.name));
        PacketDistributor.sendToPlayer(player, new RespawnGoPayload());
    }

    /** The first choice: the player is alive, so he is moved right away. */
    private static void chooseStart(ServerPlayer player, String cityName) {
        MinecraftServer server = player.getServer();
        Destination dest = new Destination(null);
        if (!cityName.isEmpty()) {
            City city = banners(player).stream().filter(c -> c.name.equals(cityName)).findFirst().orElse(null);
            if (city == null) {
                player.sendSystemMessage(Component.literal("You cannot begin at that banner.").withStyle(ChatFormatting.RED));
                sendOptions(player);
                return;
            }
            dest = new Destination(city.name);
        }
        STARTING.remove(player.getUUID());
        net.minecraft.nbt.CompoundTag tag = persisted(player);
        tag.putBoolean(STARTED, true);
        tag.remove(START_PENDING);
        PacketDistributor.sendToPlayer(player, new RespawnGoPayload());
        try {
            moveAfterRespawn(server, player, dest);
        } catch (Throwable t) {
            valoris2.LOGGER.error("Start: could not move {}", player.getName().getString(), t);
        }
    }

    // ------------------------------------------------------------------ respawn

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.RESPAWN_CHOICE.get()) return;
        Destination dest = PENDING.remove(player.getUUID());
        DEATHS.remove(player.getUUID());
        DEATH_MESSAGES.remove(player.getUUID());
        MinecraftServer server = player.getServer();
        UUID id = player.getUUID();
        // move the player one tick later, when the respawn is complete on both sides
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || p.isDeadOrDying()) return;
            try {
                moveAfterRespawn(server, p, dest);
            } catch (Throwable t) {
                valoris2.LOGGER.error("Respawn: could not move {}", p.getName().getString(), t);
            }
        }));
    }

    private static void moveAfterRespawn(MinecraftServer server, ServerPlayer player, Destination dest) {
        if (dest != null && dest.city() != null) {
            ValorisData data = ValorisData.get(server);
            Optional<City> city = data.cityByName(dest.city());
            ServerLevel level = city.map(c -> CityService.levelOf(server, c)).orElse(null);
            if (city.isPresent() && level != null) {
                BlockPos spot = nearBanner(level, BlockPos.of(city.get().bannerPos));
                player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), 0F);
                player.sendSystemMessage(Component.literal("You rise again at the banner of " + city.get().name + ".")
                        .withStyle(ChatFormatting.GOLD));
                return;
            }
        }
        ServerLevel overworld = server.overworld();
        BlockPos spot = randomSpot(overworld);
        player.teleportTo(overworld, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), 0F);
        player.sendSystemMessage(Component.literal("You wake up somewhere in the wilderness...").withStyle(ChatFormatting.GRAY));
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    private static BlockPos nearBanner(ServerLevel level, BlockPos banner) {
        level.getChunk(banner); // make sure it is loaded
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -2; dy <= 3; dy++) {
                        BlockPos p = banner.offset(dx, dy, dz);
                        if (standable(level, p)) return p;
                    }
                }
            }
        }
        return banner.above();
    }

    // ------------------------------------------------------------------ random place

    private record Area(double x, double z, double radius, String text) {}

    private static Area area(MinecraftServer server) {
        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        BlockPos spawn = level.getSharedSpawnPos();
        int configured = Config.RESPAWN_RADIUS.get();
        if (configured > 0) {
            return new Area(spawn.getX(), spawn.getZ(), configured, "Anywhere within " + configured + " blocks of the world spawn");
        }
        if (border.getSize() < 1_000_000) {
            double r = Math.max(16, border.getSize() / 2 - 16);
            return new Area(border.getCenterX(), border.getCenterZ(), r, "Anywhere inside the world border");
        }
        int fallback = Config.RESPAWN_FALLBACK_RADIUS.get();
        return new Area(spawn.getX(), spawn.getZ(), fallback, "Anywhere within " + fallback + " blocks of the world spawn");
    }

    private static String randomAreaText(MinecraftServer server) {
        return area(server).text();
    }

    private static BlockPos randomSpot(ServerLevel level) {
        Area a = area(level.getServer());
        RandomSource rnd = level.random;
        WorldBorder border = level.getWorldBorder();
        int loaded = 0;
        for (int attempt = 0; attempt < 200 && loaded < 24; attempt++) {
            int x = Mth.floor(a.x() + (rnd.nextDouble() * 2 - 1) * a.radius());
            int z = Mth.floor(a.z() + (rnd.nextDouble() * 2 - 1) * a.radius());
            if (!border.isWithinBounds(x, z)) continue;
            // cheap checks first: nobody's land (nor right next to it), not open water
            if (claimedNear(level, x, z)) continue;
            if (wet(level, x, z)) continue;
            BlockPos p = surface(level, x, z);
            loaded++;
            if (p != null) return p;
        }
        // nothing found at random: the nearest unclaimed dry land around the world spawn
        BlockPos spawn = level.getSharedSpawnPos();
        search:
        for (int r = 0; r <= 64; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = spawn.getX() + dx * 16, z = spawn.getZ() + dz * 16;
                    if (!border.isWithinBounds(x, z) || claimedNear(level, x, z) || wet(level, x, z)) continue;
                    BlockPos p = surface(level, x, z);
                    if (p != null) return p;
                    if (++loaded > 64) break search; // give up rather than generate half the world
                }
            }
        }
        return new BlockPos(spawn.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ());
    }

    /** A standable spot on the surface at x, z (loads or generates the chunk), or null. */
    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos p = new BlockPos(x, y, z);
        return standable(level, p) ? p : null;
    }

    /** Ocean or river biome there (asked from the generator, no chunk is loaded). */
    private static boolean wet(ServerLevel level, int x, int z) {
        var biome = level.getUncachedNoiseBiome(x >> 2, 64 >> 2, z >> 2);
        return biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER);
    }

    /** The chunk at x, z or one of the 8 around it is claimed: no waking up inside (or at the edge of) a city. */
    private static boolean claimedNear(ServerLevel level, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (claimedAt(level, new BlockPos(x + dx * 16, 64, z + dz * 16))) return true;
            }
        }
        return false;
    }

    private static boolean claimedAt(ServerLevel level, BlockPos p) {
        try {
            return dev.ftb.mods.ftbchunks.api.FTBChunksAPI.api().getManager()
                    .getChunk(new dev.ftb.mods.ftblibrary.math.ChunkDimPos(level, p)) != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
