package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.color.CustomColorHandler;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 6c: per-potion liquid color override.
 *
 * Honors `potion.<name>=0xRRGGBB` entries from OF-format color.properties.
 * The lookup uses the potion's registry-name path (e.g. "regeneration",
 * "swiftness", "fire_resistance") matched case-insensitively.
 *
 * Falls through to vanilla on no override, no config, or a missing registry
 * name (defensive — modded potions without a name).
 */
@Mixin(Potion.class)
public abstract class MixinPotionColor {

    @Inject(method = "getLiquidColor", at = @At("HEAD"), cancellable = true)
    private void ldog$overridePotionColor(CallbackInfoReturnable<Integer> cir) {
        if (!LDOGConfig.enableCustomColors) return;
        if (!CustomColorHandler.hasAnyPotionOverride()) return;

        ResourceLocation name = ((Potion) (Object) this).getRegistryName();
        if (name == null) return;

        int over = CustomColorHandler.getPotionColor(name.getPath());
        if (over != -1) cir.setReturnValue(over);
    }
}
