package net.gabbage.discordRoleSync.commands.discord;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.DiscordManager; // Added import
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class StatusSubCommand implements IDiscordSubCommand {

    @Override
    public String getName() {
        return "status"; // Internal name, not directly typed by user for default action
    }

    @Override
    public String getPermission() {
        return "discordrolesync.discord"; // Base permission for viewing status
    }

    @Override
    public void execute(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return;
        }
        Player player = (Player) sender;
        ConfigManager configManager = plugin.getConfigManager();
        LinkedPlayersManager linkedPlayersManager = plugin.getLinkedPlayersManager();

        boolean isLinked = linkedPlayersManager.isMcAccountLinked(player.getUniqueId());
        String discordInviteLink = configManager.getDiscordInviteLink();

        // Header
        player.sendMessage(configManager.getMessage("discord_command.header"));

        // Invite Link
        if (discordInviteLink != null && !discordInviteLink.isEmpty() && !"https://discord.gg/yourinvitecode".equals(discordInviteLink)) {
            String inviteLineTemplate = configManager.getMessage("discord_command.invite_line");
            TextComponent inviteLinkComponent = new TextComponent(discordInviteLink);
            inviteLinkComponent.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            inviteLinkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, discordInviteLink));
            inviteLinkComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to join: " + discordInviteLink)));

            String placeholder = "%discord_invite_link_component%";
            if (inviteLineTemplate.contains(placeholder)) {
                String[] parts = inviteLineTemplate.split(java.util.regex.Pattern.quote(placeholder), 2);
                TextComponent finalMessage = new TextComponent(TextComponent.fromLegacyText(parts[0]));
                finalMessage.addExtra(inviteLinkComponent);
                if (parts.length > 1) {
                    finalMessage.addExtra(new TextComponent(TextComponent.fromLegacyText(parts[1])));
                }
                player.spigot().sendMessage(finalMessage);
            } else {
                TextComponent inviteMessage = new TextComponent(TextComponent.fromLegacyText(ChatColor.GRAY + "Invite link: "));
                inviteMessage.addExtra(inviteLinkComponent);
                player.spigot().sendMessage(inviteMessage);
            }
        } else {
            player.sendMessage(configManager.getMessage("discord_command.no_invite_configured"));
        }

        player.sendMessage(" "); // Spacer

        // Link Status
        player.sendMessage(configManager.getMessage("discord_command.status_header"));
        if (isLinked) {
            String discordId = linkedPlayersManager.getDiscordId(player.getUniqueId());
            DiscordManager discordManager = plugin.getDiscordManager();

            if (discordManager != null && discordManager.getJda() != null) {
                discordManager.getJda().retrieveUserById(discordId).queue(
                    (net.dv8tion.jda.api.entities.User discordUser) -> { // Explicit type for clarity
                        player.sendMessage(configManager.getMessage("discord_command.status_linked_line",
                                "%discord_user_tag%", discordUser.getAsTag(),
                                "%discord_user_id%", discordId
                        ));
                    },
                    (Throwable failure) -> {
                        plugin.getLogger().warning("Failed to retrieve Discord user " + discordId + " for /discord status: " + failure.getMessage());
                        player.sendMessage(configManager.getMessage("discord_command.status_linked_error_retrieving_discord_user",
                                "%discord_user_id%", discordId
                        ));
                    }
                );
            } else {
                // JDA not available, just show the ID with error message
                player.sendMessage(configManager.getMessage("discord_command.status_linked_error_retrieving_discord_user",
                        "%discord_user_id%", discordId
                ));
            }
        } else {
            player.sendMessage(configManager.getMessage("discord_command.status_not_linked_line"));
        }

        player.sendMessage(" "); // Spacer

        // Next Steps
        player.sendMessage(configManager.getMessage("discord_command.action_header"));
        if (isLinked) {
            TextComponent unlinkMessage = new TextComponent(TextComponent.fromLegacyText(ChatColor.GRAY + "  To unlink your account, type "));
            TextComponent unlinkCommand = new TextComponent("/unlink");
            unlinkCommand.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            unlinkCommand.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/unlink"));
            unlinkCommand.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to type /unlink")));
            unlinkMessage.addExtra(unlinkCommand);
            player.spigot().sendMessage(unlinkMessage);
        } else {
            player.sendMessage(configManager.getMessage("discord_command.action_not_linked_line", "%your_mc_username%", player.getName()));
        }

        // Footer
        player.sendMessage(configManager.getMessage("discord_command.footer"));
    }

    @Override
    public List<String> onTabComplete(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        return Collections.emptyList(); // No arguments for the status display
    }
}
