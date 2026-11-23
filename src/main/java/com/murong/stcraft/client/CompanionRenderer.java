package com.murong.stcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.murong.stcraft.entity.CompanionEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

public class CompanionRenderer extends HumanoidMobRenderer<CompanionEntity, PlayerModel<CompanionEntity>> {
    private static final ResourceLocation STEVE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final ResourceLocation ALEX =
            ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png");

    private final PlayerModel<CompanionEntity> wide;
    private final PlayerModel<CompanionEntity> slim;

    public CompanionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public void render(CompanionEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
        this.model = entity.isSlim() ? slim : wide;
        applyArmPoses(entity);
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    private void applyArmPoses(CompanionEntity e)
    {
        HumanoidModel.ArmPose main = HumanoidModel.ArmPose.EMPTY;
        HumanoidModel.ArmPose off = HumanoidModel.ArmPose.EMPTY;

        if (!e.getMainHandItem().isEmpty())
        {
            main = HumanoidModel.ArmPose.ITEM;
            if (e.isShooting())
            {
                switch (e.weaponStyle())
                {
                    case BOW -> main = HumanoidModel.ArmPose.BOW_AND_ARROW;
                    case CROSSBOW -> main = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                    case TRIDENT, THROWN -> main = HumanoidModel.ArmPose.THROW_SPEAR;
                    default -> { }
                }
            }
        }
        if (!e.getOffhandItem().isEmpty()) off = HumanoidModel.ArmPose.ITEM;
        if (main.isTwoHanded()) off = e.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;

        if (e.getMainArm() == HumanoidArm.RIGHT)
        {
            model.rightArmPose = main;
            model.leftArmPose = off;
        }
        else
        {
            model.rightArmPose = off;
            model.leftArmPose = main;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionEntity entity) {
        return SkinCache.get(entity.getSkinSource(), entity.isSlim() ? ALEX : STEVE);
    }
}