package com.limitlessdev.ldog.gui;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.Desktop;
import java.io.File;

/**
 * Standalone shader-pack picker screen. Kept around for direct invocation
 * (e.g. external mods linking against LDOG), even though the main flow now
 * uses the Shaders tab embedded inside {@link GuiLDOGSettings}.
 *
 * <p>The list rendering lives in {@link GuiShaderPackList} so both this
 * screen and the tab share one implementation.
 */
@SideOnly(Side.CLIENT)
public class GuiShaderPackPicker extends GuiScreen {

    private static final int BTN_DONE = 200;
    private static final int BTN_RESCAN = 201;
    private static final int BTN_OPEN_FOLDER = 202;

    private final GuiScreen parent;
    private GuiShaderPackList list;

    public GuiShaderPackPicker(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        // Refresh available packs every time the screen opens.
        ShaderPackManager.INSTANCE.rescan();
        list = new GuiShaderPackList(this.mc, this.width, this.height, 32, this.height - 36);

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

        this.drawCenteredString(this.fontRenderer, "§eShader Packs", this.width / 2, 12, 0xFFFFFF);

        String hint = list != null && list.discoveredCount() == 0
            ? "§7No packs found. Drop a pack into the shaderpacks/ folder, then Rescan."
            : "§7Click a pack to activate it. Active pack highlighted in yellow.";
        this.drawCenteredString(this.fontRenderer, hint, this.width / 2, 22, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_DONE:
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
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        // GuiListExtended's row click handling (PackEntry.mousePressed) is only
        // reached via GuiListExtended.mouseClicked, which vanilla input never
        // calls on its own — handleMouseInput above only covers scroll-wheel
        // and drag polling. Without this explicit forward, rows in this
        // standalone picker were unclickable. Mirrors GuiLDOGSettings.mouseClicked.
        if (list != null) list.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (list != null) list.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private void openShaderpacksFolder() {
        File mcDir = this.mc.gameDir;
        File packs = new File(mcDir, "shaderpacks");
        if (!packs.exists()) packs.mkdirs();
        try {
            Desktop.getDesktop().open(packs);
        } catch (Throwable t) {
            LDOGMod.LOGGER.warn("LDOG: Could not open shaderpacks folder via Desktop: {}",
                t.toString());
        }
    }
}
