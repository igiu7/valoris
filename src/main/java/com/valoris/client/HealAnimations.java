package com.valoris.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.valoris.heal.Healing;
import com.valoris.registry.ModPlants;
import com.valoris.valoris2;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Use animations of the healing items, instead of eating:
 * - bandage and poultice: the arm goes across the body and circles, wrapping the other forearm;
 * - herbs: the hand presses to the chest and rubs up and down.
 * The third-person pose is computed on every client from the synced "using item" state, so all players see it.
 * The third-person pose is applied by HumanoidModelMixin, see HealPoses.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class HealAnimations {
    private HealAnimations() {}

    private static Healing.Kind kindOf(ItemStack stack) {
        return HealPoses.kindOf(stack);
    }

    private static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            // vanilla pose; the actual arm movement is applied by HumanoidModelMixin
            return null;
        }

        /** First person: the item moves to the middle of the view and circles (wrapping) or rubs (herbs). */
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                               float partialTick, float equipProcess, float swingProcess) {
            if (!player.isUsingItem() || player.getUseItem() != stack) return false;
            Healing.Kind kind = kindOf(stack);
            if (kind == null) return false;
            float s = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            float t = player.getTicksUsingItem() + partialTick;
            pose.translate(s * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
            if (kind.wrapping) {
                pose.translate(s * -0.32F + Mth.cos(t * 0.55F) * 0.07F, 0.12F + Mth.sin(t * 0.55F) * 0.06F, 0.12F);
                pose.mulPose(Axis.YP.rotationDegrees(s * 30.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(s * (12.0F + Mth.sin(t * 0.55F) * 14.0F)));
            } else {
                pose.translate(s * -0.22F, 0.02F + Mth.sin(t * 0.9F) * 0.07F, 0.18F);
                pose.mulPose(Axis.XP.rotationDegrees(-15.0F + Mth.sin(t * 0.9F) * 8.0F));
                pose.mulPose(Axis.YP.rotationDegrees(s * 40.0F));
            }
            return true;
        }
    };

    @SubscribeEvent
    public static void onRegister(RegisterClientExtensionsEvent event) {
        event.registerItem(EXTENSIONS, ModPlants.MEDICINAL_HERBS.get(), ModPlants.LINEN_BANDAGE.get(),
                ModPlants.HONEY_POULTICE.get(), ModPlants.HONEY_HERB_POULTICE.get(),
                ModPlants.MEDICINAL_ROOT.get(), ModPlants.HONEY_ROOT_POULTICE.get(), ModPlants.HERB_ROOT_POULTICE.get());
    }
}
