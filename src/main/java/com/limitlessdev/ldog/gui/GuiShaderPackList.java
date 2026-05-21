package com.limitlessdev.ldog.gui;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable list of shader packs, shared between the dedicated
 * {@link GuiShaderPackPicker} screen and the embedded Shaders tab inside
 * {@link GuiLDOGSettings}. Each row activates its pack on click; the active
 * pack is highlighted yellow.
 */
@SideOnly(Side.CLIENT)
public class GuiShaderPackList extends GuiListExtended {

    private final List<PackEntry> entries = new ArrayList<>();
    private final Minecraft mc;

    public GuiShaderPackList(Minecraft mc, int width, int height, int top, int bottom) {
        super(mc, width, height, top, bottom, 18);
        this.mc = mc;
        this.setShowSelectionBox(false);
        for (String name : ShaderPackManager.INSTANCE.getPackNames()) {
            entries.add(new PackEntry(name));
        }
    }

    @Override public IGuiListEntry getListEntry(int index) { return entries.get(index); }
    @Override protected int getSize() { return entries.size(); }
    @Override protected int getScrollBarX() { return this.width / 2 + 160; }
    @Override public int getListWidth() { return 310; }

    /** Number of packs found, not counting the synthetic "(none)" entry. */
    public int discoveredCount() {
        // The list always carries a "(none)" entry at the top so the user
        // can deactivate; everything else is a real pack.
        return Math.max(0, entries.size() - 1);
    }

    private class PackEntry implements IGuiListEntry {
        private final String name;
        private long lastClickTime;

        PackEntry(String name) { this.name = name; }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight,
                              int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            boolean active = name.equals(ShaderPackManager.INSTANCE.getActiveName());
            boolean hovering = mouseX >= x && mouseX <= x + listWidth
                && mouseY >= y && mouseY <= y + slotHeight;

            int bg;
            if (active)        bg = 0x60FFD800;  // yellow tint
            else if (hovering) bg = 0x40FFFFFF;  // light hover
            else               bg = 0;
            if (bg != 0) {
                Gui.drawRect(x - 2, y - 1, x + listWidth - 2, y + slotHeight - 1, bg);
            }

            String label = active ? "§e" + name + " §8(active)" : "§f" + name;
            mc.fontRenderer.drawString(label, x + 4, y + 4, 0xFFFFFF);
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent,
                                    int relativeX, int relativeY) {
            // Tiny rapid-click guard so a held button doesn't reactivate twice.
            long now = System.currentTimeMillis();
            if (now - lastClickTime < 150) return false;
            lastClickTime = now;

            LDOGConfig.shaderPackName = name;
            ShaderPackManager.INSTANCE.activate(name);
            // Persist immediately so a crash doesn't lose the choice.
            ConfigManager.sync(com.limitlessdev.ldog.Tags.MODID,
                net.minecraftforge.common.config.Config.Type.INSTANCE);
            return true;
        }

        @Override public void mouseReleased(int slotIndex, int x, int y, int mouseEvent,
                                            int relativeX, int relativeY) { }
        @Override public void updatePosition(int slotIndex, int x, int y, float partialTicks) { }
    }
}
