package com.valoris.client.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * Custom game music: every .mp3 in assets/valoris2/music/ (in the mod, or in a resource pack) is a track.
 * A random track plays, then after a short pause another random one (never the same twice in a row), forever.
 * While there is at least one track the vanilla music is silenced. Volume follows the "Music" slider.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class ValorisMusic {
    private static final String FOLDER = "music";
    private static final int MIN_PAUSE_TICKS = 3 * 20;
    private static final int MAX_PAUSE_TICKS = 10 * 20;

    private static List<ResourceLocation> tracks = List.of();
    private static Mp3SoundInstance current;
    private static ResourceLocation last;
    private static int pause = 40;
    private static final RandomSource RANDOM = RandomSource.create();

    private ValorisMusic() {}

    public static List<ResourceLocation> tracks() {
        return tracks;
    }

    /** Finds the tracks again whenever resources are (re)loaded (F3+T, resource packs). */
    @SubscribeEvent
    public static void onRegisterReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            Map<ResourceLocation, Resource> found = manager.listResources(FOLDER,
                    id -> id.getNamespace().equals(valoris2.MODID) && id.getPath().endsWith(".mp3"));
            List<ResourceLocation> list = new ArrayList<>(found.keySet());
            list.sort(null);
            tracks = List.copyOf(list);
            current = null;
            valoris2.LOGGER.info("Valoris music: {} track(s) found", tracks.size());
        });
    }

    /** Silence the vanilla music while we have our own. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSelectMusic(SelectMusicEvent event) {
        if (!tracks.isEmpty()) event.setMusic(null);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (tracks.isEmpty() || mc.getSoundManager() == null) return;
        if (current != null && mc.getSoundManager().isActive(current)) return;
        if (current != null) {
            current = null;
            pause = MIN_PAUSE_TICKS + RANDOM.nextInt(MAX_PAUSE_TICKS - MIN_PAUSE_TICKS + 1);
        }
        if (pause > 0) {
            pause--;
            return;
        }
        ResourceLocation next = tracks.get(RANDOM.nextInt(tracks.size()));
        if (tracks.size() > 1) {
            while (next.equals(last)) next = tracks.get(RANDOM.nextInt(tracks.size()));
        }
        last = next;
        current = new Mp3SoundInstance(next);
        mc.getSoundManager().play(current);
    }

    /** Stops the current track; another random one starts right away. */
    public static void skip() {
        Minecraft mc = Minecraft.getInstance();
        if (current != null) mc.getSoundManager().stop(current);
        current = null;
        pause = 0;
    }
}
