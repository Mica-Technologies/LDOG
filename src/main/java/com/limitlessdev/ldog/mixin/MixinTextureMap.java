package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.texture.ExtendedBorderHandler;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wires extended-border mipmap state into the atlas load lifecycle:
 * <ul>
 *   <li>{@code beginStitch} at the top of {@code loadTextureAtlas} freezes the
 *       decision for this pass (so a mid-stitch config toggle can't desync).</li>
 *   <li>{@code endStitch} at the tail of {@code finishLoading} clears the flag.</li>
 *   <li>The {@code uploadTextureMipmap} call inside {@code finishLoading}'s
 *       per-sprite loop is redirected so the atlas receives padded pixel data
 *       written at {@code (innerOrigin - border)} with inflated dimensions.</li>
 * </ul>
 *
 * <p>Note: animated sprite re-uploads go through {@code TextureAtlasSprite.updateAnimation},
 * which is not redirected here. Animated frames will refresh their inner region
 * correctly; the halo around them keeps its initial-frame color. Tolerable for v1.
 */
@Mixin(TextureMap.class)
public abstract class MixinTextureMap {

    @Shadow private int mipmapLevels;

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void ldog$beginStitch(net.minecraft.client.resources.IResourceManager rm, CallbackInfo ci) {
        ExtendedBorderHandler.beginStitch(this.mipmapLevels);
    }

    private static final String FINISH_LOADING_SIG =
        "finishLoading(Lnet/minecraft/client/renderer/texture/Stitcher;"
        + "Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;II)V";

    @Inject(method = FINISH_LOADING_SIG, at = @At("RETURN"), remap = false)
    private void ldog$endStitch(CallbackInfo ci) {
        ExtendedBorderHandler.endStitch();
    }

    // finishLoading is Forge-added and has no SRG name (remap = false on the enclosing
    // @Redirect), but the inner uploadTextureMipmap call IS MCP-mapped, so we force
    // remap = true on @At so its descriptor lands in the refmap.
    @Redirect(
        method = FINISH_LOADING_SIG,
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V",
            remap = true),
        remap = false)
    private void ldog$uploadWithBorder(int[][] data, int width, int height,
                                       int originX, int originY, boolean blur, boolean clamp) {
        if (!ExtendedBorderHandler.isActive()) {
            TextureUtil.uploadTextureMipmap(data, width, height, originX, originY, blur, clamp);
            return;
        }
        int border = ExtendedBorderHandler.getBorderSize();
        int mipmapLevels = ExtendedBorderHandler.getMipmapLevels();
        int[][] padded = ExtendedBorderHandler.padFrameData(data, width, height, border, mipmapLevels);
        TextureUtil.uploadTextureMipmap(padded,
            width + 2 * border, height + 2 * border,
            originX - border, originY - border,
            blur, clamp);
    }
}
