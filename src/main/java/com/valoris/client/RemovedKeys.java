package com.valoris.client;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.valoris.valoris2;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Key bindings of other mods that are taken out of the game: Iris toggles the shaders with K, the key of the Valoris
 * menu. On the first client tick they are unbound and removed from the options (so from the Controls screen too).
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class RemovedKeys {
    private RemovedKeys() {}

    private static final Set<String> REMOVED = Set.of("iris.keybind.toggleShaders");
    private static boolean done;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (done) return;
        done = true;
        Options options = Minecraft.getInstance().options;
        KeyMapping[] all = options.keyMappings;
        KeyMapping[] kept = Arrays.stream(all).filter(k -> !REMOVED.contains(k.getName())).toArray(KeyMapping[]::new);
        if (kept.length == all.length) return;
        for (KeyMapping k : all) if (REMOVED.contains(k.getName())) k.setKey(InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        try {
            Field f = Options.class.getDeclaredField("keyMappings");
            f.setAccessible(true);
            f.set(options, kept);
        } catch (ReflectiveOperationException | RuntimeException e) {
            valoris2.LOGGER.warn("Could not remove key bindings {} from the options (they stay unbound)", REMOVED, e);
        }
    }
}
