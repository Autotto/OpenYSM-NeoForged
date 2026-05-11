package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.access.IEntityRenderDispatcher;
import com.elfmcys.yesstevemodel.client.renderer.CustomFishingHookRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomProjectileRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomVehicleRenderer;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.client.renderer.VehicleRenderer;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.google.common.collect.MapMaker;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 1.21.9 entity render-dispatcher hook.
 *
 * <p>The pre-1.21.9 mod intercepted {@code EntityRenderDispatcher.render(Entity, ...)} so it
 * could route projectiles / vehicles to the mod's custom renderers while keeping the
 * {@link Entity} reference in scope. In 1.21.9 rendering is split into <em>extract</em>
 * (state-population) and <em>submit</em> (deferred draw queueing) phases — and crucially
 * {@code LevelRenderer.submitEntities} extracts ALL entities first, then submits ALL of
 * their states. A single "last rendering entity" field is therefore stale by the time
 * any individual {@code submit} fires.
 *
 * <p>We instead maintain a {@link Map} keyed by the {@link EntityRenderState} instance
 * that {@link EntityRenderer#createRenderState()} produces each frame: populated at the
 * {@code RETURN} of {@code extractEntity}, consumed (and removed) when the corresponding
 * {@code entityrenderer.submit(...)} fires inside {@code dispatcher.submit}. Weak keys
 * let entries that never see a submit (filtered before draw) be reclaimed by GC.
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

    /**
     * Identity-keyed, weakly-referenced state → entity map. {@link MapMaker#weakKeys()}
     * compares with {@code ==}, which is the only correct semantic here since
     * {@link EntityRenderState} instances are created fresh per extract call.
     */
    @Unique
    private final Map<EntityRenderState, Entity> ysm$stateToEntity =
            new MapMaker().weakKeys().<EntityRenderState, Entity>makeMap();

    @Inject(
            method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN")
    )
    private <E extends Entity> void ysm$captureExtractedState(E entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (state != null) {
            this.ysm$stateToEntity.put(state, entity);
        }
    }

    @Override
    @Unique
    public Entity ysm$getEntityForState(EntityRenderState state) {
        return state == null ? null : this.ysm$stateToEntity.get(state);
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
        // Use get() not remove() — the same state is still about to be looked up by
        // PlayerRendererMixin (inside AvatarRenderer.submit) when we return true. Weak keys
        // let entries clear naturally when the state goes out of scope.
        Entity entity = this.ysm$stateToEntity.get(state);
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
                    bufferSource.endBatch();
                    return callOriginal;
                }
                return true;
            }
            if (!GeneralConfig.DISABLE_VEHICLE_MODEL.get()) {
                VehicleRenderer vehicleRenderer = RendererManager.getVehicleRenderer();
                vehicleRenderer.extractRenderState(entity, state, partialTick);
                ModelPreviewRenderer.renderVehicleModel(entity, poseStack, partialTick);
                boolean callOriginal = CustomVehicleRenderer.renderVehicle(entity, state, entity.getRotationVector().x, partialTick, poseStack, bufferSource, packedLight);
                bufferSource.endBatch();
                return callOriginal;
            }
            return true;
        } finally {
            RenderContext.exit();
        }
    }
}
