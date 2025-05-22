package net.gabbage.discordRoleSync.commands;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.LinkManager;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class UnlinkCommand implements CommandExecutor, TabCompleter {

    private final DiscordRoleSync plugin;
    private final LinkManager linkManager;
    private final ConfigManager configManager;
    private final LinkedPlayersManager linkedPlayersManager;
    private static final String UNLINK_OTHERS_PERMISSION = "discordrolesync.unlink.others";

    public UnlinkCommand(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.linkManager = plugin.getLinkManager();
        this.configManager = plugin.getConfigManager();
        this.linkedPlayersManager = plugin.getLinkedPlayersManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { // Self-unlink
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be run by a player to unlink themselves, or specify a player name.");
                return true;
            }
            Player player = (Player) sender;
            handleSelfUnlink(player);
            return true;
        }

        if (args.length == 1) { // Self-unlink by name OR unlink others
            String targetName = args[0];
            if (sender instanceof Player && sender.getName().equalsIgnoreCase(targetName)) {
                handleSelfUnlink((Player) sender);
            } else {
                handleUnlinkOther(sender, targetName);
            }
            return true;
        }

        // Incorrect usage
        sender.sendMessage(configManager.getMessage("unlink.usage_others")); // General usage message
        return false;
    }

    private void handleSelfUnlink(Player player) {
        if (!linkedPlayersManager.isMcAccountLinked(player.getUniqueId())) {
            player.sendMessage(configManager.getMessage("unlink.not_linked_ingame"));
            return;
        }

        if (linkManager.unlinkPlayer(player)) {
            player.sendMessage(configManager.getMessage("unlink.success_ingame"));
        } else {
            player.sendMessage(configManager.getMessage("unlink.error_ingame"));
        }
    }

    private void handleUnlinkOther(CommandSender sender, String targetName) {
        if (!sender.hasPermission(UNLINK_OTHERS_PERMISSION)) {
            sender.sendMessage(configManager.getMessage("unlink.no_permission_others"));
            return;
        }

        OfflinePlayer targetOfflinePlayer;
        Player onlinePlayer = Bukkit.getPlayerExact(targetName);

        if (onlinePlayer != null) {
            targetOfflinePlayer = onlinePlayer;
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineByName = Bukkit.getOfflinePlayer(targetName);
            targetOfflinePlayer = offlineByName;
        }

        if (targetOfflinePlayer == null || !targetOfflinePlayer.hasPlayedBefore()) {
            sender.sendMessage(configManager.getMessage("unlink.player_not_found", "%mc_username%", targetName));
            return;
        }

        UUID targetUUID = targetOfflinePlayer.getUniqueId();
        if (targetUUID == null) {
            plugin.getLogger().warning("Unlink: Could not retrieve UUID for player " + targetName + ". This might indicate an issue with player data or an offline-mode server where the name couldn't be resolved to a valid UUID.");
            sender.sendMessage(configManager.getMessage("unlink.player_not_found", "%mc_username%", targetName));
            return;
        }
        if (!linkedPlayersManager.isMcAccountLinked(targetUUID)) {
            sender.sendMessage(configManager.getMessage("unlink.other_not_linked_ingame", "%mc_username%", targetName));
            return;
        }

        if (linkManager.unlinkPlayer(targetOfflinePlayer)) {
            sender.sendMessage(configManager.getMessage("unlink.success_other_ingame", "%mc_username%", targetName));
        } else {
            // This might occur if unlinkPlayer(OfflinePlayer) has other failure conditions,
            // or if the player was unlinked between the check and the action.
            sender.sendMessage(configManager.getMessage("unlink.error_ingame")); // Generic error
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission(UNLINK_OTHERS_PERMISSION)) {
            String currentArg = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>(); // Return empty list for no suggestions
    }
}
