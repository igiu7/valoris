package com.valoris.compat;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Siege machines turn with the Sable sub-level (Create Aeronautics ship) they stand on. Sable carries entities on a
 * ship by position only; their facing is left alone. Here (all Sable code is in this class, see Compat.SABLE):
 * - shipUnder: the ship an entity stands on (Sable's tracking sub-level; on the client, where remote entities are
 *   not tracked, the sub-level whose blocks are right under its feet);
 * - yawDelta: how far a rotation between two ship orientations turns a horizontal facing (MC yaw degrees);
 * - the server turns each machine by its ship's turn of the tick (SiegeMachineShipMixin), and the client turns the
 *   local player riding one by the ship's turn of the frame (SiegeMachineRiderClient), as Sable does for a player
 *   standing on a ship - per frame, from the interpolated render pose, so the camera does not shake.
 */
public final class ShipRotation {
    private ShipRotation() {}

    /** The ship the entity stands on, or null. */
    public static SubLevel shipUnder(Entity entity) {
        SubLevel tracking = Sable.HELPER.getTrackingSubLevel(entity);
        if (tracking != null && !tracking.isRemoved()) return tracking;
        if (!entity.level().isClientSide) return null;
        // the client does not track remote entities: look for a ship whose blocks are just below the feet
        Vec3 feet = entity.position();
        BoundingBox3d box = new BoundingBox3d(entity.getBoundingBox().inflate(0.25).expandTowards(0, -1.5, 0));
        for (SubLevel sub : Sable.HELPER.getAllIntersecting(entity.level(), box)) {
            if (sub == null || sub.isRemoved()) continue;
            for (double down : new double[]{0.05, 0.6}) {
                Vec3 local = sub.logicalPose().transformPositionInverse(feet.subtract(0, down, 0));
                if (!Double.isFinite(local.x) || !Double.isFinite(local.y) || !Double.isFinite(local.z)) continue;
                if (!entity.level().getBlockState(BlockPos.containing(local)).isAir()) return sub;
            }
        }
        return null;
    }

    /** MC yaw change (degrees) of a horizontal facing turned from orientation `from` to `to`. 0 if undefined. */
    public static float yawDelta(Quaterniondc from, Quaterniondc to) {
        Quaterniond rel = new Quaterniond(to).mul(new Quaterniond(from).conjugate());
        Vector3d v = rel.transform(new Vector3d(0, 0, 1));
        if (v.x * v.x + v.z * v.z < 1e-8) return 0; // facing turned straight up or down: no yaw to speak of
        double deg = Math.toDegrees(Math.atan2(-v.x, v.z));
        return Double.isFinite(deg) ? Mth.wrapDegrees((float) deg) : 0;
    }

    /** The ship's turn during the last tick (server): lastPose -> logicalPose. */
    public static float tickDelta(SubLevel ship) {
        return yawDelta(ship.lastPose().orientation(), ship.logicalPose().orientation());
    }

    /** The interpolated render orientation of a client ship (null on the server). */
    public static Quaterniond renderOrientation(SubLevel ship) {
        return ship instanceof ClientSubLevel client ? new Quaterniond(client.renderPose().orientation()) : null;
    }

    /** Server: how far the ship under a machine turned during the last tick (0 if it stands on no ship). */
    public static float machineTickDelta(Entity machine) {
        SubLevel ship = shipUnder(machine);
        if (ship == null) return 0;
        float d = tickDelta(ship);
        // a ship never turns this far in one tick; such a value would only come from a teleport or a broken pose
        return Math.abs(d) < 1e-4F || Math.abs(d) > 45F ? 0 : d;
    }

    // the local rider (client only): the ship and its orientation at the previous frame
    private static java.util.UUID lastShip;
    private static Quaterniond lastOrientation;

    /**
     * Client, every frame: turns the local player riding a siege machine by the turn of the ship under the machine
     * since the last frame - yaw, head and body, the previous values too, so the view turns smoothly with the ship
     * (as Sable does itself for a player standing on a ship; it leaves riders alone). Pass null to forget the ship.
     */
    public static void riderFrame(Entity player, Entity vehicle) {
        SubLevel ship = vehicle == null ? null : shipUnder(vehicle);
        Quaterniond now = ship == null ? null : renderOrientation(ship);
        if (now == null || !Double.isFinite(now.x) || !Double.isFinite(now.y) || !Double.isFinite(now.z)
                || !Double.isFinite(now.w)) {
            lastShip = null;
            lastOrientation = null;
            return;
        }
        if (lastOrientation != null && ship.getUniqueId().equals(lastShip)) {
            float d = yawDelta(lastOrientation, now);
            if (Math.abs(d) > 1e-5F && Math.abs(d) < 45F) {
                turn(player, d);
                // the machine the player rides is moved and turned by this client (the server takes it from here),
                // and while it reloads or fires it does not turn after its rider: turn it with the ship as well
                turn(vehicle, d);
            }
        }
        lastShip = ship.getUniqueId();
        lastOrientation = now;
    }

    /** Turns an entity by d degrees of yaw: facing, head and body, the previous values too (smooth rendering). */
    public static void turn(Entity e, float d) {
        e.setYRot(e.getYRot() + d);
        e.yRotO += d;
        if (e instanceof net.minecraft.world.entity.LivingEntity living) {
            living.yHeadRot += d;
            living.yHeadRotO += d;
            living.yBodyRot += d;
            living.yBodyRotO += d;
        }
    }
}
