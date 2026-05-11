package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.ReplacePlayerHandRenderEvent;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    public void ysm$onRenderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, float equippedProgress, float swingProgress, HumanoidArm humanoidArm, CallbackInfo ci) {
        if (ysm$dispatchHandRender(poseStack, submitNodeCollector, packedLight, humanoidArm)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMapHand", at = @At("HEAD"), cancellable = true)
    public void ysm$onRenderMapHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, HumanoidArm humanoidArm, CallbackInfo ci) {
        if (ysm$dispatchHandRender(poseStack, submitNodeCollector, packedLight, humanoidArm)) {
            ci.cancel();
        }
    }

    /**
     * 1.21.9 bridge: the engine now hands us a {@link SubmitNodeCollector} where the mod's
     * legacy hand renderer expects a {@link MultiBufferSource}. We pull the immediate buffer
     * from {@code Minecraft.renderBuffers()} so geckolib's {@code NativeModelRenderer} (which
     * writes directly to a {@code VertexConsumer}) keeps working, while stashing the active
     * collector into {@link RenderContext} for any submit-only APIs that fire deeper in the
     * layer chain. We end-batch on the immediate source after rendering so the geometry
     * actually flushes within the current frame.
     */
    private boolean ysm$dispatchHandRender(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, HumanoidArm humanoidArm) {
        if (this.minecraft.player == null) {
            return false;
        }
        MultiBufferSource.BufferSource bufferSource = this.minecraft.renderBuffers().bufferSource();
        RenderContext.enter(submitNodeCollector, null);
        try {
            boolean cancelled = ReplacePlayerHandRenderEvent.onRenderArm(this.minecraft.player, humanoidArm, poseStack, bufferSource, packedLight);
            if (cancelled) {
                bufferSource.endBatch();
            }
            return cancelled;
        } finally {
            RenderContext.exit();
        }
    }
}
