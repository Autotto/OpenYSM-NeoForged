package com.elfmcys.yesstevemodel.fabric.client;

import com.elfmcys.yesstevemodel.client.renderer.AnimationDebugOverlay;
import com.elfmcys.yesstevemodel.client.renderer.LoadingStateOverlay;
import com.elfmcys.yesstevemodel.client.renderer.ModelSyncStateOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import rip.ysm.api.client.HudOverlay;

public final class YesSteveModelFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudOverlay debugOverlay = AnimationDebugOverlay.createOverlay();
        // TODO 1.21.8 port: LoadingStateOverlay (paper doll) uses the broken 1.21.4-era
        // RenderSystem.getModelViewStack + bufferSource.endBatch immediate-mode path
        // (see ModelPreviewRenderer#renderPlayerOverlay). Under 1.21.8's deferred GUI
        // pipeline this corrupts depth/scissor/projection for the rest of the frame
        // and causes inventory face culling / missing UI text. Disabled for diagnosis.
        // HudOverlay loadingOverlay = new LoadingStateOverlay();
        HudOverlay syncOverlay = new ModelSyncStateOverlay();
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            float partial = tickDelta.getGameTimeDeltaPartialTick(false);
            debugOverlay.render(guiGraphics, mc.font, partial, w, h);
            // loadingOverlay.render(guiGraphics, mc.font, partial, w, h);
            syncOverlay.render(guiGraphics, mc.font, partial, w, h);
        });
    }
}
