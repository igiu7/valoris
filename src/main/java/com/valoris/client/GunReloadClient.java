package com.valoris.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.valoris.compat.Compat;

import net.minecraft.client.Minecraft;

/**
 * The server interrupted the local player's gun reload (compat.GunReloadInterrupt): stop the reload animation.
 * State machines that know "cancel_reload" handle it; the default one (used by the muskets) does not, so the
 * reload animation on its main track is replaced by the draw animation (the gun is raised again), or stopped.
 */
public final class GunReloadClient {
    private GunReloadClient() {}

    // the main track of the default state machine (tacz default_state_machine.lua: STATIC_TRACK_LINE, MAIN_TRACK)
    private static final int STATIC_TRACK_LINE = 0, MAIN_TRACK = 9;

    public static void interrupted() {
        if (!Compat.TACZ) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).ifPresent(display -> {
            var machine = display.getAnimationStateMachine();
            if (machine == null) return;
            machine.trigger("cancel_reload");
            AnimationStateContext context = machine.getContext();
            if (context == null) return;
            int track = context.getTrack(STATIC_TRACK_LINE, MAIN_TRACK);
            if (context.hasAnimationPrototype("draw")) {
                context.runAnimation("draw", track, false, ObjectAnimation.PlayType.PLAY_ONCE_STOP.ordinal(), 0.2F);
            } else {
                context.stopAnimation(track);
            }
        });
    }
}
