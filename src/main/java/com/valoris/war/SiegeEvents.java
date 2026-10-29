package com.valoris.war;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Siege;
import com.valoris.data.ValorisData.SiegePhase;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Rules around sieges:
 * - the city banner can only be destroyed by the attackers during the active phase, by hitting it
 *   (Config siegeBannerHits, 10 by default; the hits of all attackers count together);
 * - in a city under an active siege only the two sides may place blocks (one every siegeBlockPlaceSeconds) and
 *   break them (both get Mining Fatigue II there), and siege machine explosions break walls;
 * - explosions never destroy city land, except in a city under an active siege.
 * While a siege is active FTB Chunks protection of that city is switched off (see SiegeService), so these
 * handlers are the city's protection then: bystanders cannot touch anything there, and nobody pours fluids.
 * Players in creative mode are exempt from these rules; operators in survival are not.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public class SiegeEvents {

    /** Once per second. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 == 0) SiegeService.tick(server);
    }

    // ------------------------------------------------------------------ helpers

    /** The city whose banner stands at pos. */
    private static Optional<City> protectedBannerOf(ValorisData data, ServerLevel level, BlockPos pos) {
        return data.cityByBanner(level.dimension().location().toString(), pos.asLong());
    }

    private static Optional<City> cityAt(ValorisData data, ServerLevel level, BlockPos pos) {
        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos));
        if (chunk == null) return Optional.empty();
        return data.byTeam(chunk.getTeamData().getTeam().getId());
    }

    /** The siege in its active phase whose city owns the land at pos. */
    private static Optional<Siege> activeSiegeAt(ValorisData data, ServerLevel level, BlockPos pos) {
        if (data.sieges().isEmpty()) return Optional.empty();
        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos));
        if (chunk == null) return Optional.empty();
        UUID team = chunk.getTeamData().getTeam().getId();
        return data.siegeOfCity(team).filter(s -> s.phase == SiegePhase.ACTIVE);
    }

    // ------------------------------------------------------------------ breaking

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        ValorisData data = ValorisData.get(level.getServer());
        BlockPos pos = event.getPos();

        Optional<City> bannerCity = protectedBannerOf(data, level, pos);
        if (bannerCity.isPresent()) {
            // never broken the normal way: the attackers bring it down with hits (see onHitBanner)
            event.setCanceled(true);
            Optional<Siege> siege = data.siegeOfCity(bannerCity.get().teamId);
            if (siege.isPresent() && siege.get().phase == SiegePhase.ACTIVE && SiegeService.isAttacker(player, siege.get())) {
                return; // the hit was already counted
            }
            boolean defender = siege.isPresent() && !SiegeService.isAttacker(player, siege.get())
                    && SiegeService.isParticipant(player, siege.get());
            player.sendSystemMessage(Msg.text(defender
                    ? "Defenders cannot destroy or move the city banner during a siege. Defend it!"
                    : "The city banner is protected. Only besiegers can destroy it, and only during an active siege.",
                    ChatFormatting.RED));
            return;
        }

        // in a city under an active siege only the two sides may break blocks (both with Mining Fatigue II)
        Optional<Siege> siege = activeSiegeAt(data, level, pos);
        if (siege.isPresent() && !SiegeService.isParticipant(player, siege.get()) && !player.isCreative()) {
            event.setCanceled(true);
            player.displayClientMessage(Msg.text("Only the two sides of the siege may break blocks here.",
                    ChatFormatting.RED), true);
        }
    }

    /** player -> game time of their last counted hit on a banner (one hit counts per half second) */
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();
    /** siege -> game time when the defenders were last told the banner is under attack */
    private static final Map<UUID, Long> LAST_ALARM = new HashMap<>();

    /**
     * During an active siege the attackers bring the city banner down by hitting it (left click) siegeBannerHits
     * times; the hits of all attackers add up. The last hit destroys the banner and the city falls.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onHitBanner(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        ValorisData data = ValorisData.get(level.getServer());
        BlockPos pos = event.getPos();
        Optional<City> city = protectedBannerOf(data, level, pos);
        if (city.isEmpty()) return;
        Optional<Siege> siege = data.siegeOfCity(city.get().teamId);
        if (siege.isEmpty() || siege.get().phase != SiegePhase.ACTIVE || !SiegeService.isAttacker(player, siege.get())) return;
        Siege s = siege.get();

        long now = level.getGameTime();
        Long last = LAST_HIT.get(player.getUUID());
        if (last != null && now - last < 10) return; // no auto-clicker: one hit per half second
        LAST_HIT.put(player.getUUID(), now);

        int needed = com.valoris.Config.SIEGE_BANNER_HITS.get();
        s.bannerHits++;
        data.touch();
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS,
                0.6F, 0.6F + 0.8F * s.bannerHits / needed);
        if (s.bannerHits >= needed) {
            level.destroyBlock(pos, false); // no drop; SiegeService sees the banner gone and the city falls
            return;
        }
        player.displayClientMessage(Msg.text("City banner: " + s.bannerHits + " / " + needed + " hits",
                ChatFormatting.GOLD), true);
        Long alarm = LAST_ALARM.get(s.id);
        if (alarm == null || now - alarm >= 200) { // at most every 10 seconds
            LAST_ALARM.put(s.id, now);
            for (UUID d : SiegeService.defendersOf(data, s)) {
                com.valoris.realm.Realms.message(data, d, Msg.text("The city banner of " + s.cityName + " is under attack! ("
                        + s.bannerHits + " / " + needed + " hits)", ChatFormatting.RED));
            }
        }
    }

    // ------------------------------------------------------------------ building

    /** player -> game time of the last block they placed in a besieged city */
    private static final Map<UUID, Long> LAST_PLACE = new HashMap<>();

    /**
     * In a city under an active siege only the two sides build, one block every siegeBlockPlaceSeconds per player
     * (barricades, ladders, patching a breach); bystanders cannot build there. Only creative mode is exempt
     * (operators playing in survival follow the siege rules like everyone).
     */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        ValorisData data = ValorisData.get(level.getServer());
        Optional<Siege> siege = activeSiegeAt(data, level, event.getPos());
        if (siege.isEmpty() || player.isCreative()) return;
        if (!SiegeService.isParticipant(player, siege.get())) {
            event.setCanceled(true);
            deny(player, "Only the two sides of the siege may build here.");
            return;
        }
        long every = com.valoris.Config.SIEGE_PLACE_SECONDS.get() * 20L;
        long now = level.getGameTime();
        Long last = LAST_PLACE.get(player.getUUID());
        if (every > 0 && last != null && now - last < every && now >= last) {
            event.setCanceled(true);
            long left = (every - (now - last) + 19) / 20;
            deny(player, "During a siege you can place one block every " + every / 20 + " seconds (" + left + "s left).");
            return;
        }
        LAST_PLACE.put(player.getUUID(), now);
    }

    // ------------------------------------------------------------------ the open city during a siege

    private static boolean outsider(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        ValorisData data = ValorisData.get(level.getServer());
        Optional<Siege> siege = activeSiegeAt(data, level, pos);
        return siege.isPresent() && !SiegeService.isParticipant(player, siege.get()) && !player.isCreative();
    }

    private static void deny(ServerPlayer player, String message) {
        player.displayClientMessage(Msg.text(message, ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onUseBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockPos target = event.getPos().relative(event.getFace() == null ? net.minecraft.core.Direction.UP : event.getFace());
        if (outsider(player, event.getPos())) {
            event.setCanceled(true);
            deny(player, "This city is under siege. Only the two sides may act here.");
            return;
        }
        // no flooding or burning a besieged city
        var item = event.getItemStack().getItem();
        boolean fluidOrFire = item instanceof net.minecraft.world.item.BucketItem
                || item instanceof net.minecraft.world.item.FlintAndSteelItem
                || item instanceof net.minecraft.world.item.FireChargeItem;
        if (fluidOrFire && !player.isCreative()
                && player.level() instanceof ServerLevel level
                && activeSiegeAt(ValorisData.get(level.getServer()), level, target).isPresent()) {
            event.setCanceled(true);
            deny(player, "You cannot pour fluids or light fires in a city under siege.");
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onUseItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && outsider(player, player.blockPosition())) {
            event.setCanceled(true);
            deny(player, "This city is under siege. Only the two sides may act here.");
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onInteractEntity(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && outsider(player, event.getTarget().blockPosition())) {
            event.setCanceled(true);
            deny(player, "This city is under siege. Only the two sides may act here.");
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onInteractEntityAt(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player && outsider(player, event.getTarget().blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (outsider(player, event.getTarget().blockPosition()) || outsider(player, player.blockPosition()))) {
            event.setCanceled(true);
            deny(player, "This city is under siege. Only the two sides may fight here.");
        }
    }

    // ------------------------------------------------------------------ explosions

    /**
     * Explosions do not destroy city land, unless the city is under an active siege.
     * Members of a city may still blow up their own land. City banners always survive.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ValorisData data = ValorisData.get(level.getServer());
        LivingEntity source = event.getExplosion().getIndirectSourceEntity();
        ServerPlayer sourcePlayer = source instanceof ServerPlayer sp ? sp : null;
        Map<Long, Optional<City>> cityByChunk = new HashMap<>();

        event.getAffectedBlocks().removeIf(pos -> {
            if (protectedBannerOf(data, level, pos).isPresent()) return true;

            Optional<City> city = cityByChunk.computeIfAbsent(ChunkPos.asLong(pos), key -> cityAt(data, level, pos));
            if (city.isEmpty()) return false;
            City c = city.get();

            Optional<Siege> siege = data.siegeOfCity(c.teamId);
            if (siege.isPresent() && siege.get().phase == SiegePhase.ACTIVE) return false; // war zone
            if (sourcePlayer != null && Roles.cityOfMember(sourcePlayer).map(x -> x.teamId.equals(c.teamId)).orElse(false)) {
                return false; // own land
            }
            return true;
        });
    }
}
