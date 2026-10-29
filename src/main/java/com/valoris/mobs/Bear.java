package com.valoris.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A big brown bear: lots of health and a heavy blow, but poor senses - it notices a player only close by
 * (config mobs.bearDetection, 10 blocks) and soon gives up a chase. When it spots someone it rears up and roars,
 * then charges; every blow is a swipe of its front paws. Lives alone in tier 2 and 3 land (see BearSpawns).
 * Drops raw bear meat and bear fur (loot table entities/bear). Animations: client.BearModel.
 */
public class Bear extends PathfinderMob {
    public static final int ATTACK_TICKS = 14;
    public static final int ROAR_TICKS = 30;
    private static final byte EVENT_ATTACK = 4;
    private static final byte EVENT_ROAR = 5;

    // count down on both sides; the client gets them through entity events
    private int attackTicks;
    private int roarTicks;

    public Bear(EntityType<? extends Bear> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.35, true) {
            @Override
            public boolean canUse() {
                return roarTicks <= 0 && super.canUse(); // the roar comes first
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, null));
    }

    // ------------------------------------------------------------------ attack and roar

    @Override
    public boolean doHurtTarget(Entity target) {
        attackTicks = ATTACK_TICKS;
        level().broadcastEntityEvent(this, EVENT_ATTACK);
        return super.doHurtTarget(target);
    }

    @Override
    public void setTarget(LivingEntity target) {
        // spotting a new enemy: rear up and roar
        if (target != null && target != getTarget() && !level().isClientSide() && isAlive()) {
            roarTicks = ROAR_TICKS;
            level().broadcastEntityEvent(this, EVENT_ROAR);
            playSound(SoundEvents.POLAR_BEAR_WARNING, 1.6F, 0.7F);
            getNavigation().stop();
        }
        super.setTarget(target);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_ATTACK) attackTicks = ATTACK_TICKS;
        else if (id == EVENT_ROAR) roarTicks = ROAR_TICKS;
        else super.handleEntityEvent(id);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (attackTicks > 0) attackTicks--;
        if (roarTicks > 0) {
            roarTicks--;
            if (!level().isClientSide()) getNavigation().stop(); // stands still while roaring
        }
    }

    /** 0..1 through the paw swipe, 0 when not attacking. */
    public float attackProgress(float partialTick) {
        return attackTicks <= 0 ? 0 : Mth.clamp(1.0F - (attackTicks - partialTick) / ATTACK_TICKS, 0, 1);
    }

    /** 0..1 through the roar, 0 when not roaring. */
    public float roarProgress(float partialTick) {
        return roarTicks <= 0 ? 0 : Mth.clamp(1.0F - (roarTicks - partialTick) / ROAR_TICKS, 0, 1);
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.POLAR_BEAR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.POLAR_BEAR_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.POLAR_BEAR_STEP, 0.2F, 0.8F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.75F; // deeper than a polar bear
    }

    @Override
    protected float getSoundVolume() {
        return 1.3F;
    }
}
