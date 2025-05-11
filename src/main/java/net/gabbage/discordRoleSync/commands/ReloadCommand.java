package net.gabbage.discordRoleSync.commands;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {

    private final DiscordRoleSync plugin;

    public ReloadCommand(DiscordRoleSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ConfigManager configManager = plugin.getConfigManager();

        if (!sender.hasPermission("discordrolesync.reload")) {
            sender.sendMessage(configManager.getMessage("reload.no_permission"));
            return true;
        }

        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Reloading DiscordRoleSync plugin...");
        try {
            plugin.reloadPlugin();
            sender.sendMessage(configManager.getMessage("reload.success"));
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error during plugin reload triggered by command", e);
            sender.sendMessage(configManager.getMessage("reload.failure"));
        }
        return true;
    }
}
