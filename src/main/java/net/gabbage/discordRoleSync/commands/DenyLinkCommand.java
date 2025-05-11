package net.gabbage.discordRoleSync.commands;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.LinkManager;
import net.gabbage.discordRoleSync.util.LinkRequest;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DenyLinkCommand implements CommandExecutor {

    private final DiscordRoleSync plugin;

    public DenyLinkCommand(DiscordRoleSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        ConfigManager configManager = plugin.getConfigManager();

        if (!player.hasPermission("discordrolesync.denylink")) {
            player.sendMessage(configManager.getMessage("reload.no_permission")); // Assuming reload.no_permission is generic enough, or add a denylink.no_permission
            return true;
        }

        LinkManager linkManager = plugin.getLinkManager();

        if (linkManager.hasPendingRequest(player.getUniqueId())) {
            LinkRequest request = linkManager.getPendingRequest(player.getUniqueId()); // Get request to access Discord user ID
            if (request == null) { // Should not happen if hasPendingRequest is true, but for safety
                player.sendMessage(configManager.getMessage("denylink.no_pending_request_ingame"));
                return true;
            }

            linkManager.removePendingRequest(player.getUniqueId());
            player.sendMessage(configManager.getMessage("denylink.success_ingame", "%discord_user_tag%", request.getFullDiscordName()));

            // Notify the Discord user
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(request.getMinecraftPlayerUUID());
            String mcUsername = offlinePlayer.getName() != null ? offlinePlayer.getName() : player.getName(); // Fallback to current player name if offline name not found
            plugin.getDiscordManager().sendLinkDeniedDM(request.getDiscordUserId(), mcUsername);

        } else {
            player.sendMessage(configManager.getMessage("denylink.no_pending_request_ingame"));
        }
        return true;
    }
}
