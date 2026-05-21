package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Phase Tier A: keep advanced item tooltips (the F3+H breakdown — durability,
 * lore, NBT info) visible at all times when {@link LDOGConfig#advancedTooltipsAlways}
 * is on.
 *
 * Vanilla reads {@code Minecraft.gameSettings.advancedItemTooltips} each
 * call inside {@code ItemStack.getTooltip}. We flip the flag at every client
 * tick so subsequent tooltip builds see it. We also restore the user's saved
 * preference on disable / shutdown so we don't accidentally persist the
 * "always on" state into {@code options.txt}.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public final class AdvancedTooltipHandler {

    private static boolean savedFlag;
    private static boolean saved;
    private static boolean active;

    private AdvancedTooltipHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings == null) return;

        if (LDOGConfig.advancedTooltipsAlways) {
            if (!active) {
                savedFlag = mc.gameSettings.advancedItemTooltips;
                saved = true;
                active = true;
            }
            mc.gameSettings.advancedItemTooltips = true;
        } else if (active) {
            if (saved) {
                mc.gameSettings.advancedItemTooltips = savedFlag;
            }
            active = false;
            saved = false;
        }
    }
}
