package com.elfmcys.yesstevemodel.access;

import com.elfmcys.yesstevemodel.mixin.client.EntityRenderDispatcherMixin;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Bridge between the 1.21.9 deferred submit pipeline (which only carries
 * {@link EntityRenderState}) and the mod's rendering hooks that need the
 * original {@link Entity}. Backed by {@link EntityRenderDispatcherMixin},
 * which records the entity-for-state mapping at extract time.
 */
public interface IEntityRenderDispatcher {

    /**
     * Returns the {@link Entity} whose {@code extractEntity} call produced the
     * given {@code state}, or {@code null} if no mapping is recorded (e.g. the
     * state came from outside the normal dispatcher flow, or its extract
     * happened on a previous frame and the entry has since been collected).
     *
     * <p>Important: extract for ALL entities in a frame happens before submit
     * for ANY of them (see {@code LevelRenderer.submitEntities}), so a single
     * "last rendering entity" field is unreliable — every state must look up
     * its own entity.
     */
    @Nullable
    Entity ysm$getEntityForState(EntityRenderState state);

}
