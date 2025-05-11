package net.gabbage.discordRoleSync.commands;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class UnlinkCommand implements CommandExecutor {

    private final DiscordRoleSync plugin;

    public UnlinkCommand(DiscordRoleSync plugin) {
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
        net.gabbage.discordRoleSync.storage.LinkedPlayersManager linkedPlayersManager = plugin.getLinkedPlayersManager();

        if (!linkedPlayersManager.isMcAccountLinked(player.getUniqueId())) {
            player.sendMessage(configManager.getMessage("unlink.not_linked_ingame"));
            return true;
        }

        if (linkManager.unlinkPlayer(player)) {
            player.sendMessage(configManager.getMessage("unlink.success_ingame"));
        } else {
            // This else block might be redundant if unlinkPlayer itself doesn't return false for other reasons
            // than "not linked", which is already checked. But as a fallback.
            player.sendMessage(configManager.getMessage("unlink.error_ingame"));
        }

        return true;
    }
}
