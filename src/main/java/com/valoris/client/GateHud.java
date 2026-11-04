package com.valoris.client;

import com.valoris.gate.PortcullisBlock;
import com.valoris.net.GateInfoPayload;
import com.valoris.net.GateInfoRequestPayload;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Under the crosshair: the hit points of what you look at up to 64 blocks away - a gate during a siege on its land
 * (asked from the server), or a siege machine of Siege Machines (its health, known to the client).
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class GateHud {
    private GateHud() {}

    private static final double RANGE = 64.0;
    private static BlockPos target;
    private static int sinceRequest;
    private static GateInfoPayload info;
    private static long infoAt;
    private static LivingEntity machine;
    private static final String MACHINE = "ru.magistu.siegemachines.entity.machine.Machine";

    private static boolean isMachine(Entity e) {
        for (Class<?> c = e.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals(MACHINE)) return true;
        return false;
    }

    /** The siege machine under the crosshair, if it is nearer than the block there. */
    private static LivingEntity machineAt(Minecraft mc, Entity camera, HitResult blockHit) {
        Vec3 eye = camera.getEyePosition(1.0F);
        Vec3 end = eye.add(camera.getViewVector(1.0F).scale(RANGE));
        double max = blockHit.getType() == HitResult.Type.MISS ? RANGE * RANGE : blockHit.getLocation().distanceToSqr(eye);
        AABB box = camera.getBoundingBox().expandTowards(camera.getViewVector(1.0F).scale(RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && isMachine(e), max);
        return hit == null ? null : (LivingEntity) hit.getEntity();
    }

    public static void receive(GateInfoPayload payload) {
        info = payload;
        infoAt = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (mc.level == null || camera == null) {
            target = null;
            machine = null;
            return;
        }
        HitResult hit = camera.pick(RANGE, 1.0F, false);
        machine = machineAt(mc, camera, hit);
        if (machine != null) {
            target = null;
            return;
        }
        BlockPos pos = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockState(b.getBlockPos()).getBlock() instanceof PortcullisBlock ? b.getBlockPos() : null;
        if (pos == null) {
            target = null;
            return;
        }
        if (!pos.equals(target) || ++sinceRequest >= 10) {
            target = pos;
            sinceRequest = 0;
            PacketDistributor.sendToServer(new GateInfoRequestPayload(pos));
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        if (machine != null && machine.isAlive()) {
            float hp = Math.max(0, machine.getHealth()), max = Math.max(1, machine.getMaxHealth());
            bar(g, mc, machine.getDisplayName().getString() + " " + (int) Math.ceil(hp) + "/" + (int) Math.ceil(max), hp / max, 0);
            return;
        }
        if (info == null || target == null) return;
        if (!info.pos().equals(target) || info.max() <= 0 || System.currentTimeMillis() - infoAt > 2000) return;
        bar(g, mc, "Gate " + info.left() + "/" + info.max(), info.left() / (float) info.max(), info.max());
    }

    /** A label and a red bar under the crosshair; notches > 1 marks every hit. */
    private static void bar(GuiGraphics g, Minecraft mc, String label, float fill, int notches) {
        int x = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 + 12;
        g.drawCenteredString(mc.font, label, x, y, 0xFF5555);
        int w = 60, left = x - w / 2, top = y + 11;
        g.fill(left - 1, top - 1, left + w + 1, top + 5, 0xFF000000);
        g.fill(left, top, left + w, top + 4, 0xFF3A1010);
        g.fill(left, top, left + Math.round(w * Math.max(0, Math.min(1, fill))), top + 4, 0xFFD02020);
        for (int i = 1; i < notches; i++) { // a notch for every hit
            int nx = left + Math.round(w * i / (float) notches);
            g.fill(nx, top, nx + 1, top + 4, 0xFF000000);
        }
    }
}
