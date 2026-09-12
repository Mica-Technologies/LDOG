package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.shaderpack.TerrainVertexFormat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's VBO chunk renderer hard-codes the BLOCK vertex layout (stride 28,
 * four pointers). When {@link TerrainVertexFormat} has added a normal, the
 * stride is 32 and a normal pointer must be set up as well — otherwise every
 * chunk renders as interleaved garbage. The display-list path is unaffected:
 * it bakes vertices through the generic {@code VertexFormat} pointer setup.
 */
@Mixin(VboRenderList.class)
public abstract class MixinVboRenderList {

    @Inject(method = "setupArrayPointers", at = @At("HEAD"), cancellable = true)
    private void ldog$setupExtendedPointers(CallbackInfo ci) {
        if (!TerrainVertexFormat.isExtended()) return;
        int stride = TerrainVertexFormat.stride();
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, stride, 0);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, 12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, stride, 16);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, stride, 24);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        // GlStateManager only wraps the ByteBuffer overload of glNormalPointer;
        // a VBO needs the offset form.
        GL11.glNormalPointer(GL11.GL_BYTE, stride, (long) TerrainVertexFormat.normalOffset());
        GlStateManager.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        ci.cancel();
    }

    /**
     * RenderGlobal.renderBlockLayer disables the vertex/colour/texcoord client
     * arrays after the layer, but knows nothing about the normal array we
     * enabled. Leaving it on would make every later fixed-function draw read
     * normals from a stale pointer.
     */
    @Inject(method = "renderChunkLayer", at = @At("RETURN"))
    private void ldog$disableNormalArray(BlockRenderLayer layer, CallbackInfo ci) {
        if (!TerrainVertexFormat.isExtended()) return;
        GlStateManager.glDisableClientState(GL11.GL_NORMAL_ARRAY);
    }
}
