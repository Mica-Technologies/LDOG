package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.gui.GuiLDOGSettings;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an "LDOG..." button to Minecraft's Options screen,
 * placed below the Resource Packs / Snooper row.
 */
@Mixin(GuiOptions.class)
public abstract class MixinGuiOptions extends GuiScreen {

    private static final int BTN_LDOG = 111;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void ldog$addSettingsButton(CallbackInfo ci) {
        this.buttonList.add(new GuiButton(BTN_LDOG,
            this.width / 2 - 155, this.height / 6 + 144 - 6,
            150, 20, "LDOG..."));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void ldog$handleSettingsButton(GuiButton button, CallbackInfo ci) {
        if (button.id == BTN_LDOG) {
            this.mc.displayGuiScreen(new GuiLDOGSettings(this));
            ci.cancel();
        }
    }
}
