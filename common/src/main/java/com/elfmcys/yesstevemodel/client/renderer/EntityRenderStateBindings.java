package com.elfmcys.yesstevemodel.client.renderer;

import com.google.common.collect.MapMaker;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Identity-keyed binding from {@link EntityRenderState} back to the {@link Entity} that
 * produced it.
 *
 * <p>In 1.21.9 the entity render flow is split into <em>extract</em> (build a state) and
 * <em>submit</em> (deferred draw) phases. The submit phase carries only the state, not
 * the original entity. The mod needs the entity to make rendering decisions (player vs
 * vehicle vs projectile, which capability is bound, etc.), so we record a binding at the
 * point states are constructed.
 *
 * <p>States are populated through two distinct paths and we have to cover both:
 * <ol>
 *   <li>{@code LevelRenderer.submitEntities} extracts all visible entities via
 *       {@code dispatcher.extractEntity} → {@code entityRenderer.createRenderState(entity, partialTick)}.</li>
 *   <li>GUI / inventory previews (e.g. {@code InventoryScreen.renderEntityInInventory})
 *       call {@code entityRenderer.createRenderState(entity, partialTick)} <em>directly</em>,
 *       bypassing the dispatcher. The PIP {@code GuiEntityRenderer.renderToTexture} then
 *       submits that state through {@code dispatcher.submit(...)}.</li>
 * </ol>
 *
 * <p>We Mixin into the shared sink {@code EntityRenderer.createRenderState(Entity, float)} so
 * both paths populate the same map.
 *
 * <p>{@link MapMaker#weakKeys()} gives identity (==) key comparison and weak references,
 * so stale entries are reclaimed automatically once states fall out of scope at frame end.
 */
public final class EntityRenderStateBindings {

    private static final Map<EntityRenderState, Entity> BINDINGS =
            new MapMaker().weakKeys().<EntityRenderState, Entity>makeMap();

    private EntityRenderStateBindings() {
    }

    public static void bind(EntityRenderState state, Entity entity) {
        if (state != null && entity != null) {
            BINDINGS.put(state, entity);
        }
    }

    @Nullable
    public static Entity get(@Nullable EntityRenderState state) {
        return state == null ? null : BINDINGS.get(state);
    }
}
