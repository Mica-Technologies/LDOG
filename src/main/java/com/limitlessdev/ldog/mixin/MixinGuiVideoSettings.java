package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.gui.GuiLDOGSettings;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an "LDOG..." button to the Video Settings screen next to the
 * Done button, similar to how OptiFine integrates its settings.
 * Resizes the Done button to the right column and places LDOG in the left.
 */
@Mixin(GuiVideoSettings.class)
public abstract class MixinGuiVideoSettings extends GuiScreen {

    @Unique
    private static final int LDOG$BTN_ID = 211;

    @Unique
    private GuiButton ldog$settingsButton;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void ldog$addSettingsButton(CallbackInfo ci) {
        // Shrink and move the Done button (id 200) to the right column
        for (GuiButton btn : this.buttonList) {
            if (btn.id == 200) {
                btn.x = this.width / 2 + 5;
                btn.width = 150;
                break;
            }
        }

        // Add LDOG button in the left column at the same Y as Done
        ldog$settingsButton = new GuiButton(LDOG$BTN_ID,
            this.width / 2 - 155, this.height - 27,
            150, 20, "LDOG...");
        this.buttonList.add(ldog$settingsButton);
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void ldog$handleSettingsButton(GuiButton button, CallbackInfo ci) {
        if (button.id == LDOG$BTN_ID) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new GuiLDOGSettings(this));
            ci.cancel();
        }
    }
}
