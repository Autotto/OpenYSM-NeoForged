package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.client.renderer.EntityRenderStateBindings;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records the {@link EntityRenderState} ↔ {@link Entity} mapping at the universal sink
 * {@link EntityRenderer#createRenderState(Entity, float)} so the mod's submit-time hooks
 * can recover the original entity. See {@link EntityRenderStateBindings} for the rationale
 * (the two distinct caller paths — world rendering and GUI/PIP previews — both funnel
 * through this method).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
            method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN")
    )
    private <T extends Entity, S extends EntityRenderState> void ysm$bindStateToEntity(T entity, float partialTick, CallbackInfoReturnable<S> cir) {
        EntityRenderStateBindings.bind(cir.getReturnValue(), entity);
    }
}
