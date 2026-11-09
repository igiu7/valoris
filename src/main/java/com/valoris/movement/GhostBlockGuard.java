package com.valoris.movement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * No climbing on blocks the server refused. The client shows a placed block at once; when the server refuses it (a
 * claim, the siege's one-block-every-few-seconds rule...) it is gone again only after the server's answer arrives,
 * and for that moment the player can stand and jump on it. So for 2 seconds after a refused placement a player
 * whose client says he stands on the ground, above one of those blocks with nothing real under him, is put back
 * where he was when the block was refused (mixin.GhostBlockMoveMixin checks every movement).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class GhostBlockGuard {
    private GhostBlockGuard() {}

    private static final long WINDOW = 40;

    private static final class Ghosts {
        final List<BlockPos> blocks = new ArrayList<>();
        Vec3 safe;
        ServerLevel level;
        long until;
    }

    private static final Map<UUID, Ghosts> GHOSTS = new HashMap<>();

    /** A refused right click with a block (claim protection and the like). */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem)) return;
        refused(player, new BlockPlaceContext(player, event.getHand(), stack, event.getHitVec()).getClickedPos());
    }

    /** A refused placement (the siege's building rule and the like). */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() && event.getEntity() instanceof ServerPlayer player) refused(player, event.getPos());
    }

    private static void refused(ServerPlayer player, BlockPos pos) {
        long now = player.serverLevel().getGameTime();
        Ghosts g = GHOSTS.get(player.getUUID());
        if (g == null || now > g.until || g.level != player.serverLevel()) {
            g = new Ghosts();
            g.safe = player.position(); // the first refusal of a row: where he stood before any of them
            g.level = player.serverLevel();
            GHOSTS.put(player.getUUID(), g);
        }
        g.blocks.add(pos.immutable());
        g.until = now + WINDOW;
    }

    /** After each movement the client sends. */
    public static void afterMove(ServerPlayer player) {
        if (GHOSTS.isEmpty()) return;
        Ghosts g = GHOSTS.get(player.getUUID());
        if (g == null) return;
        ServerLevel level = player.serverLevel();
        if (level.getGameTime() > g.until || g.level != level) {
            GHOSTS.remove(player.getUUID());
            return;
        }
        if (!player.onGround() || player.isPassenger() || player.isCreative() || player.isSpectator()
                || player.getAbilities().flying) return;
        AABB feet = player.getBoundingBox().expandTowards(0, -0.1, 0);
        if (!level.noCollision(player, feet)) return; // something real holds him
        boolean onGhost = false;
        for (BlockPos pos : g.blocks) {
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && feet.intersects(new AABB(pos))) onGhost = true;
        }
        if (!onGhost) return;
        for (BlockPos pos : g.blocks) player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        player.connection.teleport(g.safe.x, g.safe.y, g.safe.z, player.getYRot(), player.getXRot());
        GHOSTS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GHOSTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        GHOSTS.clear();
    }
}
