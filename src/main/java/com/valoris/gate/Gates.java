package com.valoris.gate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.valoris.Config;
import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.realm.Realms;
import com.valoris.registry.ModBlocks;
import com.valoris.util.Msg;
import com.valoris.valoris2;
import com.valoris.war.SiegeEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Portcullis gates. The connected portcullis blocks of one plane (up, down, along the axis) are one gate, saved here
 * with its closed shape. Right click (PortcullisBlock) raises it - the whole grid goes up into the wall together until
 * only its top row is left - or lowers it back, both animated (the blocks' SHIFT, 1 px every tick). Only the people of
 * the land's realm use it: on a city's claim its residents, or every resident of its kingdom; unclaimed gates anyone.
 * During an active siege on its land a gate cannot be mined, and every hit of a siege machine (Siege Machines'
 * missile and ram explosions; the ballista shoots arrows, which do not count) takes one of its Config.GATE_HITS hit
 * points; at none the whole gate is destroyed. During the siege the hit points show under the crosshair of whoever
 * looks at the gate (client.GateHud) and are back to full when it is over. A gate does not close onto a block in its
 * way: it will not start, and if a block is put under it while it goes down, it goes back up. An open gate closes by
 * itself Config.GATE_AUTO_CLOSE_SECONDS after it was opened (while something is in its way it waits).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class Gates extends SavedData {
    private static final String NAME = "valoris_gates";
    private static final int MAX_BLOCKS = 1024, STEP_TICKS = 1, STEPS = 16, INFO_RANGE = 64;
    private static final String MISSILE_EXPLOSION = "ru.magistu.siegemachines.entity.projectile.MissileExplosion";

    static final class Gate {
        final UUID id;
        final String dim;
        final Direction.Axis axis;
        final Set<Long> blocks = new LinkedHashSet<>(); // the closed shape
        int raised; // rows the gate is lifted by: 0 closed, height() open
        int hits;

        Gate(UUID id, String dim, Direction.Axis axis) {
            this.id = id;
            this.dim = dim;
            this.axis = axis;
        }
    }

    private final Map<UUID, Gate> gates = new LinkedHashMap<>();
    /** dimension -> block -> gate */
    private final Map<String, Map<Long, UUID>> index = new HashMap<>();

    private static final Map<UUID, Motion> MOVING = new HashMap<>();
    /** open gate -> server tick it was opened at (gates found open after a restart: when first seen) */
    private static final Map<UUID, Integer> OPENED = new HashMap<>();
    private static int ticks;

    public static Gates get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Gates::new, Gates::load, null), NAME);
    }

    // ------------------------------------------------------------------ shape

    private static String dim(Level level) {
        return level.dimension().location().toString();
    }

    private static boolean isGate(BlockState state) {
        return state.getBlock() instanceof PortcullisBlock;
    }

    /** The connected portcullis blocks of start's plane. */
    private static Set<Long> component(ServerLevel level, BlockPos start) {
        BlockState first = level.getBlockState(start);
        Set<Long> seen = new LinkedHashSet<>();
        if (!isGate(first)) return seen;
        Direction.Axis axis = first.getValue(PortcullisBlock.AXIS);
        Direction along = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        while (!queue.isEmpty() && seen.size() < MAX_BLOCKS) {
            BlockPos p = queue.poll();
            for (BlockPos n : new BlockPos[]{p.above(), p.below(), p.relative(along), p.relative(along.getOpposite())}) {
                if (seen.contains(n.asLong())) continue;
                BlockState s = level.getBlockState(n);
                if (isGate(s) && s.getValue(PortcullisBlock.AXIS) == axis) {
                    seen.add(n.asLong());
                    queue.add(n);
                }
            }
        }
        return seen;
    }

    private void reindex() {
        index.clear();
        for (Gate g : gates.values()) {
            Map<Long, UUID> m = index.computeIfAbsent(g.dim, k -> new HashMap<>());
            for (long b : g.blocks) m.put(b, g.id);
        }
    }

    private Gate at(Level level, BlockPos pos) {
        UUID id = index.getOrDefault(dim(level), Map.of()).get(pos.asLong());
        return id == null ? null : gates.get(id);
    }

    /** The gate of a portcullis block: the saved one, brought up to date with the blocks of a closed gate. */
    private Gate track(ServerLevel level, BlockPos pos) {
        Gate known = at(level, pos);
        if (known != null && (known.raised > 0 || MOVING.containsKey(known.id))) return known;
        Set<Long> comp = component(level, pos);
        if (comp.isEmpty()) return null;
        Set<Gate> touched = new LinkedHashSet<>();
        for (long b : comp) {
            Gate g = at(level, BlockPos.of(b));
            if (g != null) touched.add(g);
        }
        for (Gate g : touched) {
            if (g.raised > 0 || MOVING.containsKey(g.id)) { // built onto a raised gate: the new blocks join its shape
                g.blocks.addAll(comp);
                reindex();
                setDirty();
                return g;
            }
        }
        Gate gate = touched.isEmpty() ? null : touched.iterator().next();
        int hits = 0;
        for (Gate g : touched) {
            hits = Math.max(hits, g.hits);
            if (g != gate) gates.remove(g.id);
        }
        if (gate == null) {
            gate = new Gate(UUID.randomUUID(), dim(level), level.getBlockState(pos).getValue(PortcullisBlock.AXIS));
            gates.put(gate.id, gate);
        }
        gate.blocks.clear();
        gate.blocks.addAll(comp);
        gate.hits = hits;
        reindex();
        setDirty();
        return gate;
    }


    /** Rows a gate goes up to be open: all but its top row. */
    private static int height(Gate gate) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (long b : gate.blocks) {
            int y = BlockPos.getY(b);
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        return gate.blocks.isEmpty() ? 0 : max - min;
    }

    /**
     * Where the gate shows when lifted by n rows: the whole grid moves up together and what goes above its closed
     * shape is in the wall - a block of the shape shows if the block n rows under it belongs to the shape.
     */
    private static Set<Long> visible(Gate gate, int n) {
        Set<Long> out = new LinkedHashSet<>();
        for (long b : gate.blocks) if (gate.blocks.contains(BlockPos.offset(b, 0, -n, 0))) out.add(b);
        return out;
    }

    private static Vec3 center(Gate gate) {
        double x = 0, y = 0, z = 0;
        for (long b : gate.blocks) {
            BlockPos p = BlockPos.of(b);
            x += p.getX();
            y += p.getY();
            z += p.getZ();
        }
        int n = Math.max(1, gate.blocks.size());
        return new Vec3(x / n + 0.5, y / n + 0.5, z / n + 0.5);
    }

    // ------------------------------------------------------------------ who may use it

    private static boolean mayUse(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.isCreative()) return true;
        ValorisData data = ValorisData.get(level.getServer());
        Optional<City> owner = SiegeEvents.cityAt(data, level, pos);
        if (owner.isEmpty()) return true;
        Optional<City> mine = Roles.cityOfMember(player);
        if (mine.isEmpty()) return false;
        if (mine.get().teamId.equals(owner.get().teamId)) return true;
        return data.kingdomOfCity(owner.get().teamId).map(k -> k.cities.contains(mine.get().teamId)).orElse(false);
    }

    public static void toggle(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!mayUse(player, level, pos)) {
            ValorisData data = ValorisData.get(level.getServer());
            String realm = SiegeEvents.cityAt(data, level, pos).flatMap(c -> Realms.ofCity(data, c))
                    .map(r -> "the " + r.title()).orElse("this city");
            player.displayClientMessage(Msg.text("Only the people of " + realm + " can open this gate.", ChatFormatting.RED), true);
            return;
        }
        Gates gates = get(level.getServer());
        Gate gate = gates.track(level, pos);
        if (gate == null) return;
        if (MOVING.containsKey(gate.id)) return;
        boolean raising = gate.raised == 0;
        if (!raising && blocked(level, gate, gate.raised - 1)) {
            player.displayClientMessage(Msg.text("Something is in the way: the gate cannot close.", ChatFormatting.RED), true);
            return;
        }
        MOVING.put(gate.id, new Motion(level, raising));
        if (raising) OPENED.put(gate.id, level.getServer().getTickCount());
        else OPENED.remove(gate.id);
        Vec3 c = center(gate);
        level.playSound(null, c.x, c.y, c.z, raising ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    // ------------------------------------------------------------------ raising and lowering

    private static final class Motion {
        final ServerLevel level;
        boolean raising; // a gate going down goes back up when something is in its way
        int k;

        Motion(ServerLevel level, boolean raising) {
            this.level = level;
            this.raising = raising;
        }
    }

    /** True if a block the gate cannot replace stands where it shows when lifted by n rows. */
    private static boolean blocked(ServerLevel level, Gate gate, int n) {
        for (long b : visible(gate, n)) {
            BlockState s = level.getBlockState(BlockPos.of(b));
            if (!isGate(s) && !s.canBeReplaced()) return true;
        }
        return false;
    }

    /** The blocks of the set that are portcullis blocks in the world. */
    private static List<BlockPos> present(ServerLevel level, Set<Long> blocks) {
        List<BlockPos> out = new ArrayList<>();
        for (long b : blocks) {
            BlockPos p = BlockPos.of(b);
            if (isGate(level.getBlockState(p))) out.add(p);
        }
        return out;
    }

    private static void shift(ServerLevel level, List<BlockPos> blocks, int k) {
        for (BlockPos p : blocks) {
            BlockState s = level.getBlockState(p);
            if (isGate(s) && s.getValue(PortcullisBlock.SHIFT) != k) level.setBlock(p, s.setValue(PortcullisBlock.SHIFT, k), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * One step (a tick) of a moving gate; true when it is done. The whole grid moves a pixel (SHIFT); after 16 the
     * gate is a row higher or lower: the blocks that went into the wall are taken away, or the ones coming out of it
     * set in (where nothing solid is in the way).
     */
    private boolean step(Gate gate, Motion m) {
        ServerLevel level = m.level;
        Vec3 c = center(gate);
        if (m.raising) {
            if (gate.raised >= height(gate)) return true;
            m.k++;
            if (m.k < STEPS) {
                shift(level, present(level, visible(gate, gate.raised)), m.k);
                return false;
            }
            gate.raised++;
            setDirty();
            Set<Long> next = visible(gate, gate.raised);
            for (long b : gate.blocks) {
                BlockPos p = BlockPos.of(b);
                if (!next.contains(b) && isGate(level.getBlockState(p))) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            shift(level, present(level, next), 0);
            m.k = 0;
            level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
            return gate.raised >= height(gate);
        }
        if (m.k == 0) {
            if (gate.raised <= 0) return true;
            if (blocked(level, gate, gate.raised - 1)) { // a block was put under it: back up
                m.raising = true;
                level.playSound(null, c.x, c.y, c.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 0.8F, 1.2F);
                return false;
            }
            gate.raised--;
            setDirty();
            Set<Long> next = visible(gate, gate.raised);
            for (long b : next) {
                BlockPos p = BlockPos.of(b);
                BlockState s = level.getBlockState(p);
                if (!isGate(s) && s.canBeReplaced()) {
                    level.setBlock(p, ModBlocks.PORTCULLIS.get().defaultBlockState()
                            .setValue(PortcullisBlock.AXIS, gate.axis).setValue(PortcullisBlock.SHIFT, STEPS - 1)
                            .setValue(PortcullisBlock.BOTTOM, !isGate(level.getBlockState(p.below()))), Block.UPDATE_ALL);
                }
            }
            m.k = STEPS - 1;
            level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
        } else {
            m.k--;
        }
        shift(level, present(level, visible(gate, gate.raised)), m.k);
        return false;
    }

    /** Ends a movement at once (server stopping): raised to its top row, or lowered as far as it goes. */
    private void finish(Gate gate, Motion m) {
        int guard = 0;
        while (!step(gate, m) && guard++ < STEPS * MAX_BLOCKS) { }
        setDirty();
    }


    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && isGate(event.getPlacedBlock())) get(level.getServer()).track(level, event.getPos());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isGate(event.getState())) return;
        BlockPos pos = event.getPos();
        Gates gates = get(level.getServer());
        Gate gate = gates.at(level, pos);
        if (gate != null && MOVING.containsKey(gate.id)) {
            event.setCanceled(true);
            return;
        }
        boolean creative = event.getPlayer() != null && event.getPlayer().isCreative();
        if (!creative && SiegeEvents.activeSiegeAt(ValorisData.get(level.getServer()), level, pos).isPresent()) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer p) {
                p.displayClientMessage(Msg.text("A gate cannot be broken by hand during a siege. Bring siege machines!", ChatFormatting.RED), true);
            }
            return;
        }
        if (gate != null && gate.raised == 0) { // a closed gate loses the block for good
            gate.blocks.remove(pos.asLong());
            if (gate.blocks.isEmpty()) gates.gates.remove(gate.id);
            gates.reindex();
            gates.setDirty();
        }
    }

    /**
     * The gate whose frame this block is: a block right next to the gate's closed shape (diagonals, in front and
     * behind too). Null if it is none.
     */
    private Gate frameOf(ServerLevel level, BlockPos pos) {
        for (BlockPos n : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            Gate g = at(level, n);
            if (g == null && isGate(level.getBlockState(n))) g = track(level, n.immutable());
            if (g != null) return g;
        }
        return null;
    }

    /**
     * Gates fall only by their hit points, and so does their frame: an explosion leaves both standing, and a siege
     * machine's hit on the frame takes a hit point of the gate like a hit on the gate itself. Once the gate is
     * destroyed its frame is a wall like any other.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Gates gates = get(level.getServer());
        Set<Gate> framed = new LinkedHashSet<>();
        event.getAffectedBlocks().removeIf(p -> {
            BlockState s = level.getBlockState(p);
            if (isGate(s)) return true;
            if (s.isAir()) return false;
            Gate g = gates.frameOf(level, p);
            if (g == null) return false;
            framed.add(g);
            return true;
        });
        if (!event.getExplosion().getClass().getName().equals(MISSILE_EXPLOSION)) return;
        ValorisData data = ValorisData.get(level.getServer());
        BlockPos center = BlockPos.containing(event.getExplosion().center());
        Set<Gate> hit = new LinkedHashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (!isGate(level.getBlockState(p))) continue;
            if (SiegeEvents.activeSiegeAt(data, level, p).isEmpty()) continue;
            Gate g = gates.track(level, p.immutable());
            if (g != null) hit.add(g);
        }
        for (Gate g : framed) {
            if (g.blocks.isEmpty() || !gates.gates.containsKey(g.id)) continue;
            if (SiegeEvents.activeSiegeAt(data, level, BlockPos.of(g.blocks.iterator().next())).isPresent()) hit.add(g);
        }
        int max = Config.GATE_HITS.get();
        for (Gate g : hit) {
            g.hits++;
            gates.setDirty();
            Vec3 c = center(g);
            if (g.hits < max) {
                level.playSound(null, c.x, c.y, c.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 2.0F, 0.7F);
                continue;
            }
            MOVING.remove(g.id);
            for (long b : g.blocks) {
                BlockPos p = BlockPos.of(b);
                if (isGate(level.getBlockState(p))) level.destroyBlock(p, false);
            }
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 3.0F, 0.6F);
            gates.gates.remove(g.id);
        }
        if (!hit.isEmpty()) gates.reindex();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ticks++;
        if (ticks % STEP_TICKS == 0 && !MOVING.isEmpty()) {
            Gates gates = get(server);
            for (var it = MOVING.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                Gate gate = gates.gates.get(e.getKey());
                if (gate == null) { it.remove(); continue; }
                if (gates.step(gate, e.getValue())) {
                    gates.setDirty();
                    it.remove();
                }
            }
        }
        if (ticks % 20 == 0) {
            heal(server);
            autoClose(server);
        }
    }

    /** Lowers the open gates whose time is up; a gate with something in its way tries again every second. */
    private static void autoClose(MinecraftServer server) {
        int after = Config.GATE_AUTO_CLOSE_SECONDS.get() * 20;
        if (after <= 0) return;
        Gates gates = get(server);
        int now = server.getTickCount();
        OPENED.keySet().removeIf(id -> !gates.gates.containsKey(id));
        for (Gate g : gates.gates.values()) {
            if (g.raised <= 0 && !MOVING.containsKey(g.id)) { OPENED.remove(g.id); continue; }
            Integer since = OPENED.putIfAbsent(g.id, now);
            if (since == null || now - since < after || MOVING.containsKey(g.id)) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(g.dim)));
            if (level == null || g.blocks.isEmpty() || !level.isLoaded(BlockPos.of(g.blocks.iterator().next()))) continue;
            if (blocked(level, g, g.raised - 1)) continue;
            MOVING.put(g.id, new Motion(level, false));
            OPENED.remove(g.id);
            Vec3 c = center(g);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
    }

    /** Answer to a client looking at a gate: its hit points during a siege on its land (max 0: none to show). */
    public static void sendInfo(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(pos)) return;
        if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > INFO_RANGE * INFO_RANGE || !isGate(level.getBlockState(pos))) return;
        int left = 0, max = 0;
        if (SiegeEvents.activeSiegeAt(ValorisData.get(level.getServer()), level, pos).isPresent()) {
            Gates gates = get(level.getServer());
            Gate g = gates.at(level, pos);
            if (g == null) g = gates.track(level, pos);
            if (g != null) {
                max = Config.GATE_HITS.get();
                left = Math.max(0, max - g.hits);
            }
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.valoris.net.GateInfoPayload(pos, left, max));
    }

    /** After the siege the gate is whole again. */
    private static void heal(MinecraftServer server) {
        Gates gates = get(server);
        ValorisData data = ValorisData.get(server);
        for (Gate g : gates.gates.values()) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(g.dim)));
            if (level == null || g.blocks.isEmpty()) continue;
            BlockPos any = BlockPos.of(g.blocks.iterator().next());
            if (!level.isLoaded(any)) continue;
            if (g.hits > 0 && SiegeEvents.activeSiegeAt(data, level, any).isEmpty()) {
                g.hits = 0;
                gates.setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        Gates gates = get(event.getServer());
        for (var e : MOVING.entrySet()) {
            Gate gate = gates.gates.get(e.getKey());
            if (gate != null) gates.finish(gate, e.getValue());
        }
        MOVING.clear();
    }

    // ------------------------------------------------------------------ saving

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Gate g : gates.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", g.id);
            t.putString("dim", g.dim);
            t.putString("axis", g.axis.getName());
            t.putLongArray("blocks", g.blocks.stream().mapToLong(Long::longValue).toArray());
            t.putInt("raised", g.raised);
            t.putInt("hits", g.hits);
            list.add(t);
        }
        tag.put("gates", list);
        return tag;
    }

    public static Gates load(CompoundTag tag, HolderLookup.Provider registries) {
        Gates data = new Gates();
        for (Tag raw : tag.getList("gates", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Direction.Axis axis = Direction.Axis.byName(t.getString("axis"));
            Gate g = new Gate(t.getUUID("id"), t.getString("dim"), axis == null ? Direction.Axis.X : axis);
            for (long b : t.getLongArray("blocks")) g.blocks.add(b);
            g.raised = t.getInt("raised");
            g.hits = t.getInt("hits");
            if (!g.blocks.isEmpty()) data.gates.put(g.id, g);
        }
        data.reindex();
        return data;
    }
}
