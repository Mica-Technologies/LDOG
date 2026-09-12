package com.limitlessdev.ldog.command;

import com.limitlessdev.ldog.gui.GuiLDOGSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /ldog} — opens the LDOG settings screen from chat, plus a few vanilla
 * screens for convenience. Client-side only (registered through Forge's
 * {@code ClientCommandHandler}, so it never reaches a server).
 *
 * <ul>
 *   <li>{@code /ldog} — LDOG settings</li>
 *   <li>{@code /ldog menu} — the pause menu</li>
 *   <li>{@code /ldog options} — vanilla Options</li>
 *   <li>{@code /ldog packs} — vanilla Resource Packs</li>
 * </ul>
 *
 * <p>The screen is opened on the next client tick rather than inside the
 * command: when typed by a person the chat screen is still open while the
 * command runs and closes itself right after, which would immediately
 * replace whatever we displayed.
 */
public class CommandLdog extends CommandBase {

    private static final List<String> SUBCOMMANDS = Arrays.asList("menu", "options", "packs");

    @Override
    public String getName() {
        return "ldog";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ldog [menu|options|packs]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, net.minecraft.util.math.BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, SUBCOMMANDS) : super.getTabCompletions(server, sender, args, targetPos);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        Minecraft mc = Minecraft.getMinecraft();
        String which = args.length > 0 ? args[0].toLowerCase() : "";
        GuiScreen screen;
        switch (which) {
            case "":
                screen = new GuiLDOGSettings(null);
                break;
            case "menu":
                screen = new GuiIngameMenu();
                break;
            case "options":
                screen = new GuiOptions(null, mc.gameSettings);
                break;
            case "packs":
                screen = new GuiScreenResourcePacks(null);
                break;
            default:
                sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
                return;
        }
        mc.addScheduledTask(() -> mc.displayGuiScreen(screen));
    }
}
