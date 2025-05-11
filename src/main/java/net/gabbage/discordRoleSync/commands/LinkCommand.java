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
            // Player has a pending request
            if (args.length == 0) {
                // Typed /link but code is missing
                player.sendMessage(configManager.getMessage("link.link_code_missing_ingame"));
                return true;
            }

            // Player provided a code, let's validate it
            String providedCode = args[0].toUpperCase(); // Match the case of generated code
            net.gabbage.discordRoleSync.util.LinkRequest request = linkManager.getPendingRequest(player.getUniqueId()); // getPendingRequest also handles expiry

            if (request == null) { // Should ideally not happen if hasPendingRequest was true and getPendingRequest handles expiry, but for safety
                player.sendMessage(configManager.getMessage("link.request_expired_ingame"));
                return true;
            }

            if (!request.getConfirmationCode().equals(providedCode)) {
                player.sendMessage(configManager.getMessage("link.link_code_invalid_ingame"));
                return true;
            }

            // Code is valid, proceed with confirmation
            if (linkManager.confirmLink(player.getUniqueId())) {
                player.sendMessage(configManager.getMessage("link.success_ingame", "%discord_user_tag%", request.getFullDiscordName()));
            } else {
                // This case might occur if the request expired just now (e.g. race condition)
                player.sendMessage(configManager.getMessage("link.link_code_invalid_ingame")); // Or request_expired_ingame
            }
        } else {
            // Player has NO pending request
            // Whether they typed /link or /link <CODE>, the message is the same: no pending request.
            player.sendMessage(configManager.getMessage("link.no_pending_request_ingame", "%your_mc_username%", player.getName()));
        }

        return true;
    }
}
