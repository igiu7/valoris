package com.valoris.client;

import com.valoris.registry.JapaneseArmory;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
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
 * The Shinobi chest piece (with a sword on the back and sleeves) of registry.JapaneseArmory:
 * body and arms of its own, nothing else (64x64 texture japanese_shinobi_chest_layer_1.png). From the 1.20.1 model
 * of Epic Knights: Japanese Armory.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class JapaneseArmorModels {
    private JapaneseArmorModels() {}

    public static final ModelLayerLocation SHINOBI = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "shinobi_chest"), "main");

    /** A humanoid mesh with empty head and legs; body and arms get the given cubes. */
    private static MeshDefinition emptyMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        return mesh;
    }

    private static LayerDefinition shinobi() {
        MeshDefinition mesh = emptyMesh();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition chest = body.addOrReplaceChild("chestplate", CubeListBuilder.create().texOffs(16, 37)
                .addBox(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition sword = chest.addOrReplaceChild("sword", CubeListBuilder.create(), PartPose.offset(-1.0F, -9.0F, 2.9F));
        sword.addOrReplaceChild("sword_r1", CubeListBuilder.create()
                        .texOffs(20, 39).addBox(-2.0F, -8.0F, -0.4F, 2.0F, 6.0F, 1.0F)
                        .texOffs(29, 43).addBox(-2.5F, -2.0F, -1.0F, 3.0F, 16.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.6109F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create()
                        .texOffs(0, 50).addBox(0.4F, 2.5F, -2.5F, 4.0F, 6.0F, 5.0F, new CubeDeformation(0.2F))
                        .texOffs(16, 40).addBox(0.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.41F)),
                PartPose.offset(-1.0F, 0.0F, 0.0F));
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create()
                        .texOffs(16, 40).mirror().addBox(-4.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.41F)).mirror(false)
                        .texOffs(0, 50).mirror().addBox(-4.5F, 2.5F, -2.5F, 4.0F, 6.0F, 5.0F, new CubeDeformation(0.2F)).mirror(false),
                PartPose.offset(1.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SHINOBI, JapaneseArmorModels::shinobi);
    }

    private static IClientItemExtensions extensions(ModelLayerLocation layer) {
        return new IClientItemExtensions() {
            private HumanoidModel<LivingEntity> model;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (model == null) model = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(layer));
                return model;
            }
        };
    }

    @SubscribeEvent
    public static void onExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(extensions(SHINOBI), JapaneseArmory.SHINOBI_CHESTPLATE.get());
    }
}
