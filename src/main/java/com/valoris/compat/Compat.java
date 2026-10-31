package com.valoris.compat;

import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Which optional mods are present (checked once). Code using them must be reached only through these checks. */
public final class Compat {
    private Compat() {}

    public static final boolean SABLE = ModList.get().isLoaded("sable");
    public static final boolean SIEGE_MACHINES = ModList.get().isLoaded("siegemachines");
    public static final boolean TACZ = ModList.get().isLoaded("tacz");

    /** A Siege Machines machine (not its seats): every entity of that mod that can be ridden. */
    public static boolean isSiegeMachine(Entity e) {
        return SIEGE_MACHINES && e != null && !(e instanceof net.minecraft.world.entity.player.Player)
                && e instanceof net.minecraft.world.entity.Mob
                && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("siegemachines");
    }
}
