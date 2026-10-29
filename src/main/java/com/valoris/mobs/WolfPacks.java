package com.valoris.mobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.valoris.Config;
import com.valoris.valoris2;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wild wolves live and hunt in packs of 6-8:
 * - packs spawn now and then around players, in every overworld biome except water, beaches and mushroom
 *   islands (vanilla wolf spawning is off, see HostileSpawnRule and the remove_wolves biome modifier);
 *   in tier 4 land (deserts, badlands, snow and ice, peaks) they are packs of bigger great wolves (GreatWolves);
 * - members follow their leader; a leader that dies is replaced by another member;
 * - they see players from far away (48 blocks) and the whole pack attacks together;
 * - a player wearing all four armour pieces (any armour) is avoided: the pack runs away;
 * - a player who comes crouching is neither attacked nor avoided, until they hit a wolf of the pack:
 *   then the pack flees from an armoured player and attacks an unarmoured one.
 *   Crouching does not help once the pack is already hunting you.
 * Tamed wolves keep the vanilla behaviour.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class WolfPacks {
    private WolfPacks() {}

    private static final String PACK = "valoris2_pack";
    private static final String LEADER = "valoris2_pack_leader";
    private static final String SPAWNED = "valoris2_pack_spawned";
    private static final net.minecraft.resources.ResourceLocation PACK_DAMAGE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "pack_wolf_damage");

    public static final double SIGHT = 48.0;
    private static final float FLEE_DISTANCE = 20.0F;
    private static final long PROVOKED_TICKS = 2400;   // 2 minutes after being hit
    private static final long ENGAGED_TICKS = 600;     // 30 s after the last chase
    private static final int SPAWN_INTERVAL = 600;     // every 30 s per player
    private static final int MAX_WOLVES_NEARBY = 8;

    public enum Attitude { IGNORE, FLEE, HUNT }

    /** pack id -> player id -> game time until which the mark lasts */
    private static final Map<UUID, Map<UUID, Long>> PROVOKED = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> ENGAGED = new HashMap<>();

    // ------------------------------------------------------------------ pack membership

    private static CompoundTag tag(Wolf wolf) {
        return wolf.getPersistentData();
    }

    public static boolean inPack(Wolf wolf) {
        return tag(wolf).hasUUID(PACK);
    }

    /** The pack id, or the wolf's own id for a lone wolf. */
    public static UUID packId(Wolf wolf) {
        return inPack(wolf) ? tag(wolf).getUUID(PACK) : wolf.getUUID();
    }

    public static boolean isLeader(Wolf wolf) {
        return tag(wolf).getBoolean(LEADER);
    }

    public static List<Wolf> members(Wolf wolf, double range) {
        UUID id = packId(wolf);
        return wolf.level().getEntitiesOfClass(Wolf.class, wolf.getBoundingBox().inflate(range),
                w -> w.isAlive() && !w.isTame() && packId(w).equals(id));
    }

    /** The nearest leader of this wolf's pack; when there is none, this wolf takes the lead. */
    public static Wolf leaderOf(Wolf wolf) {
        if (!inPack(wolf) || isLeader(wolf)) return null;
        Wolf best = null;
        double bestD = Double.MAX_VALUE;
        for (Wolf w : members(wolf, 64.0)) {
            if (w == wolf || !isLeader(w)) continue;
            double d = w.distanceToSqr(wolf);
            if (d < bestD) { bestD = d; best = w; }
        }
        if (best == null) tag(wolf).putBoolean(LEADER, true);
        return best;
    }

    // ------------------------------------------------------------------ attitude towards a player

    public static boolean fullArmor(Player p) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isEmpty() || stack.getItem() instanceof ElytraItem) return false;
            if (!(stack.getItem() instanceof ArmorItem) && !stack.canEquip(slot, p)) return false;
        }
        return true;
    }

    private static boolean marked(Map<UUID, Map<UUID, Long>> map, UUID pack, UUID player, long now) {
        Map<UUID, Long> m = map.get(pack);
        if (m == null) return false;
        Long until = m.get(player);
        if (until == null) return false;
        if (until < now) {
            m.remove(player);
            if (m.isEmpty()) map.remove(pack);
            return false;
        }
        return true;
    }

    private static void mark(Map<UUID, Map<UUID, Long>> map, UUID pack, UUID player, long until) {
        map.computeIfAbsent(pack, k -> new HashMap<>()).put(player, until);
    }

    public static Attitude attitude(Wolf wolf, Player p) {
        if (wolf.isTame() || p.isCreative() || p.isSpectator() || !p.isAlive()) return Attitude.IGNORE;
        long now = wolf.level().getGameTime();
        UUID pack = packId(wolf);
        boolean provoked = marked(PROVOKED, pack, p.getUUID(), now);
        boolean engaged = marked(ENGAGED, pack, p.getUUID(), now);
        if (p.isCrouching() && !provoked && !engaged) return Attitude.IGNORE;
        if (fullArmor(p)) return Attitude.FLEE;
        return Attitude.HUNT;
    }

    // ------------------------------------------------------------------ AI setup

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Wolf wolf)) return;
        if (!Config.WOLF_PACKS_ENABLED.get()) return;
        if (!wolf.isTame()) {
            AttributeInstance range = wolf.getAttribute(Attributes.FOLLOW_RANGE);
            if (range != null && range.getBaseValue() < SIGHT) range.setBaseValue(SIGHT);
            // wild pack wolves bite harder (multiplies the total, so great wolves get it on top of their own bonus)
            AttributeInstance damage = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) {
                damage.removeModifier(PACK_DAMAGE);
                double factor = Config.WOLF_PACK_DAMAGE.get();
                if (factor != 1.0) damage.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        PACK_DAMAGE, factor - 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        wolf.goalSelector.addGoal(2, new AvoidEntityGoal<>(wolf, Player.class, FLEE_DISTANCE, 1.25, 1.6,
                e -> e instanceof Player p && attitude(wolf, p) == Attitude.FLEE));
        wolf.goalSelector.addGoal(7, new PackFollowLeaderGoal(wolf));
        wolf.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(wolf, Player.class, 10, true, false,
                e -> e instanceof Player p && attitude(wolf, p) == Attitude.HUNT));
    }

    // ------------------------------------------------------------------ being hit

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf) || wolf.level().isClientSide() || wolf.isTame()) return;
        if (!Config.WOLF_PACKS_ENABLED.get()) return;
        Entity src = event.getSource().getEntity();
        if (!(src instanceof Player p)) return;
        UUID pack = packId(wolf);
        mark(PROVOKED, pack, p.getUUID(), wolf.level().getGameTime() + PROVOKED_TICKS);
        Attitude a = attitude(wolf, p);
        for (Wolf m : members(wolf, SIGHT)) {
            if (a == Attitude.HUNT) {
                m.setTarget(p);
            } else if (m.getTarget() == p) {
                m.setTarget(null);
                m.stopBeingAngry();
            }
        }
    }

    // ------------------------------------------------------------------ every tick: keep the rules

    @SubscribeEvent
    public static void onWolfTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Wolf wolf) || wolf.level().isClientSide() || wolf.isTame()) return;
        if (!Config.WOLF_PACKS_ENABLED.get()) return;

        LivingEntity target = wolf.getTarget();
        if (target instanceof Player p) {
            Attitude a = attitude(wolf, p);
            if (a != Attitude.HUNT) {
                // vanilla anger (being hit, alerted by another wolf) must not override the pack rules
                wolf.setTarget(null);
                wolf.stopBeingAngry();
            } else if (wolf.tickCount % 10 == 0) {
                long now = wolf.level().getGameTime();
                mark(ENGAGED, packId(wolf), p.getUUID(), now + ENGAGED_TICKS);
                for (Wolf m : members(wolf, SIGHT)) {
                    if (m != wolf && m.getTarget() == null) m.setTarget(p);
                }
            }
        }

        // packs spawned by us leave the world when nobody is around, so they do not pile up
        if (wolf.tickCount % 200 == 0 && tag(wolf).getBoolean(SPAWNED) && !wolf.hasCustomName() && !wolf.isLeashed()
                && wolf.level().getNearestPlayer(wolf, 128.0) == null) {
            wolf.discard();
        }
    }

    // ------------------------------------------------------------------ spawning packs

    /** Normal packs: not in deserts and badlands, and nowhere in tier 4 land, which belongs to the great wolves. */
    public static boolean allowedBiome(Holder<Biome> biome) {
        return !biome.is(BiomeTags.IS_BADLANDS) && !biome.is(Tags.Biomes.IS_DESERT) && !GreatWolves.greatLand(biome)
                && anyWolfBiome(biome);
    }

    /** No packs on water, beaches or mushroom islands. */
    static boolean anyWolfBiome(Holder<Biome> biome) {
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH)
                && !biome.is(Tags.Biomes.IS_MUSHROOM);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.WOLF_PACKS_ENABLED.get()) return;
        ServerLevel level = event.getServer().overworld();
        if (level.getGameTime() % SPAWN_INTERVAL != 0) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (level.random.nextDouble() >= Config.WOLF_PACK_CHANCE.get()) continue;
            int nearby = level.getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(96.0), w -> !w.isTame()).size();
            if (nearby >= MAX_WOLVES_NEARBY) continue;
            trySpawnPack(level, p);
        }
    }

    static BlockPos ground(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) return null;
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return null;
        if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) return null;
        return pos;
    }

    static boolean claimed(ServerLevel level, BlockPos pos) {
        try {
            return FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Spawns one pack 48-80 blocks from the player, out of sight distance of anybody closer than 32 blocks. */
    public static int trySpawnPack(ServerLevel level, Player near) {
        var rnd = level.random;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = 48.0 + rnd.nextDouble() * 32.0;
            int cx = Mth.floor(near.getX() + Math.cos(angle) * dist);
            int cz = Mth.floor(near.getZ() + Math.sin(angle) * dist);
            BlockPos center = ground(level, cx, cz);
            if (center == null) continue;
            Holder<Biome> biome = level.getBiome(center);
            boolean great = GreatWolves.greatLand(biome) && anyWolfBiome(biome);
            if (!great && !allowedBiome(biome)) continue;
            if (level.getNearestPlayer(center.getX(), center.getY(), center.getZ(), 32.0, false) != null) continue;
            if (claimed(level, center)) continue;
            return spawnPack(level, center, great);
        }
        return 0;
    }

    /** Spawns a pack of 6-8 wolves (great wolves when great) around the position; returns how many spawned. */
    public static int spawnPack(ServerLevel level, BlockPos center, boolean great) {
        var rnd = level.random;
        int count = 6 + rnd.nextInt(3);
        UUID pack = UUID.randomUUID();
        SpawnGroupData data = null;
        int spawned = 0;
        for (int i = 0; i < count * 4 && spawned < count; i++) {
            BlockPos pos = i == 0 ? center : ground(level, center.getX() + rnd.nextInt(9) - 4, center.getZ() + rnd.nextInt(9) - 4);
            if (pos == null || Math.abs(pos.getY() - center.getY()) > 4) continue;
            if (claimed(level, pos)) continue; // never on a city's land, not even at the edge of the pack
            Wolf wolf = EntityType.WOLF.create(level);
            if (wolf == null) return spawned;
            if (great) GreatWolves.make(wolf); // first: the bigger hitbox must fit too
            wolf.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(wolf)) continue;
            data = wolf.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, data);
            CompoundTag t = wolf.getPersistentData();
            t.putUUID(PACK, pack);
            t.putBoolean(LEADER, spawned == 0);
            t.putBoolean(SPAWNED, true);
            level.addFreshEntity(wolf);
            spawned++;
        }
        return spawned;
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        PROVOKED.clear();
        ENGAGED.clear();
    }
}
