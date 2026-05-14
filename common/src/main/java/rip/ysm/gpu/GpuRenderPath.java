package rip.ysm.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.mixin.client.GlBufferAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class GpuRenderPath {
    private static final float[] rootPoseScratch = new float[16];
    private static final float[] rootNormalScratch = new float[9];
    private static final float[] modelViewScratch = new float[16];
    private static final ConcurrentHashMap<Long, GpuMesh> meshMap = new ConcurrentHashMap<>();
    private static final AtomicLong ref = new AtomicLong(1);

    public static boolean tryRender(GeoModel model, PoseStack.Pose pose, float[] boneParams, int renderPartMask, int packedLight, int packedOverlay, float r, float g, float b, float a, Identifier textureLocation) {
        if (!GpuCapability.isAvailable()) return false;
        if (!BoneSkinShader.ensureCompiled()) return false;
        if (model.bakedBones == null || model.bakedBones.isEmpty()) return false;

        if (model.gpuMeshHandle == 0) {
            GpuMesh mesh = GpuMeshBuilder.build(model);
            if (mesh == null) return false;
            model.gpuMeshHandle = encodeMeshRef(mesh);
        }
        GpuMesh mesh = decodeMeshRef(model.gpuMeshHandle);
        if (mesh == null) return false;

        Minecraft mc = Minecraft.getInstance();
        Matrix4f rootPose = pose.pose();
        Matrix3f rootNormal = pose.normal();

        rootPose.get(rootPoseScratch);
        rootNormal.get(rootNormalScratch);
        RenderSystem.getModelViewMatrix().get(modelViewScratch);

        ByteBuffer boneBuf = mesh.perFrameBoneBuffer;
        boneBuf.clear();
        GeoModel.nComputeBoneMatrices(mesh.pointer, rootPoseScratch, rootNormalScratch, boneParams, packedLight, boneBuf);
        boneBuf.position(0);
        boneBuf.limit(mesh.boneCount * 144);

        int savedProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int savedFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GpuTextureView targetColorView;
        GpuTextureView targetDepthView;
        int targetWidth;
        int targetHeight;
        if (RenderSystem.outputColorTextureOverride != null && RenderSystem.outputDepthTextureOverride != null) {
            targetColorView = RenderSystem.outputColorTextureOverride;
            targetDepthView = RenderSystem.outputDepthTextureOverride;
            targetWidth = targetColorView.getWidth(0);
            targetHeight = targetColorView.getHeight(0);
        } else {
            RenderTarget mainTarget = mc.getMainRenderTarget();
            targetColorView = mainTarget.getColorTextureView();
            targetDepthView = mainTarget.getDepthTextureView();
            targetWidth = mainTarget.width;
            targetHeight = mainTarget.height;
        }
        if (!(targetColorView instanceof GlTextureView glTargetColorView)) return false;
        if (!(targetDepthView instanceof GlTextureView glTargetDepthView)) return false;
        DirectStateAccess dsa = ((GlDevice) RenderSystem.getDevice()).directStateAccess();
        int targetFbo = glTargetColorView.getFbo(dsa, glTargetDepthView.texture());
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);
        GlStateManager._viewport(0, 0, targetWidth, targetHeight);

        GlStateManager._disableCull();
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();

        AbstractTexture modelTex = mc.getTextureManager().getTexture(textureLocation);
        GpuTexture modelGpuTex = modelTex.getTexture();
        if (!(modelGpuTex instanceof GlTexture glModelTex)) return false;
        int modelTexId = glModelTex.glId();

        GpuTextureView lightView = mc.gameRenderer.lightTexture().getTextureView();
        if (!(lightView instanceof GlTextureView glLightView)) return false;
        int lightTexId = glLightView.texture().glId();

        GpuTextureView overlayView = mc.gameRenderer.overlayTexture().getTextureView();
        if (!(overlayView instanceof GlTextureView glOverlayView)) return false;
        int overlayTexId = glOverlayView.texture().glId();

        int nearestSamplerId = ((GlSampler) RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)).getId();
        int linearSamplerId = ((GlSampler) RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)).getId();

        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 2);
        GL33C.glBindSampler(2, linearSamplerId);
        GlStateManager._bindTexture(lightTexId);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 1);
        GL33C.glBindSampler(1, nearestSamplerId);
        GlStateManager._bindTexture(overlayTexId);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL33C.glBindSampler(0, nearestSamplerId);
        GlStateManager._bindTexture(modelTexId);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, mesh.boneSsbo);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, boneBuf);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BoneSkinShader.ssbo, mesh.boneSsbo);

        GlStateManager._glUseProgram(BoneSkinShader.program());
        if (BoneSkinShader.locModelView() >= 0) GL20.glUniformMatrix4fv(BoneSkinShader.locModelView(), false, modelViewScratch);
        if (BoneSkinShader.locColor() >= 0) GL20.glUniform4f(BoneSkinShader.locColor(), r, g, b, a);
        if (BoneSkinShader.locOverlay() >= 0) GL20.glUniform1i(BoneSkinShader.locOverlay(), packedOverlay);
        if (BoneSkinShader.locFogStart() >= 0) GL20.glUniform1f(BoneSkinShader.locFogStart(), 0.0f);
        if (BoneSkinShader.locFogEnd() >= 0) GL20.glUniform1f(BoneSkinShader.locFogEnd(), Float.MAX_VALUE);
        if (BoneSkinShader.locFogColor() >= 0) GL20.glUniform4f(BoneSkinShader.locFogColor(), 0.0f, 0.0f, 0.0f, 0.0f);
        if (BoneSkinShader.locFogShape() >= 0) GL20.glUniform1i(BoneSkinShader.locFogShape(), 0);

        GpuBufferSlice lightsSlice = RenderSystem.getShaderLights();
        if (lightsSlice != null && lightsSlice.buffer() instanceof GlBuffer glLightsBuf) {
            int lightsHandle = ((GlBufferAccessor) glLightsBuf).ysm$getHandle();
            GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER, BoneSkinShader.lightUboBinding, lightsHandle, lightsSlice.offset(), lightsSlice.length());
        }

        GpuBufferSlice projSlice = RenderSystem.getProjectionMatrixBuffer();
        if (projSlice != null && projSlice.buffer() instanceof GlBuffer glProjBuf) {
            int projHandle = ((GlBufferAccessor) glProjBuf).ysm$getHandle();
            GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER, BoneSkinShader.projUboBinding, projHandle, projSlice.offset(), projSlice.length());
        }

        GlStateManager._glBindVertexArray(mesh.vao);

        int offsetBytes = mesh.indexOffsetBytes(renderPartMask);
        int drawCount = mesh.indexDrawCount(renderPartMask);
        if (drawCount > 0) {
            GL11.glDrawElements(GL11.GL_TRIANGLES, drawCount, GL11.GL_UNSIGNED_INT, offsetBytes);
        }

        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BoneSkinShader.ssbo, 0);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BoneSkinShader.lightUboBinding, 0);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BoneSkinShader.projUboBinding, 0);
        GlStateManager._glUseProgram(savedProgram);

        GlStateManager._glBindVertexArray(0);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 2);
        GL33C.glBindSampler(2, 0);
        GlStateManager._bindTexture(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 1);
        GL33C.glBindSampler(1, 0);
        GlStateManager._bindTexture(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL33C.glBindSampler(0, 0);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);

        return true;
    }

    public static void disposeMesh(GeoModel model) {
        if (model.gpuMeshHandle == 0) return;
        GpuMesh mesh = meshMap.remove(model.gpuMeshHandle);
        if (mesh != null) mesh.dispose();
        model.gpuMeshHandle = 0;
    }

    private static long encodeMeshRef(GpuMesh mesh) {
        long ref = GpuRenderPath.ref.getAndIncrement();
        meshMap.put(ref, mesh);
        return ref;
    }

    private static GpuMesh decodeMeshRef(long ref) {
        return meshMap.get(ref);
    }
}
