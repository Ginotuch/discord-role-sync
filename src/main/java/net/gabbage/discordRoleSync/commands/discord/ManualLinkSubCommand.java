package net.gabbage.discordRoleSync.commands.discord;

import net.dv8tion.jda.api.entities.User;
import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.DiscordManager; // Keep if used for JDA checks, though LinkManager handles JDA interactions
import net.gabbage.discordRoleSync.managers.LinkManager; // Import LinkManager
import net.gabbage.discordRoleSync.service.RoleSyncService; // Keep if directly used, though LinkManager centralizes this
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

public class ManualLinkSubCommand implements IDiscordSubCommand {

    @Override
    public String getName() {
        return "manuallink";
    }

    @Override
    public String getPermission() {
        return "discordrolesync.manuallink";
    }

    @Override
    public void execute(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        ConfigManager configManager = plugin.getConfigManager();

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("manuallink.usage"));
            return;
        }

        String minecraftUsername = args[0];
        String discordIdString = args[1];

        OfflinePlayer targetOfflinePlayer;
        Player onlinePlayer = Bukkit.getPlayerExact(minecraftUsername);

        if (onlinePlayer != null) {
            targetOfflinePlayer = onlinePlayer; // Player object is an OfflinePlayer
        } else {
            // Player is not online, use the deprecated method for offline lookup by name.
            // This is acknowledged as deprecated but is the Bukkit API's way to get an OfflinePlayer by name
            // for players who have played before but are currently offline.
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineByName = Bukkit.getOfflinePlayer(minecraftUsername);
            targetOfflinePlayer = offlineByName;
        }

        // Check if the player data is valid for linking.
        // targetOfflinePlayer could be null if Bukkit.getOfflinePlayer(name) somehow returns null (not typical).
        // !targetOfflinePlayer.hasPlayedBefore() is key: if a name has never joined, we can't link it.
        if (targetOfflinePlayer == null || !targetOfflinePlayer.hasPlayedBefore()) {
            sender.sendMessage(configManager.getMessage("manuallink.player_not_found", "%mc_username%", minecraftUsername));
            return;
        }

        UUID targetUUID = targetOfflinePlayer.getUniqueId();
        // Additional check for null UUID, though hasPlayedBefore() should generally ensure a non-null UUID
        // for players who have genuinely played on an online-mode server.
        if (targetUUID == null) {
            plugin.getLogger().warning("ManualLink: Could not retrieve UUID for player " + minecraftUsername + ". This might indicate an issue with player data or an offline-mode server where the name couldn't be resolved to a valid UUID.");
            sender.sendMessage(configManager.getMessage("manuallink.player_not_found", "%mc_username%", minecraftUsername));
            return;
        }
        String actualMcUsername = targetOfflinePlayer.getName() != null ? targetOfflinePlayer.getName() : minecraftUsername;

        try {
            Long.parseLong(discordIdString); // Validate if it's a number
        } catch (NumberFormatException e) {
            sender.sendMessage(configManager.getMessage("manuallink.invalid_discord_id"));
            return;
        }

        LinkedPlayersManager linkedPlayersManager = plugin.getLinkedPlayersManager();
        LinkManager linkManager = plugin.getLinkManager(); // Get LinkManager instance
        DiscordManager discordManager = plugin.getDiscordManager(); // For JDA check before retrieving user for messages

        // Check if Minecraft account is already linked
        if (linkedPlayersManager.isMcAccountLinked(targetUUID)) {
            String existingDiscordId = linkedPlayersManager.getDiscordId(targetUUID);
            if (discordManager != null && discordManager.getJda() != null) {
                discordManager.getJda().retrieveUserById(existingDiscordId).queue(
                    discordUser -> sender.sendMessage(configManager.getMessage("manuallink.mc_already_linked",
                            "%mc_username%", actualMcUsername,
                            "%discord_user_tag%", discordUser.getAsTag(),
                            "%discord_user_id%", existingDiscordId)),
                    failure -> sender.sendMessage(configManager.getMessage("manuallink.error_retrieving_existing_discord_user",
                            "%mc_username%", actualMcUsername,
                            "%discord_user_id%", existingDiscordId))
                );
            } else {
                 sender.sendMessage(configManager.getMessage("manuallink.error_retrieving_existing_discord_user",
                            "%mc_username%", actualMcUsername,
                            "%discord_user_id%", existingDiscordId));
            }
            return;
        }

        // Check if Discord ID is already linked
        if (linkedPlayersManager.isDiscordAccountLinked(discordIdString)) {
            UUID existingMcUUID = linkedPlayersManager.getMcUUID(discordIdString);
            OfflinePlayer existingOfflinePlayer = Bukkit.getOfflinePlayer(existingMcUUID);
            String existingMcUsername = existingOfflinePlayer.getName() != null ? existingOfflinePlayer.getName() : "an unknown Minecraft account";
            sender.sendMessage(configManager.getMessage("manuallink.discord_already_linked",
                    "%discord_user_id%", discordIdString,
                    "%mc_username%", existingMcUsername));
            return;
        }

        // Perform the link and all subsequent actions using LinkManager
        if (linkManager.performManualLink(targetOfflinePlayer, discordIdString)) {
            sender.sendMessage(configManager.getMessage("manuallink.success",
                    "%mc_username%", actualMcUsername,
                    "%discord_user_id%", discordIdString));
        } else {
            // performManualLink logs details, provide a generic error to sender
            sender.sendMessage(configManager.getMessage("manuallink.error_linking"));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[0], playerNames, new ArrayList<>());
        }
        // No suggestions for Discord ID (args.length == 2 or more)
        return Collections.emptyList();
    }
}
