package net.gabbage.discordRoleSync.commands;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LinkCommand implements CommandExecutor {

    private final DiscordRoleSync plugin;

    public LinkCommand(DiscordRoleSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        net.gabbage.discordRoleSync.managers.LinkManager linkManager = plugin.getLinkManager();
        net.gabbage.discordRoleSync.managers.ConfigManager configManager = plugin.getConfigManager();

        if (linkManager.hasPendingRequest(player.getUniqueId())) {
            net.gabbage.discordRoleSync.util.LinkRequest request = linkManager.getPendingRequest(player.getUniqueId());
            if (request == null) { // Should ideally not happen if hasPendingRequest was true, but good for safety
                player.sendMessage(configManager.getMessage("link.request_expired_ingame"));
                return true;
            }

            if (linkManager.confirmLink(player.getUniqueId())) {
                player.sendMessage(configManager.getMessage("link.success_ingame", "%discord_user_tag%", request.getFullDiscordName()));
                // plugin.getDiscordManager().sendDirectMessage(request.getDiscordUserId(), configManager.getMessage("link.success_discord", "%mc_username%", player.getName())); // Removed DM to Discord user
            } else {
                // This case might occur if the request expired between hasPendingRequest and confirmLink, or other issues.
                player.sendMessage(configManager.getMessage("link.no_pending_request_ingame", "%your_mc_username%", player.getName()));
            }
        } else {
            player.sendMessage(configManager.getMessage("link.no_pending_request_ingame", "%your_mc_username%", player.getName()));
        }

        return true;
    }
}
