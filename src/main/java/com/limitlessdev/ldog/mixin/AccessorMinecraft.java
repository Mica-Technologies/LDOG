package com.limitlessdev.ldog.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Access to private Minecraft state needed by the borderless-fullscreen
 * handler: the internal {@code fullscreen} flag and {@code updateFramebufferSize}.
 *
 * displayWidth / displayHeight are already public, and refreshResources is
 * public too, so those don't need accessors here.
 */
@Mixin(Minecraft.class)
public interface AccessorMinecraft {

    @Accessor("fullscreen")
    boolean ldog$getFullscreen();

    @Accessor("fullscreen")
    void ldog$setFullscreen(boolean fullscreen);

    @Invoker("updateFramebufferSize")
    void ldog$updateFramebufferSize();
}
