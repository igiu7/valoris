package com.valoris.client;

import com.valoris.heal.Healing;
import com.valoris.heal.HealingBlockItem;
import com.valoris.heal.HealingItem;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Third-person arm poses of the healing items. Applied at the end of HumanoidModel.setupAnim
 * (see HumanoidModelMixin), on every client, from the synced "using item" state, so all players see it.
 */
public final class HealPoses {
    private HealPoses() {}

    public static Healing.Kind kindOf(ItemStack stack) {
        if (stack.getItem() instanceof HealingItem h) return h.kind();
        if (stack.getItem() instanceof HealingBlockItem h) return h.kind();
        return null;
    }

    public static void apply(HumanoidModel<?> model, LivingEntity entity) {
        if (entity == null || !entity.isUsingItem()) return;
        Healing.Kind kind = kindOf(entity.getUseItem());
        if (kind == null) return;
        HumanoidArm side = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? entity.getMainArm() : entity.getMainArm().getOpposite();
        ModelPart part = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float s = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float t = entity.getTicksUsingItem();
        float crouch = model.crouching ? 0.4F : 0.0F;
        if (kind.wrapping) {
            part.xRot = -1.05F + Mth.sin(t * 0.55F) * 0.2F + crouch;
            part.yRot = s * (-0.6F + Mth.cos(t * 0.55F) * 0.25F);
            part.zRot = 0.0F;
        } else {
            part.xRot = -1.25F + Mth.sin(t * 0.9F) * 0.14F + crouch;
            part.yRot = s * -0.85F;
            part.zRot = s * (0.15F + Mth.sin(t * 0.9F) * 0.06F);
        }
    }
}
