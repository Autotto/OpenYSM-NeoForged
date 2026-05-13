package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.access.IEntityRenderDispatcher;
import com.elfmcys.yesstevemodel.client.renderer.CustomFishingHookRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomProjectileRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomVehicleRenderer;
import com.elfmcys.yesstevemodel.client.renderer.EntityRenderStateBindings;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.client.renderer.VehicleRenderer;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.9 entity render-dispatcher hook.
 *
 * <p>Pre-1.21.9 the mod intercepted {@code EntityRenderDispatcher.render(Entity, ...)} so it
 * could route projectiles / vehicles to the mod's custom renderers while keeping the
 * {@link Entity} reference in scope. In 1.21.9 rendering is split into <em>extract</em>
 * (state-population) and <em>submit</em> (deferred draw queueing) phases, and the entity
 * isn't carried through the submit pipeline — only an {@link EntityRenderState}.
 *
 * <p>We recover the entity-for-state mapping via {@link EntityRenderStateBindings},
 * which is populated by {@link EntityRendererMixin} at the universal sink
 * {@code EntityRenderer.createRenderState(Entity, float)} so that both world rendering
 * AND GUI/PIP previews (inventory, model picker) share the same lookup.
 *
 * <p>The mod's custom renderers still take {@code MultiBufferSource}/{@code packedLight} —
 * we obtain those by pulling the immediate {@code BufferSource} from
 * {@code Minecraft.renderBuffers()} and using {@code state.lightCoords} respectively,
 * while stashing the active {@link SubmitNodeCollector} / {@link CameraRenderState} into
 * {@link RenderContext} so that submit-only APIs deeper in the layer chain
 * (e.g. {@code ItemInHandRenderer.renderItem}) still resolve correctly.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements IEntityRenderDispatcher {

    @Override
    @Unique
    public Entity ysm$getEntityForState(EntityRenderState state) {
        return EntityRenderStateBindings.get(state);
    }

    /**
     * Wraps the {@code entityrenderer.submit(...)} call inside the dispatcher's
     * {@code submit(EntityRenderState, CameraRenderState, double, double, double, PoseStack,
     * SubmitNodeCollector)} method. Returning {@code false} skips the vanilla submit
     * (mod has fully handled the render); returning {@code true} lets vanilla proceed.
     */
    @WrapWithCondition(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
            )
    )
    private boolean ysm$wrapEntityRendererSubmit(
            EntityRenderer<?, ?> renderer,
            EntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        if (!YesSteveModel.isAvailable()) {
            return true;
        }
        Entity entity = EntityRenderStateBindings.get(state);
        if (entity == null) {
            return true;
        }

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        int packedLight = state.lightCoords;

        RenderContext.enter(collector, cameraState);
        try {
            if (entity instanceof Projectile projectile) {
                if (!GeneralConfig.DISABLE_PROJECTILE_MODEL.get()) {
                    boolean callOriginal;
                    if (projectile instanceof FishingHook fishingHook) {
                        callOriginal = CustomFishingHookRenderer.tryRenderCustomHook(fishingHook, state, partialTick, poseStack, bufferSource, packedLight);
                    } else {
                        callOriginal = CustomProjectileRenderer.renderProjectile(projectile, state, partialTick, poseStack, bufferSource, packedLight);
                    }
                    // bufferSource.endBatch();
                    return callOriginal;
                }
                return true;
            }
            if (!GeneralConfig.DISABLE_VEHICLE_MODEL.get()) {
                VehicleRenderer vehicleRenderer = RendererManager.getVehicleRenderer();
                vehicleRenderer.extractRenderState(entity, state, partialTick);
                ModelPreviewRenderer.renderVehicleModel(entity, poseStack, partialTick);
                boolean callOriginal = CustomVehicleRenderer.renderVehicle(entity, state, entity.getRotationVector().x, partialTick, poseStack, bufferSource, packedLight);
                // bufferSource.endBatch();
                return callOriginal;
            }
            return true;
        } finally {
            RenderContext.exit();
        }
    }
}
