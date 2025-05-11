package net.gabbage.discordRoleSync.commands;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.commands.discord.IDiscordSubCommand;
import net.gabbage.discordRoleSync.commands.discord.ReloadSubCommand;
import net.gabbage.discordRoleSync.commands.discord.StatusSubCommand;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DiscordCommand implements CommandExecutor, TabCompleter {

    private final DiscordRoleSync plugin;
    private final Map<String, IDiscordSubCommand> subCommands;
    private final IDiscordSubCommand defaultSubCommand;

    public DiscordCommand(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.subCommands = new HashMap<>();
        this.defaultSubCommand = new StatusSubCommand(); // Default action

        // Register subcommands
        registerSubCommand(new ReloadSubCommand());
        registerSubCommand(this.defaultSubCommand); // Register status as a fallback/default
    }

    private void registerSubCommand(IDiscordSubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ConfigManager configManager = plugin.getConfigManager();

        if (args.length > 0) {
            String subCommandName = args[0].toLowerCase();
            IDiscordSubCommand subCommand = subCommands.get(subCommandName);

            if (subCommand != null) {
                if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
                    sender.sendMessage(configManager.getMessage("discord_command.no_permission")); // Or a more generic no_perm message
                    return true;
                }
                // Remove the subcommand name from args before passing to execute
                String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                subCommand.execute(plugin, sender, subArgs);
                return true;
            }
        }

        // Default action if no subcommand or invalid subcommand is given
        if (defaultSubCommand.getPermission() != null && !sender.hasPermission(defaultSubCommand.getPermission())) {
            sender.sendMessage(configManager.getMessage("discord_command.no_permission"));
            return true;
        }
        defaultSubCommand.execute(plugin, sender, new String[0]); // Default command takes no args
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            // Suggest subcommands for which the player has permission
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], subCommands.keySet().stream()
                .filter(name -> !name.equals(defaultSubCommand.getName())) // Don't suggest "status" explicitly
                .filter(name -> {
                    IDiscordSubCommand sub = subCommands.get(name);
                    return sub.getPermission() == null || sender.hasPermission(sub.getPermission());
                })
                .collect(Collectors.toList()), completions);
            Collections.sort(completions);
            return completions;
        } else if (args.length > 1) {
            // Delegate to the specific subcommand's tab completer
            IDiscordSubCommand subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null && (subCommand.getPermission() == null || sender.hasPermission(subCommand.getPermission()))) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.onTabComplete(plugin, sender, subArgs);
            }
        }
        return Collections.emptyList();
    }
}
