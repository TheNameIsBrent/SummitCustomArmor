package gg.summit.customarmor.command;

import gg.summit.customarmor.ArmorManager;
import gg.summit.customarmor.SummitCustomArmor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CustomArmorCommand implements CommandExecutor, TabCompleter {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;

    public CustomArmorCommand(SummitCustomArmor plugin) {
        this.plugin = plugin;
        this.armorManager = new ArmorManager(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give"   -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            default       -> sendUsage(sender);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // /ca give <piece> [player]
    // -------------------------------------------------------------------------
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customarmor.give")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /ca give <" + String.join("|", ArmorManager.PIECES) + "> [player]",
                    NamedTextColor.RED));
            return;
        }

        String piece = args[1].toLowerCase();
        if (!ArmorManager.PIECES.contains(piece)) {
            sender.sendMessage(Component.text("Unknown armor piece: " + piece, NamedTextColor.RED));
            return;
        }

        // Resolve target player
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text(
                    "Console must specify a player: /ca give <piece> <player>", NamedTextColor.RED));
            return;
        }

        ItemStack item = armorManager.buildItem(piece);
        if (item == null) {
            sender.sendMessage(Component.text(
                    "Failed to build item for '" + piece + "'. Check config.yml.", NamedTextColor.RED));
            return;
        }

        target.getInventory().addItem(item);
        target.sendMessage(Component.text("You received a custom armor piece.", NamedTextColor.GREEN));
        if (!sender.equals(target)) {
            sender.sendMessage(Component.text(
                    "Gave " + piece + " to " + target.getName() + ".", NamedTextColor.GREEN));
        }
    }

    // -------------------------------------------------------------------------
    // /ca reload
    // -------------------------------------------------------------------------
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("customarmor.reload")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        plugin.reloadConfig();
        sender.sendMessage(Component.text("SummitCustomArmor config reloaded.", NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("give", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            completions.addAll(ArmorManager.PIECES);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        String input = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        return completions;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /ca <give|reload>", NamedTextColor.YELLOW));
    }
}
