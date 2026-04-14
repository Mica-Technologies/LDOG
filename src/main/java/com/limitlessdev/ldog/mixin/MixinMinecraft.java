package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.FpsReducerHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Minecraft's input handling to reset the AFK timer
 * whenever the player interacts with the game.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "runTickKeyboard", at = @At("HEAD"))
    private void ldog$onKeyboard(CallbackInfo ci) {
        FpsReducerHandler.onUserInput();
    }

    @Inject(method = "runTickMouse", at = @At("HEAD"))
    private void ldog$onMouse(CallbackInfo ci) {
        FpsReducerHandler.onUserInput();
    }
}
