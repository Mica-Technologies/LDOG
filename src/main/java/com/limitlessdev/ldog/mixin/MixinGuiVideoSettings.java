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
 * Adds an "LDOG..." button to the Video Settings screen next to the
 * Done button, similar to how OptiFine integrates its settings.
 * Shrinks the Done button to make room.
 */
@Mixin(GuiVideoSettings.class)
public abstract class MixinGuiVideoSettings extends GuiScreen {

    private static final int BTN_LDOG = 211;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void ldog$addSettingsButton(CallbackInfo ci) {
        // Find the Done button (id 200) and shrink it to make room for LDOG
        for (GuiButton button : this.buttonList) {
            if (button.id == 200) {
                // Move Done to the right column: width/2 + 5, width 150
                button.x = this.width / 2 + 5;
                button.setWidth(150);
                break;
            }
        }

        // Add LDOG button in the left column, same row as Done
        this.buttonList.add(new GuiButton(BTN_LDOG,
            this.width / 2 - 155, this.height - 27,
            150, 20, "LDOG..."));
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
