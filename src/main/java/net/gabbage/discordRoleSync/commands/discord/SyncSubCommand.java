package net.gabbage.discordRoleSync.commands.discord;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SyncSubCommand implements IDiscordSubCommand {

    @Override
    public String getName() {
        return "sync";
    }

    @Override
    public String getPermission() {
        return "discordrolesync.sync";
    }

    @Override
    public void execute(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        ConfigManager configManager = plugin.getConfigManager();

        if (args.length < 1) {
            sender.sendMessage(configManager.getMessage("sync.usage"));
            return;
        }

        String minecraftUsername = args[0];

        OfflinePlayer targetOfflinePlayer;
        Player onlinePlayer = Bukkit.getPlayerExact(minecraftUsername);

        if (onlinePlayer != null) {
            targetOfflinePlayer = onlinePlayer;
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineByName = Bukkit.getOfflinePlayer(minecraftUsername);
            targetOfflinePlayer = offlineByName;
        }

        if (targetOfflinePlayer == null || !targetOfflinePlayer.hasPlayedBefore()) {
            sender.sendMessage(configManager.getMessage("sync.player_not_found", "%mc_username%", minecraftUsername));
            return;
        }

        UUID targetUUID = targetOfflinePlayer.getUniqueId();
        if (targetUUID == null) {
            plugin.getLogger().warning("Sync: Could not retrieve UUID for player " + minecraftUsername + ". This might indicate an issue with player data or an offline-mode server where the name couldn't be resolved to a valid UUID.");
            sender.sendMessage(configManager.getMessage("sync.player_not_found", "%mc_username%", minecraftUsername));
            return;
        }
        String actualMcUsername = targetOfflinePlayer.getName() != null ? targetOfflinePlayer.getName() : minecraftUsername;
        LinkedPlayersManager linkedPlayersManager = plugin.getLinkedPlayersManager();

        if (!linkedPlayersManager.isMcAccountLinked(targetUUID)) {
            sender.sendMessage(configManager.getMessage("sync.player_not_linked", "%mc_username%", actualMcUsername));
            return;
        }

        String discordId = linkedPlayersManager.getDiscordId(targetUUID);
        if (discordId == null) { // Should not happen if isMcAccountLinked is true, but as a safeguard
            sender.sendMessage(configManager.getMessage("sync.error_missing_discord_id", "%mc_username%", actualMcUsername));
            return;
        }

        plugin.getRoleSyncService().synchronizeRoles(targetUUID, discordId);
        sender.sendMessage(configManager.getMessage("sync.success", "%mc_username%", actualMcUsername));
    }

    @Override
    public List<String> onTabComplete(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            // Optionally, you could also suggest offline linked players if desired, but that's more complex.
            // For now, just online players.
            return StringUtil.copyPartialMatches(args[0], playerNames, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
