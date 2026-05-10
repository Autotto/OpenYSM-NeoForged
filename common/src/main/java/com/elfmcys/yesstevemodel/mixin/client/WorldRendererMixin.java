package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.entity.EntityRenderCache;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public class WorldRendererMixin {

    @Inject(method = "method_62218", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V"))
    private static void renderLevel(Vector4f vector4f, CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            ModelPreviewRenderer.setFirstPersonMode(true);
            EntityRenderCache.tick(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        }
    }

    @Inject(method = {"method_62214"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/Camera;Lnet/minecraft/client/DeltaTracker;Ljava/util/List;)V")})
    private void renderLevelPost(FogParameters fogParameters, DeltaTracker deltaTracker, Camera camera, ProfilerFiller profilerFiller, Matrix4f matrix4f, Matrix4f matrix4f2, ResourceHandle resourceHandle, ResourceHandle resourceHandle2, ResourceHandle resourceHandle3, ResourceHandle resourceHandle4, boolean bl, Frustum frustum, ResourceHandle resourceHandle5, CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            EntityRenderCache.clear();
            ModelPreviewRenderer.setFirstPersonMode(false);
        }
    }
}