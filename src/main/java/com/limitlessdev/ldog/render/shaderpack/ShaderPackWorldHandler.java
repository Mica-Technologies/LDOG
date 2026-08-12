package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.Tags;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Re-resolves the active shader pack's per-dimension {@code worldN/} folder
 * when the client enters a different dimension.
 *
 * <p>A pack's {@code worldDir} is decided once, at activation. Activate from
 * the main menu and it locks to the {@code world0} default; walk into the
 * Nether with a pack already running and it keeps executing the Overworld's
 * programs. Both are visible as "the pack looks wrong in this dimension".
 *
 * <p>{@code WorldEvent.Load} is the hook: on the client it fires from the
 * {@code WorldClient} constructor for both the initial join and every
 * dimension change. Note that {@code Minecraft.world} still points at the
 * world being LEFT at that moment, so the dimension is read off the event's
 * world and passed explicitly to
 * {@link ShaderPackManager#onDimensionChanged(int)} (which no-ops when the
 * folder wouldn't actually change, so the common case costs one string compare).
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
@SideOnly(Side.CLIENT)
public final class ShaderPackWorldHandler {

    private ShaderPackWorldHandler() {}

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        // Client worlds only — an integrated server fires this for its own
        // WorldServer instances on the same JVM.
        if (event.getWorld() == null || !event.getWorld().isRemote) return;
        if (event.getWorld().provider == null) return;
        ShaderPackManager.INSTANCE.onDimensionChanged(event.getWorld().provider.getDimension());
    }
}
