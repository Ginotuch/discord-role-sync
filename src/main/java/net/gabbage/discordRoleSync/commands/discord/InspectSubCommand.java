package net.gabbage.discordRoleSync.commands.discord;

import net.dv8tion.jda.api.entities.User;
import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.DiscordManager;
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

public class InspectSubCommand implements IDiscordSubCommand {

    @Override
    public String getName() {
        return "inspect";
    }

    @Override
    public String getPermission() {
        return "discordrolesync.inspect";
    }

    @Override
    public void execute(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        ConfigManager configManager = plugin.getConfigManager();

        if (args.length == 0) {
            sender.sendMessage(configManager.getMessage("inspect.usage"));
            return;
        }

        String targetUsername = args[0];
        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(targetUsername); // Deprecated but standard way to get by name

        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline()) {
            sender.sendMessage(configManager.getMessage("inspect.player_not_found", "%mc_username%", targetUsername));
            return;
        }

        UUID targetUUID = targetOfflinePlayer.getUniqueId();
        String actualUsername = targetOfflinePlayer.getName() != null ? targetOfflinePlayer.getName() : targetUsername; // Use stored name if available

        List<String> messagesToSend = new ArrayList<>();
        messagesToSend.add(configManager.getMessage("inspect.inspecting_header", "%mc_username%", actualUsername));

        LinkedPlayersManager linkedPlayersManager = plugin.getLinkedPlayersManager();
        if (linkedPlayersManager.isMcAccountLinked(targetUUID)) {
            String discordId = linkedPlayersManager.getDiscordId(targetUUID);
            DiscordManager discordManager = plugin.getDiscordManager();

            if (discordManager != null && discordManager.getJda() != null) {
                discordManager.getJda().retrieveUserById(discordId).queue(
                    (User discordUser) -> {
                        sender.sendMessage(configManager.getMessage("inspect.linked_to",
                                "%mc_username%", actualUsername,
                                "%discord_user_tag%", discordUser.getAsTag(),
                                "%discord_user_id%", discordId
                        ));
                    },
                    (Throwable failure) -> {
                        plugin.getLogger().warning("Failed to retrieve Discord user " + discordId + " for inspect command: " + failure.getMessage());
                        sender.sendMessage(configManager.getMessage("inspect.error_retrieving_discord_user",
                                "%mc_username%", actualUsername,
                                "%discord_user_id%", discordId
                        ));
                    }
                );
            } else {
                // JDA not available, just show the ID
                sender.sendMessage(configManager.getMessage("inspect.error_retrieving_discord_user",
                        "%mc_username%", actualUsername,
                        "%discord_user_id%", discordId
                ));
            }
        } else {
            sender.sendMessage(configManager.getMessage("inspect.not_linked", "%mc_username%", actualUsername));
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
        return Collections.emptyList();
    }
}
