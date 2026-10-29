package com.valoris.net;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;

/**
 * One technology as shown in the tree screen.
 * status: 0 locked (prerequisites), 1 needs skills, 2 can be researched, 3 being researched, 4 known.
 * skills: required level per skill (Skill order, 0 = no requirement).
 * remainingSeconds: time left when this snapshot was built (only while status is 3).
 */
public record TechRow(String id, String era, String eraName, String name, String icon, String description,
                      int x, int y, List<String> requires, int[] skills, List<String> features,
                      int status, int researchSeconds, int remainingSeconds) {

    public static final int LOCKED = 0, NEEDS_SKILLS = 1, AVAILABLE = 2, RESEARCHING = 3, KNOWN = 4, ERA_CLOSED = 5;

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(era);
        buf.writeUtf(eraName);
        buf.writeUtf(name);
        buf.writeUtf(icon);
        buf.writeUtf(description);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeCollection(requires, (b, s) -> b.writeUtf(s));
        buf.writeVarIntArray(skills);
        buf.writeCollection(features, (b, s) -> b.writeUtf(s));
        buf.writeVarInt(status);
        buf.writeVarInt(researchSeconds);
        buf.writeVarInt(remainingSeconds);
    }

    public static TechRow read(FriendlyByteBuf buf) {
        return new TechRow(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readList(b -> b.readUtf()), buf.readVarIntArray(),
                buf.readList(b -> b.readUtf()), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
