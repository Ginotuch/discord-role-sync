package net.gabbage.discordRoleSync.commands.discord;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList; // Added import
import java.util.Collections;
import java.util.List;

public class ReloadSubCommand implements IDiscordSubCommand {

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "discordrolesync.reload";
    }

    @Override
    public void execute(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        ConfigManager configManager = plugin.getConfigManager(); // Get fresh instance after reload potentially
        
        // Permission already checked by the main command dispatcher before calling this execute method.
        // However, if called directly, this check would be useful.
        // For this structure, the dispatcher handles it.

        // Send "Reloading..." message immediately on the main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            sender.sendMessage(ChatColor.YELLOW + "Reloading DiscordRoleSync plugin...");
        });

        try {
            plugin.reloadPlugin();
            // Schedule success message on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // ConfigManager might be new, so get messages from the potentially new instance
                sender.sendMessage(plugin.getConfigManager().getMessage("reload.success"));
            });
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error during plugin reload triggered by /discord reload command", e);
            // Schedule failure message on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(plugin.getConfigManager().getMessage("reload.failure"));
            });
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        return Collections.emptyList(); // No arguments for "reload"
    }
}
