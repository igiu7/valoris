package com.valoris.client;

import com.valoris.mobs.Bear;
import com.valoris.valoris2;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Brown bear (texture 128x64, textures/entity/bear.png). The "upper" part (body, head, front legs) turns around
 * the hind legs so the bear can rear up. Animations, all from code:
 * - walk: heavy four-beat gait, the body rolls and bobs, the head swings with the steps;
 * - idle: breathing, the head sways, ears twitch now and then, the tail wags;
 * - roar (when it spots a player): stands up on its hind legs, head thrown up and shaking, jaw wide open;
 * - attack: half rears and slams both front paws down one after the other, jaw open.
 */
public class BearModel extends HierarchicalModel<Bear> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "bear"), "main");

    private final ModelPart root;
    private final ModelPart upper;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart earLeft;
    private final ModelPart earRight;
    private final ModelPart tail;
    private final ModelPart legFrontLeft;
    private final ModelPart legFrontRight;
    private final ModelPart legBackLeft;
    private final ModelPart legBackRight;

    public BearModel(ModelPart root) {
        this.root = root;
        this.upper = root.getChild("upper");
        this.body = upper.getChild("body");
        this.tail = body.getChild("tail");
        this.head = upper.getChild("head");
        this.jaw = head.getChild("jaw");
        this.earLeft = head.getChild("ear_left");
        this.earRight = head.getChild("ear_right");
        this.legFrontLeft = upper.getChild("leg_front_left");
        this.legFrontRight = upper.getChild("leg_front_right");
        this.legBackLeft = root.getChild("leg_back_left");
        this.legBackRight = root.getChild("leg_back_right");
    }

    // model space: y grows downwards, the ground is at y = 24, the bear looks towards -z
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition upper = root.addOrReplaceChild("upper", CubeListBuilder.create(), PartPose.offset(0, 14, 11));

        PartDefinition body = upper.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7, -7, -13, 14, 14, 26)
                        // the shoulder hump of a brown bear
                        .texOffs(0, 40).addBox(-6, -10, -12, 12, 4, 10),
                PartPose.offset(0, -4, -9));
        body.addOrReplaceChild("tail", CubeListBuilder.create()
                        .texOffs(100, 24).addBox(-1.5F, -1.5F, 0, 3, 3, 3),
                PartPose.offsetAndRotation(0, -4, 13, -0.5F, 0, 0));

        PartDefinition head = upper.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(80, 0).addBox(-5, -5, -8, 10, 10, 8)
                        // upper muzzle
                        .texOffs(80, 18).addBox(-3, -1, -12, 6, 4, 4),
                PartPose.offset(0, -6, -22));
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(100, 40).addBox(-3, 0, -4, 6, 2, 4),
                PartPose.offset(0, 3, -8));
        head.addOrReplaceChild("ear_left", CubeListBuilder.create()
                        .texOffs(100, 18).addBox(-1.5F, -3, -0.5F, 3, 3, 1),
                PartPose.offset(3.5F, -5, -3));
        head.addOrReplaceChild("ear_right", CubeListBuilder.create()
                        .texOffs(108, 18).addBox(-1.5F, -3, -0.5F, 3, 3, 1),
                PartPose.offset(-3.5F, -5, -3));

        CubeListBuilder leg = CubeListBuilder.create().texOffs(44, 40).addBox(-3, 0, -3, 6, 10, 6);
        upper.addOrReplaceChild("leg_front_left", leg, PartPose.offset(4.5F, 0, -17));
        upper.addOrReplaceChild("leg_front_right", leg, PartPose.offset(-4.5F, 0, -17));
        root.addOrReplaceChild("leg_back_left", leg, PartPose.offset(4.5F, 14, 11));
        root.addOrReplaceChild("leg_back_right", leg, PartPose.offset(-4.5F, 14, 11));

        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Bear bear, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float partial = ageInTicks - bear.tickCount;
        float attack = bear.attackProgress(partial);
        float roar = bear.roarProgress(partial);

        // ---- walk: diagonal legs together, heavy roll and bob of the whole body
        float speed = 0.55F;
        float amount = Math.min(limbSwingAmount, 1.0F);
        float step = limbSwing * speed;
        float a = Mth.cos(step) * 0.9F * amount;
        float b = Mth.cos(step + Mth.PI) * 0.9F * amount;
        legFrontLeft.xRot = a;
        legBackRight.xRot = a;
        legFrontRight.xRot = b;
        legBackLeft.xRot = b;
        upper.zRot = Mth.cos(step) * 0.05F * amount;
        upper.y -= Math.abs(Mth.sin(step)) * 0.8F * amount;
        head.yRot = Mth.sin(step) * 0.12F * amount;
        head.xRot = Mth.cos(step * 2.0F) * 0.05F * amount;

        // ---- idle: breathing, head sway, ears, tail
        float breath = Mth.sin(ageInTicks * 0.07F);
        body.xScale = 1.0F + breath * 0.015F;
        body.yScale = 1.0F + breath * 0.02F;
        head.y += breath * 0.2F;
        head.zRot = Mth.sin(ageInTicks * 0.03F) * 0.04F * (1.0F - amount);
        tail.yRot = Mth.sin(ageInTicks * 0.2F) * 0.25F;
        int twitch = (bear.tickCount + bear.getId() * 37) % 90;
        if (twitch < 6) {
            float t = Mth.sin((twitch + partial) / 6.0F * Mth.PI);
            earLeft.zRot = 0.35F * t;
            earRight.zRot = -0.15F * t;
        }

        // looking around
        head.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.8F;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.8F;

        // ---- roar: up on the hind legs
        if (roar > 0) {
            // rise fast, hold, come down at the end
            float up = roar < 0.25F ? roar / 0.25F : roar > 0.8F ? (1.0F - roar) / 0.2F : 1.0F;
            up = up * up * (3 - 2 * up); // smoothstep
            upper.xRot -= 1.05F * up;
            head.xRot += 0.55F * up;                     // keep looking forward and a little up
            head.zRot += Mth.sin(ageInTicks * 1.4F) * 0.18F * up;
            jaw.xRot = 0.85F * up;
            earLeft.zRot += 0.3F * up;                   // ears laid back
            earRight.zRot -= 0.3F * up;
            // front paws hang down-forward in front of the chest and paw at the air
            legFrontLeft.xRot = 0.4F * up + Mth.sin(ageInTicks * 0.5F) * 0.3F * up;
            legFrontRight.xRot = 0.4F * up + Mth.sin(ageInTicks * 0.5F + Mth.PI) * 0.3F * up;
            legFrontLeft.zRot = -0.15F * up;
            legFrontRight.zRot = 0.15F * up;
        }

        // ---- attack: half rear, then both paws slam down one after the other
        if (attack > 0) {
            float rear = Mth.sin(attack * Mth.PI);
            upper.xRot -= 0.5F * rear;
            head.xRot += 0.3F * rear;
            jaw.xRot = Math.max(jaw.xRot, 0.6F * rear);
            float left = swipe(attack);
            float right = swipe(Math.max(0, attack - 0.15F) / 0.85F);
            legFrontLeft.xRot = -2.0F * left;
            legFrontRight.xRot = -2.0F * right;
        }
    }

    /** Paw goes up (0 -> 1) during the first 60% and slams down fast after. */
    private static float swipe(float t) {
        if (t <= 0) return 0;
        if (t < 0.6F) return Mth.sin(t / 0.6F * Mth.HALF_PI);
        return Mth.cos((t - 0.6F) / 0.4F * Mth.HALF_PI);
    }
}
