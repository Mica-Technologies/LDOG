package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Phase Tier B: per-HUD-element hide toggles for armor / hunger / air / boss
 * health bars. Implemented via Forge's {@link RenderGameOverlayEvent.Pre}
 * which is fired per element type and cancellable — cleaner than mixing into
 * vanilla's monolithic {@code GuiIngame.renderPlayerStats}, since vanilla
 * skips the element entirely when the event is cancelled.
 *
 * Sister to {@link com.limitlessdev.ldog.mixin.MixinGuiIngameHud}, which
 * handles hotbar / xp / horse-jump / item tooltip — those are not cleanly
 * keyed to a {@code ElementType} so they need surgical mixins. Armor/food/air
 * are first-class element types and avoid that complexity.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public final class HudHideHandler {

    private HudHideHandler() {}

    @SubscribeEvent
    public static void onPre(RenderGameOverlayEvent.Pre event) {
        switch (event.getType()) {
            case ARMOR:
                if (LDOGConfig.hideArmorBar) event.setCanceled(true);
                break;
            case FOOD:
                if (LDOGConfig.hideHungerBar) event.setCanceled(true);
                break;
            case AIR:
                if (LDOGConfig.hideAirBar) event.setCanceled(true);
                break;
            case BOSSHEALTH:
            case BOSSINFO:
                if (LDOGConfig.hideBossHealthBars) event.setCanceled(true);
                break;
            default:
                break;
        }
    }
}
