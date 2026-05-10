package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.ReplacePlayerRenderEvent;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    private <E extends Entity, S extends EntityRenderState> void ysm$onRenderPlayerPre(E entity, double d, double e, double f, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, EntityRenderer<? super E, S> entityRenderer, CallbackInfo ci) {
        if (entity instanceof Player player) {
            S entityRenderState = entityRenderer.createRenderState(entity, partialTick);

            if (ReplacePlayerRenderEvent.onRenderPlayerPre(player, (PlayerRenderState) entityRenderState, partialTick, poseStack, bufferSource, packedLight)) {
                ci.cancel();
            }
        }
    }
}
