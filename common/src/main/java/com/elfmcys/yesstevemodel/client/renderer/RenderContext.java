package com.elfmcys.yesstevemodel.client.renderer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-local carrier for the 1.21.9 submit-pipeline objects so that mod render code
 * deep in the geckolib3 callstack (layer renderers in particular) can reach the active
 * {@link SubmitNodeCollector} and {@link CameraRenderState} without rewriting every
 * intermediate signature. Set at the engine boundary (entity submit Mixin), cleared
 * in a finally.
 */
public final class RenderContext {
    private static final ThreadLocal<SubmitNodeCollector> COLLECTOR = new ThreadLocal<>();
    private static final ThreadLocal<CameraRenderState> CAMERA = new ThreadLocal<>();

    private RenderContext() {
    }

    public static void enter(SubmitNodeCollector collector, CameraRenderState cameraState) {
        COLLECTOR.set(collector);
        CAMERA.set(cameraState);
    }

    public static void exit() {
        COLLECTOR.remove();
        CAMERA.remove();
    }

    @Nullable
    public static SubmitNodeCollector collector() {
        return COLLECTOR.get();
    }

    @Nullable
    public static CameraRenderState camera() {
        return CAMERA.get();
    }
}
