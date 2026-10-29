package com.valoris.mobs;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;

/** Pack members keep close to their leader; the leader wanders with the vanilla stroll goal. */
public class PackFollowLeaderGoal extends Goal {
    private final Wolf wolf;
    private Wolf leader;
    private int recheck;
    private int repath;

    public PackFollowLeaderGoal(Wolf wolf) {
        this.wolf = wolf;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (wolf.isTame() || wolf.getTarget() != null || wolf.isLeashed() || !WolfPacks.inPack(wolf)) return false;
        if (--recheck > 0) return leader != null && leader.isAlive() && wolf.distanceToSqr(leader) > 64.0;
        recheck = adjustedTickDelay(20);
        leader = WolfPacks.leaderOf(wolf);
        return leader != null && wolf.distanceToSqr(leader) > 64.0;
    }

    @Override
    public boolean canContinueToUse() {
        return leader != null && leader.isAlive() && wolf.getTarget() == null && !wolf.isTame()
                && wolf.distanceToSqr(leader) > 9.0;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        wolf.getNavigation().stop();
    }

    @Override
    public void tick() {
        wolf.getLookControl().setLookAt(leader, 10.0F, wolf.getMaxHeadXRot());
        if (--repath <= 0) {
            repath = adjustedTickDelay(10);
            double d = wolf.distanceToSqr(leader);
            wolf.getNavigation().moveTo(leader, d > 400.0 ? 1.35 : 1.1);
        }
    }
}
