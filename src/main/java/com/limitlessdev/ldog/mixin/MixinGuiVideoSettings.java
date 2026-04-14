package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.gui.GuiLDOGSettings;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an "LDOG..." button to the Video Settings screen,
 * placed next to the Done button at the bottom -- similar to
 * how OptiFine integrates its settings into Video Settings.
 */
@Mixin(GuiVideoSettings.class)
public abstract class MixinGuiVideoSettings extends GuiScreen {

    private static final int BTN_LDOG = 211;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void ldog$addSettingsButton(CallbackInfo ci) {
        // Place the LDOG button to the left of the Done button
        // Done button is at: width/2 - 100, height - 27, width 200
        // LDOG button goes to the left: width/2 - 155, height - 27, width 50
        this.buttonList.add(new GuiButton(BTN_LDOG,
            this.width / 2 - 155, this.height - 27,
            50, 20, "LDOG"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void ldog$handleSettingsButton(GuiButton button, CallbackInfo ci) {
        if (button.id == BTN_LDOG) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new GuiLDOGSettings(this));
            ci.cancel();
        }
    }
}
