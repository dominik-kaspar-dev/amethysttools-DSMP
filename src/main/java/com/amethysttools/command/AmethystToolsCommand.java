package com.amethysttools.command;

import com.amethysttools.AmethystToolService;
import com.amethysttools.AmethystToolsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AmethystToolsCommand implements CommandExecutor, TabCompleter {

    private final AmethystToolsPlugin plugin;
    private final AmethystToolService toolService;

    public AmethystToolsCommand(AmethystToolsPlugin plugin, AmethystToolService toolService) {
        this.plugin = plugin;
        this.toolService = toolService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(toolService.msg("usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("amethysttools.admin")) {
                sender.sendMessage(toolService.msg("no-permission"));
                return true;
            }
            plugin.reloadPluginConfig();
            sender.sendMessage(toolService.msg("reloaded"));
            return true;
        }

        if (sub.equals("give")) {
            if (!sender.hasPermission("amethysttools.admin")) {
                sender.sendMessage(toolService.msg("no-permission"));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("/at give <player> [item] [amount]");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(toolService.msg("invalid-player"));
                return true;
            }

            String requestedItem = args.length >= 3 ? args[2] : "amethyst_pickaxe";
            String itemKey = toolService.resolveItemKey(requestedItem);
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[3]));
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }

            if (itemKey == null) {
                String available = String.join(", ", toolService.getItemInputOptions());
                sender.sendMessage(toolService.msg("invalid-item").replace("%items%", available));
                return true;
            }

            target.getInventory().addItem(toolService.createItem(itemKey, amount));

            sender.sendMessage(toolService.msg("gave-item")
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%player%", target.getName()));
            target.sendMessage(toolService.msg("received-item")
                    .replace("%amount%", String.valueOf(amount)));
            return true;
        }

        sender.sendMessage(toolService.msg("usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (sender.hasPermission("amethysttools.admin")) {
            if (args.length == 1) {
                if ("give".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add("give");
                }
                if ("reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add("reload");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                String typed = args[1].toLowerCase(Locale.ROOT);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                        out.add(player.getName());
                    }
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                String typed = args[2].toLowerCase(Locale.ROOT);
                for (String itemOption : toolService.getItemInputOptions()) {
                    if (itemOption.startsWith(typed)) {
                        out.add(itemOption);
                    }
                }
            }
        }
        return out;
    }
}
