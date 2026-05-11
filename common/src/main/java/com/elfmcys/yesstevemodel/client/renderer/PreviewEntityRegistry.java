package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Associates an in-flight {@link EntityRenderState} with the YSM animatable
 * (e.g. {@code PlayerCapability} for the local player, or a
 * {@code PlayerPreviewEntity} for a button thumbnail) that should drive its
 * deferred PIP render.
 *
 * <p>1.21.8 GUI rendering is deferred: {@code submitEntityRenderState} captures
 * a state and the actual draw runs later in
 * {@code GuiEntityRenderer.renderToTexture}. That deferred path receives only
 * the {@link EntityRenderState} - the originating entity is gone, so the
 * existing {@code ysm$lastRenderingEntity} fallback can't disambiguate between
 * multiple simultaneous previews (e.g. 10 model thumbnails on one screen).
 *
 * <p>The redirect in {@code GuiEntityRendererMixin} looks up the animatable
 * keyed on the state's identity and routes the render through
 * {@link CustomPlayerRenderer#renderEntity} directly, bypassing the vanilla
 * dispatcher and the {@code PlayerCapability.get(player)} lookup that
 * {@code ReplacePlayerRenderEvent} would otherwise perform.
 */
public final class PreviewEntityRegistry {

    private static final Map<EntityRenderState, CustomPlayerEntity> ANIMATABLES = new IdentityHashMap<>();

    private PreviewEntityRegistry() {
    }

    public static void register(EntityRenderState state, CustomPlayerEntity animatable) {
        ANIMATABLES.put(state, animatable);
    }

    public static CustomPlayerEntity get(EntityRenderState state) {
        return ANIMATABLES.get(state);
    }

    public static void remove(EntityRenderState state) {
        ANIMATABLES.remove(state);
    }
}
