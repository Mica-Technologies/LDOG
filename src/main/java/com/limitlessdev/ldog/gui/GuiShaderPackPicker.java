package com.limitlessdev.ldog.gui;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated screen for picking a shader pack from a scrollable list, replacing
 * the awkward cycle-button picker in the main settings GUI.
 *
 * <p>Behavior:
 *
 * <ul>
 *   <li>Lists every pack {@link ShaderPackManager} has discovered, plus
 *       "(none)" at the top.</li>
 *   <li>The active pack is highlighted yellow + carries a "(active)" tag.</li>
 *   <li>Clicking a row activates that pack immediately.</li>
 *   <li>Bottom row buttons: Rescan (force a fresh disk scan), Open Folder
 *       (opens {@code <.minecraft>/shaderpacks/} in the OS file browser),
 *       Done (return to settings).</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class GuiShaderPackPicker extends GuiScreen {

    private static final int BTN_DONE = 200;
    private static final int BTN_RESCAN = 201;
    private static final int BTN_OPEN_FOLDER = 202;

    private final GuiScreen parent;
    private PackList list;

    public GuiShaderPackPicker(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        // Refresh available packs every time the screen opens — cheap and
        // handles the case where the user dropped a file in while the GUI
        // was on a different screen.
        ShaderPackManager.INSTANCE.rescan();
        list = new PackList(this.mc, this.width, this.height, 32, this.height - 36);

        this.buttonList.clear();
        int btnW = 100;
        int btnH = 20;
        int spacing = 4;
        int totalW = btnW * 3 + spacing * 2;
        int row = this.height - 28;
        int x0 = (this.width - totalW) / 2;

        this.buttonList.add(new GuiButton(BTN_OPEN_FOLDER, x0, row, btnW, btnH, "Open Folder"));
        this.buttonList.add(new GuiButton(BTN_RESCAN, x0 + btnW + spacing, row, btnW, btnH, "Rescan"));
        this.buttonList.add(new GuiButton(BTN_DONE, x0 + (btnW + spacing) * 2, row, btnW, btnH,
            I18n.format("gui.done")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (list != null) list.drawScreen(mouseX, mouseY, partialTicks);

        String title = "§eShader Packs";
        this.drawCenteredString(this.fontRenderer, title, this.width / 2, 12, 0xFFFFFF);

        String hint = list != null && list.entries.size() <= 1
            ? "§7No packs found. Drop a pack into the shaderpacks/ folder, then Rescan."
            : "§7Click a pack to activate it. Active pack highlighted in yellow.";
        this.drawCenteredString(this.fontRenderer, hint, this.width / 2, 22, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_DONE:
                // Persist the active pack selection back to config so it
                // survives a restart.
                ConfigManager.sync(com.limitlessdev.ldog.Tags.MODID,
                    net.minecraftforge.common.config.Config.Type.INSTANCE);
                this.mc.displayGuiScreen(parent);
                break;
            case BTN_RESCAN:
                ShaderPackManager.INSTANCE.rescan();
                this.initGui();
                break;
            case BTN_OPEN_FOLDER:
                openShaderpacksFolder();
                break;
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        if (list != null) list.handleMouseInput();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void openShaderpacksFolder() {
        File mcDir = this.mc.gameDir;
        File packs = new File(mcDir, "shaderpacks");
        if (!packs.exists()) packs.mkdirs();
        try {
            // Desktop.open requires headed AWT; works on Windows/macOS/most
            // Linux desktops, but in deobf workspaces or odd setups it may
            // throw. Fail soft — log and skip.
            Desktop.getDesktop().open(packs);
        } catch (Throwable t) {
            LDOGMod.LOGGER.warn("LDOG: Could not open shaderpacks folder via Desktop: {}",
                t.toString());
        }
    }

    // -------------------------------------------------------------------
    // Inner list. Pulls live data from ShaderPackManager every layout pass
    // so a Rescan call refreshes without a screen tear-down.
    // -------------------------------------------------------------------

    private class PackList extends GuiListExtended {
        private final List<PackEntry> entries = new ArrayList<>();

        PackList(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, height, top, bottom, 18);
            this.setShowSelectionBox(false);
            for (String name : ShaderPackManager.INSTANCE.getPackNames()) {
                entries.add(new PackEntry(name));
            }
        }

        @Override public IGuiListEntry getListEntry(int index) { return entries.get(index); }
        @Override protected int getSize() { return entries.size(); }
        @Override protected int getScrollBarX() { return this.width / 2 + 160; }
        @Override public int getListWidth() { return 310; }
    }

    private class PackEntry implements GuiListExtended.IGuiListEntry {
        private final String name;
        private long lastClickTime;

        PackEntry(String name) { this.name = name; }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight,
                              int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            boolean active = name.equals(ShaderPackManager.INSTANCE.getActiveName());
            boolean hovering = mouseX >= x && mouseX <= x + listWidth
                && mouseY >= y && mouseY <= y + slotHeight;

            // Soft hover/active highlight using the existing GL state Gui
            // sets up before drawing list slots — no texture changes needed.
            int bg;
            if (active) bg = 0x60FFD800;       // yellow tint when active
            else if (hovering) bg = 0x40FFFFFF; // light hover
            else bg = 0;
            if (bg != 0) {
                drawRect(x - 2, y - 1, x + listWidth - 2, y + slotHeight - 1, bg);
            }

            String label = active ? "§e" + name + " §8(active)" : "§f" + name;
            GuiShaderPackPicker.this.fontRenderer.drawString(label, x + 4, y + 4, 0xFFFFFF);
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent,
                                    int relativeX, int relativeY) {
            // Light double-click guard so a held-down click doesn't reactivate
            // the same pack twice.
            long now = System.currentTimeMillis();
            if (now - lastClickTime < 150) return false;
            lastClickTime = now;

            LDOGConfig.shaderPackName = name;
            ShaderPackManager.INSTANCE.activate(name);
            // Persist immediately so a crash doesn't lose the selection.
            ConfigManager.sync(com.limitlessdev.ldog.Tags.MODID,
                net.minecraftforge.common.config.Config.Type.INSTANCE);
            return true;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent,
                                  int relativeX, int relativeY) { }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) { }
    }
}
