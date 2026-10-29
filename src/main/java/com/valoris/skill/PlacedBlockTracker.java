package com.valoris.skill;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.valoris.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Remembers blocks placed by players for a while, so placing and breaking the same block again
 * (stone, ores via silk touch, melons, cane...) does not give skill XP. Kept in memory only.
 */
public final class PlacedBlockTracker {
    private static final Map<String, Map<Long, Long>> PLACED = new HashMap<>();

    private PlacedBlockTracker() {}

    private static String key(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static void placed(ServerLevel level, BlockPos pos) {
        PLACED.computeIfAbsent(key(level), k -> new HashMap<>()).put(pos.asLong(), level.getGameTime());
    }

    /** True (and forgets the entry) if a player placed this block recently. */
    public static boolean consume(ServerLevel level, BlockPos pos) {
        Map<Long, Long> map = PLACED.get(key(level));
        if (map == null) return false;
        Long when = map.remove(pos.asLong());
        return when != null && level.getGameTime() - when < Config.PLACED_MEMORY_MINUTES.get() * 1200L;
    }

    /** Drops expired entries. Called once a minute. */
    public static void purge(long gameTime) {
        long maxAge = Config.PLACED_MEMORY_MINUTES.get() * 1200L;
        for (Map<Long, Long> map : PLACED.values()) {
            Iterator<Map.Entry<Long, Long>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                if (gameTime - it.next().getValue() >= maxAge) it.remove();
            }
        }
    }

    public static void clear() {
        PLACED.clear();
    }
}
