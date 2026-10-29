package com.valoris.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.valoris.mobs.Bear;
import com.valoris.valoris2;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws the bear model 1.3 times its model size (about 2 blocks tall). */
public class BearRenderer extends MobRenderer<Bear, BearModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "textures/entity/bear.png");
    private static final float SCALE = 1.3F;

    public BearRenderer(EntityRendererProvider.Context context) {
        super(context, new BearModel(context.bakeLayer(BearModel.LAYER)), 1.2F);
    }

    @Override
    public ResourceLocation getTextureLocation(Bear bear) {
        return TEXTURE;
    }

    @Override
    protected void scale(Bear bear, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }
}
