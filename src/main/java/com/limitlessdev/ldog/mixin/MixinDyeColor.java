package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.color.CustomColorHandler;
import net.minecraft.item.EnumDyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 6c: per-dye color override.
 *
 * Honors `dye.<name>=0xRRGGBB` entries from OF-format color.properties.
 * Lookup uses the enum name lowercased (e.g. EnumDyeColor.RED -> "red").
 *
 * Note that {@code EnumDyeColor} caches derived float[] color components in
 * its constructor — those are not retroactively patched, so callers that
 * sample the cached float channels see vanilla numbers. The integer color
 * returned by {@code getColorValue()} is the common path used by item
 * rendering, beacon beam color, sheep wool, etc., so most user-visible
 * surfaces honor the override.
 */
@Mixin(EnumDyeColor.class)
public abstract class MixinDyeColor {

    @Inject(method = "getColorValue", at = @At("HEAD"), cancellable = true)
    private void ldog$overrideDyeColor(CallbackInfoReturnable<Integer> cir) {
        if (!LDOGConfig.enableCustomColors) return;
        if (!CustomColorHandler.hasAnyDyeOverride()) return;

        String name = ((EnumDyeColor) (Object) this).name().toLowerCase();
        int over = CustomColorHandler.getDyeColor(name);
        if (over != -1) cir.setReturnValue(over);
    }
}
