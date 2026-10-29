package com.valoris.client;

import com.valoris.registry.ModArmor;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Fur armor (registry.ModArmor): the outer armor model (helmet, coat, boots) with the animal's head on the hood -
 * a long snout and pointed ears for the wolf, a short wide snout and round ears for the bear - and a pelt hanging
 * down the back. Leggings use the plain inner model. Texture 64x64 (textures/models/armor/*_fur_layer_1.png).
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class FurArmorModel {
    private FurArmorModel() {}

    public static final ModelLayerLocation WOLF = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "wolf_fur_armor"), "main");
    public static final ModelLayerLocation BEAR = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "bear_fur_armor"), "main");

    private static LayerDefinition create(boolean bear) {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(1.0F), 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");
        if (!bear) {
            head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 32)
                    .addBox(-1.5F, -9.0F, -9.5F, 3, 3, 4), PartPose.ZERO);
            head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(16, 32)
                    .addBox(-3.0F, -10.5F, -6.0F, 6, 2, 5), PartPose.ZERO);
            head.addOrReplaceChild("ears", CubeListBuilder.create().texOffs(44, 32)
                    .addBox(-3.0F, -13.5F, -3.5F, 2, 3, 1)
                    .texOffs(44, 32).mirror().addBox(1.0F, -13.5F, -3.5F, 2, 3, 1), PartPose.ZERO);
        } else {
            head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 32)
                    .addBox(-2.0F, -8.5F, -8.5F, 4, 3, 3), PartPose.ZERO);
            head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(16, 32)
                    .addBox(-3.5F, -10.5F, -6.0F, 7, 2, 6), PartPose.ZERO);
            head.addOrReplaceChild("ears", CubeListBuilder.create().texOffs(44, 32)
                    .addBox(-3.5F, -12.5F, -2.5F, 2, 2, 1)
                    .texOffs(44, 32).mirror().addBox(1.5F, -12.5F, -2.5F, 2, 2, 1), PartPose.ZERO);
        }
        root.getChild("body").addOrReplaceChild("pelt", CubeListBuilder.create().texOffs(0, 42)
                .addBox(-4.5F, -0.5F, 3.2F, 9, 13, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WOLF, () -> create(false));
        event.registerLayerDefinition(BEAR, () -> create(true));
    }

    private static IClientItemExtensions extensions(ModelLayerLocation layer) {
        return new IClientItemExtensions() {
            private HumanoidModel<LivingEntity> model;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (slot == EquipmentSlot.LEGS) return original;
                if (model == null) {
                    ModelPart part = Minecraft.getInstance().getEntityModels().bakeLayer(layer);
                    model = new HumanoidModel<>(part);
                }
                return model;
            }
        };
    }

    @SubscribeEvent
    public static void onExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(extensions(WOLF), ModArmor.WOLF_FUR_HELMET.get(), ModArmor.WOLF_FUR_CHESTPLATE.get(),
                ModArmor.WOLF_FUR_LEGGINGS.get(), ModArmor.WOLF_FUR_BOOTS.get());
        event.registerItem(extensions(BEAR), ModArmor.BEAR_FUR_HELMET.get(), ModArmor.BEAR_FUR_CHESTPLATE.get(),
                ModArmor.BEAR_FUR_LEGGINGS.get(), ModArmor.BEAR_FUR_BOOTS.get());
    }
}
