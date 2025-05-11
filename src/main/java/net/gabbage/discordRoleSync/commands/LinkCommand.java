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

        if (args.length == 0) {
            player.sendMessage(configManager.getMessage("link.link_code_missing_ingame"));
            return true;
        }

        String providedCode = args[0].toUpperCase(); // Match the case of generated code

        if (linkManager.hasPendingRequest(player.getUniqueId())) {
            net.gabbage.discordRoleSync.util.LinkRequest request = linkManager.getPendingRequest(player.getUniqueId());
            if (request == null) { // Should ideally not happen if hasPendingRequest was true, but good for safety
                player.sendMessage(configManager.getMessage("link.request_expired_ingame")); // Or link_code_invalid_ingame
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
                // This case might occur if the request expired just now, or other issues.
                player.sendMessage(configManager.getMessage("link.link_code_invalid_ingame")); // Using invalid code message as it's a general failure point now
            }
        } else {
            player.sendMessage(configManager.getMessage("link.no_pending_request_ingame", "%your_mc_username%", player.getName()));
        }

        return true;
    }
}
