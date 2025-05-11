package net.gabbage.discordRoleSync.commands;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DiscordCommand implements CommandExecutor {

    private final DiscordRoleSync plugin;

    public DiscordCommand(DiscordRoleSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        ConfigManager configManager = plugin.getConfigManager();

        if (!player.hasPermission("discordrolesync.discord")) {
            player.sendMessage(configManager.getMessage("discord_command.no_permission"));
            return true;
        }

        LinkedPlayersManager linkedPlayersManager = plugin.getLinkedPlayersManager();
        boolean isLinked = linkedPlayersManager.isMcAccountLinked(player.getUniqueId());
        String discordInviteLink = configManager.getDiscordInviteLink();

        // Header
        player.sendMessage(configManager.getMessage("discord_command.header"));

        // Invite Link
        if (discordInviteLink != null && !discordInviteLink.isEmpty() && !"https://discord.gg/yourinvitecode".equals(discordInviteLink)) {
            // The "invite_line" from messages.yml is expected to contain "%discord_invite_link_component%"
            // We will replace this placeholder with the actual clickable component.
            String inviteLineTemplate = configManager.getMessage("discord_command.invite_line");

            TextComponent inviteLinkComponent = new TextComponent(discordInviteLink); // The link itself is the text
            inviteLinkComponent.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            inviteLinkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, discordInviteLink));
            inviteLinkComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to join: " + discordInviteLink)));

            // Manually construct the line with the clickable component
            // This assumes the placeholder is at the end or can be simply appended.
            // A more robust way would be to split the message if placeholder is in the middle.
            // For "Invite link: %discord_invite_link_component%", we can split by the placeholder.
            String placeholder = "%discord_invite_link_component%";
            if (inviteLineTemplate.contains(placeholder)) {
                String[] parts = inviteLineTemplate.split(java.util.regex.Pattern.quote(placeholder), 2);
                TextComponent finalMessage = new TextComponent(TextComponent.fromLegacyText(parts[0])); // Text before placeholder
                finalMessage.addExtra(inviteLinkComponent);
                if (parts.length > 1) {
                    finalMessage.addExtra(new TextComponent(TextComponent.fromLegacyText(parts[1]))); // Text after placeholder
                }
                player.spigot().sendMessage(finalMessage);
            } else {
                // Fallback if placeholder is missing in the message string for some reason
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
            player.sendMessage(configManager.getMessage("discord_command.status_linked_line", "%discord_user_id%", discordId));
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

        return true;
    }
}
