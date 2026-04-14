package com.limitlessdev.ldog.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable list of settings rows for the LDOG settings screen.
 * Each row contains up to two buttons (left + right column) or a section header.
 */
@SideOnly(Side.CLIENT)
public class GuiLDOGSettingsList extends GuiListExtended {

    private final List<IGuiListEntry> entries = new ArrayList<>();

    public GuiLDOGSettingsList(Minecraft mc, int width, int height, int top, int bottom) {
        super(mc, width, height, top, bottom, 25);
        this.setShowSelectionBox(false);
    }

    public void addHeaderRow(String label) {
        entries.add(new HeaderEntry(label));
    }

    public void addButtonRow(GuiButton left, GuiButton right) {
        entries.add(new ButtonRowEntry(left, right));
    }

    @Override
    public IGuiListEntry getListEntry(int index) {
        return entries.get(index);
    }

    @Override
    protected int getSize() {
        return entries.size();
    }

    @Override
    protected int getScrollBarX() {
        return this.width / 2 + 160;
    }

    @Override
    public int getListWidth() {
        return 310;
    }

    // ---- Entry types ----

    /**
     * A section header row (non-interactive label with horizontal lines).
     */
    @SideOnly(Side.CLIENT)
    public class HeaderEntry implements IGuiListEntry {

        private final String label;

        public HeaderEntry(String label) {
            this.label = label;
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight,
                              int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            Minecraft mc = GuiLDOGSettingsList.this.mc;
            int centerX = GuiLDOGSettingsList.this.width / 2;
            int textWidth = mc.fontRenderer.getStringWidth(label);
            int left = centerX - 155;
            int right = centerX + 155;
            int textX = left + 2;
            int lineY = y + slotHeight / 2;

            // Line before text
            net.minecraft.client.gui.Gui.drawRect(left, lineY, textX - 2, lineY + 1, 0x66FFFFFF);
            // Label
            mc.fontRenderer.drawStringWithShadow("\u00a7e" + label, textX, lineY - 3, 0xFFFFFF);
            // Line after text
            net.minecraft.client.gui.Gui.drawRect(textX + textWidth + 2, lineY, right, lineY + 1, 0x66FFFFFF);
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent,
                                     int relativeX, int relativeY) {
            return false;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent,
                                   int relativeX, int relativeY) {
        }
    }

    /**
     * A row with one or two buttons side by side.
     */
    @SideOnly(Side.CLIENT)
    public class ButtonRowEntry implements IGuiListEntry {

        private final GuiButton leftButton;
        private final GuiButton rightButton;

        public ButtonRowEntry(GuiButton left, GuiButton right) {
            this.leftButton = left;
            this.rightButton = right;
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight,
                              int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            int centerX = GuiLDOGSettingsList.this.width / 2;

            if (leftButton != null) {
                leftButton.x = centerX - 155;
                leftButton.y = y;
                leftButton.drawButton(mc, mouseX, mouseY, partialTicks);
            }
            if (rightButton != null) {
                rightButton.x = centerX + 5;
                rightButton.y = y;
                rightButton.drawButton(mc, mouseX, mouseY, partialTicks);
            }
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent,
                                     int relativeX, int relativeY) {
            if (leftButton != null && leftButton.mousePressed(mc, mouseX, mouseY)) {
                leftButton.playPressSound(mc.getSoundHandler());
                return true;
            }
            if (rightButton != null && rightButton.mousePressed(mc, mouseX, mouseY)) {
                rightButton.playPressSound(mc.getSoundHandler());
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent,
                                   int relativeX, int relativeY) {
            if (leftButton != null) leftButton.mouseReleased(x, y);
            if (rightButton != null) rightButton.mouseReleased(x, y);
        }

        public GuiButton getLeftButton() { return leftButton; }
        public GuiButton getRightButton() { return rightButton; }
    }
}
