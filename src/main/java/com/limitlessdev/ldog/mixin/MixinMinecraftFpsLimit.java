package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.FpsReducerHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lowers the frame-rate cap the game loop uses while the FPS reducer wants
 * one (window unfocused, or player AFK).
 *
 * <p>{@code Minecraft.runGameLoop} ends every frame with
 * {@code if (isFramerateLimitBelowMax()) Display.sync(getLimitFramerate())},
 * and both of those read {@code getLimitFramerate()}. Injecting at its RETURN
 * is therefore the single point that controls the real per-frame cap. The
 * previous implementation called {@code Display.sync} from the 20 Hz client
 * tick, which only stalled the tick and left the render rate alone.
 *
 * <p>Lives in {@code mixins.ldog.vanilla.json}: {@code Minecraft} is loaded
 * during FML bootstrap, before the late configs register (same reason as
 * {@link MixinMinecraftBorderless}).
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftFpsLimit {

    @Inject(method = "getLimitFramerate", at = @At("RETURN"), cancellable = true)
    private void ldog$reduceFramerateLimit(CallbackInfoReturnable<Integer> cir) {
        if (!LDOGConfig.enableFpsReducer) return;
        int vanilla = cir.getReturnValueI();
        int limited = FpsReducerHandler.applyLimit(vanilla);
        if (limited != vanilla) cir.setReturnValue(limited);
    }
}
