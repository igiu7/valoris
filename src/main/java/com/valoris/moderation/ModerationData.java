package com.valoris.moderation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-persistent moderation data: mutes, frozen players, the punishment history and the last known name and IP
 * of every player (for offline IP bans and alt detection). Bans themselves live in the vanilla ban lists.
 */
public class ModerationData extends SavedData {
    private static final String NAME = "valoris_moderation";

    /** until = epoch millis, 0 = permanent. */
    public record Mute(long until, String reason, String by, long at) {
        public boolean expired(long now) { return until > 0 && now >= until; }
    }

    /** One line of a player's record. duration = millis, 0 = permanent, -1 = not timed. */
    public record Entry(String type, String reason, String by, long at, long duration) {}

    final Map<UUID, Mute> mutes = new HashMap<>();
    final Set<UUID> frozen = new LinkedHashSet<>();
    final Map<UUID, List<Entry>> history = new HashMap<>();
    final Map<UUID, String> lastIp = new HashMap<>();
    final Map<UUID, String> names = new HashMap<>();
    /** Players who left during combat; banned when they join again (see CombatTag). */
    final Set<UUID> combatLogged = new LinkedHashSet<>();

    public static ModerationData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ModerationData::new, ModerationData::load, null), NAME);
    }

    void log(UUID player, Entry e) {
        List<Entry> list = history.computeIfAbsent(player, k -> new ArrayList<>());
        list.add(e);
        while (list.size() > 200) list.remove(0);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag m = new ListTag();
        mutes.forEach((id, mute) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putLong("until", mute.until());
            t.putString("reason", mute.reason());
            t.putString("by", mute.by());
            t.putLong("at", mute.at());
            m.add(t);
        });
        tag.put("mutes", m);

        ListTag f = new ListTag();
        for (UUID id : frozen) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            f.add(t);
        }
        tag.put("frozen", f);

        ListTag cl = new ListTag();
        for (UUID id : combatLogged) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            cl.add(t);
        }
        tag.put("combatLogged", cl);

        ListTag h = new ListTag();
        history.forEach((id, list) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            ListTag entries = new ListTag();
            for (Entry e : list) {
                CompoundTag et = new CompoundTag();
                et.putString("type", e.type());
                et.putString("reason", e.reason());
                et.putString("by", e.by());
                et.putLong("at", e.at());
                et.putLong("duration", e.duration());
                entries.add(et);
            }
            t.put("entries", entries);
            h.add(t);
        });
        tag.put("history", h);

        ListTag p = new ListTag();
        names.forEach((id, name) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putString("ip", lastIp.getOrDefault(id, ""));
            p.add(t);
        });
        tag.put("players", p);
        return tag;
    }

    public static ModerationData load(CompoundTag tag, HolderLookup.Provider registries) {
        ModerationData d = new ModerationData();
        for (Tag raw : tag.getList("mutes", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            d.mutes.put(t.getUUID("id"), new Mute(t.getLong("until"), t.getString("reason"), t.getString("by"), t.getLong("at")));
        }
        for (Tag raw : tag.getList("frozen", Tag.TAG_COMPOUND)) d.frozen.add(((CompoundTag) raw).getUUID("id"));
        for (Tag raw : tag.getList("combatLogged", Tag.TAG_COMPOUND)) d.combatLogged.add(((CompoundTag) raw).getUUID("id"));
        for (Tag raw : tag.getList("history", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            List<Entry> list = new ArrayList<>();
            for (Tag er : t.getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) er;
                list.add(new Entry(e.getString("type"), e.getString("reason"), e.getString("by"), e.getLong("at"), e.getLong("duration")));
            }
            d.history.put(t.getUUID("id"), list);
        }
        for (Tag raw : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            d.names.put(t.getUUID("id"), t.getString("name"));
            if (!t.getString("ip").isEmpty()) d.lastIp.put(t.getUUID("id"), t.getString("ip"));
        }
        return d;
    }
}
