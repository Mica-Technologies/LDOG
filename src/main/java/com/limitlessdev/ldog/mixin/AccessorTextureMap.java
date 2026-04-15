package com.limitlessdev.ldog.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Accessor mixin for TextureMap's protected sprite maps.
 * Used by EmissiveTextureRegistry and CTMRegistry to enumerate
 * registered and uploaded sprites.
 */
@Mixin(TextureMap.class)
public interface AccessorTextureMap {

    @Accessor("mapRegisteredSprites")
    Map<String, TextureAtlasSprite> ldog$getMapRegisteredSprites();

    @Accessor("mapUploadedSprites")
    Map<String, TextureAtlasSprite> ldog$getMapUploadedSprites();
}
