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

import java.util.ArrayList; // Added import
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

        // Prepare messages to be sent in order
        List<TextComponent> componentsToSend = new ArrayList<>();

        // Header
        componentsToSend.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.header"))));

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
                TextComponent finalInviteMessage = new TextComponent(TextComponent.fromLegacyText(parts[0]));
                finalInviteMessage.addExtra(inviteLinkComponent);
                if (parts.length > 1) {
                    finalInviteMessage.addExtra(new TextComponent(TextComponent.fromLegacyText(parts[1])));
                }
                componentsToSend.add(finalInviteMessage);
            } else {
                TextComponent fallbackInviteMessage = new TextComponent(TextComponent.fromLegacyText(ChatColor.GRAY + "Invite link: "));
                fallbackInviteMessage.addExtra(inviteLinkComponent);
                componentsToSend.add(fallbackInviteMessage);
            }
        } else {
            componentsToSend.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.no_invite_configured"))));
        }

        componentsToSend.add(new TextComponent(" ")); // Spacer
        componentsToSend.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.status_header"))));

        // Link Status - This part is now built and added to componentsToSend within the JDA callback or synchronously if not linked/JDA error
        if (isLinked) {
            String discordId = linkedPlayersManager.getDiscordId(player.getUniqueId());
            DiscordManager discordManager = plugin.getDiscordManager();

            if (discordManager != null && discordManager.getJda() != null) {
                discordManager.getJda().retrieveUserById(discordId).queue(
                    (net.dv8tion.jda.api.entities.User discordUser) -> {
                        componentsToSend.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.status_linked_line",
                                "%discord_user_tag%", discordUser.getAsTag(),
                                "%discord_user_id%", discordId
                        ))));
                        addRemainingAndSend(plugin, player, configManager, componentsToSend, isLinked);
                    },
                    (Throwable failure) -> {
                        plugin.getLogger().warning("Failed to retrieve Discord user " + discordId + " for /discord status: " + failure.getMessage());
                        componentsToSend.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.status_linked_error_retrieving_discord_user",
                                "%discord_user_id%", discordId
                        ))));
                        addRemainingAndSend(plugin, player, configManager, componentsToSend, isLinked);
                    }
                );
            } else {
                // JDA not available
                componentsToSend.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.status_linked_error_retrieving_discord_user",
                        "%discord_user_id%", discordId
                ))));
                addRemainingAndSend(plugin, player, configManager, componentsToSend, isLinked);
            }
        } else {
            // Not linked
            componentsToSend.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.status_not_linked_line"))));
            addRemainingAndSend(plugin, player, configManager, componentsToSend, isLinked);
        }
    }

    // Renamed method to avoid confusion and make its purpose clearer
    private void addRemainingAndSend(DiscordRoleSync plugin, Player player, ConfigManager configManager, List<TextComponent> components, boolean isLinked) {
        components.add(new TextComponent(" ")); // Spacer
        components.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.action_header"))));

        if (isLinked) {
            TextComponent unlinkMessage = new TextComponent(TextComponent.fromLegacyText(ChatColor.GRAY + "  To unlink your account, type "));
            TextComponent unlinkCommand = new TextComponent("/unlink");
            unlinkCommand.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            unlinkCommand.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/unlink"));
            unlinkCommand.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to type /unlink")));
            unlinkMessage.addExtra(unlinkCommand);
            components.add(unlinkMessage);
        } else {
            components.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.action_not_linked_line", "%your_mc_username%", player.getName()))));
        }

        components.add(new TextComponent(TextComponent.fromLegacyText(configManager.getMessage("discord_command.footer"))));

        // Send all collected messages on the main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (TextComponent component : components) {
                player.spigot().sendMessage(component);
            }
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        return Collections.emptyList(); // No arguments for the status display
    }
}
