package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.client.renderer.CustomPlayerRenderer;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.PreviewEntityRegistry;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In 1.21.9 entity rendering for inventory previews is deferred through
 * {@link GuiEntityRenderer#renderToTexture} (Picture-in-Picture) using the submit pipeline.
 * Two things go wrong for YSM without this mixin:
 *
 * <ol>
 *   <li>The PIP path uses an orthographic projection. {@code NativeModelRenderer}
 *       consults {@code gameRenderer.getProjectionMatrix(fov)} (the world
 *       perspective) for CPU back-face culling, which is the wrong matrix here.
 *       We flag preview mode for the lifetime of {@code renderToTexture} so the
 *       CPU cull is skipped.</li>
 *   <li>The state-based dispatch ({@code dispatcher.submit(state, ...)}) cannot
 *       carry per-submission entity context, so 10 simultaneously-submitted
 *       {@code ModelButton} thumbnails would collapse onto whichever
 *       {@code ysm$lastRenderingEntity} happened to win the race. We instead
 *       look the originating animatable up in {@link PreviewEntityRegistry}
 *       (keyed on the state identity that we ourselves submitted) and dispatch
 *       to {@link CustomPlayerRenderer#renderEntity} directly.</li>
 * </ol>
 */
@Mixin(GuiEntityRenderer.class)
public class GuiEntityRendererMixin {

    @Inject(method = "renderToTexture", at = @At("HEAD"))
    private void ysm$enterPreviewMode(GuiEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(true);
    }

    @Inject(method = "renderToTexture", at = @At("RETURN"))
    private void ysm$exitPreviewMode(GuiEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(false);
    }

    @Redirect(
            method = "renderToTexture*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
            )
    )
    private void ysm$redirectPreviewDispatch(
            EntityRenderDispatcher dispatcher,
            EntityRenderState state,
            CameraRenderState cameraState,
            double x, double y, double z,
            PoseStack poseStack,
            SubmitNodeCollector collector
    ) {
        PreviewEntityRegistry.Entry entry = PreviewEntityRegistry.getEntry(state);
        if (entry != null && entry.animatable() != null && state instanceof AvatarRenderState playerState) {
            try {
                MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                RenderContext.enter(collector, cameraState);
                try {
                    // Scenery (ground / bed / vehicle) is rendered first so the player
                    // overlays it with the correct depth ordering, matching the pre-1.21.6
                    // call order in renderEntityPreview.
                    if (entry.beforeEntity() != null) {
                        entry.beforeEntity().render(poseStack, bufferSource, state.lightCoords);
                    }
                    CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
                    // Use the live frame partialTick so processAnimation interpolates
                    // walkAnimation / modelData.lerpedAge per frame instead of snapping
                    // to tick boundaries.
                    float framePartialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                    renderer.renderEntity(entry.animatable(), playerState, 0.0f, framePartialTick, poseStack, bufferSource, state.lightCoords);
                    if (entry.afterEntity() != null) {
                        entry.afterEntity().render(poseStack, bufferSource, state.lightCoords);
                    }
                    // bufferSource.endBatch();
                } finally {
                    RenderContext.exit();
                }
            } finally {
                PreviewEntityRegistry.remove(state);
            }
            return;
        }
        dispatcher.submit(state, cameraState, x, y, z, poseStack, collector);
    }
}
