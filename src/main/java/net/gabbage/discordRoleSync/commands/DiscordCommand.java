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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DiscordCommand implements CommandExecutor, TabCompleter {

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

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReloadSubcommand(player, configManager);
            return true;
        }

        // Default behavior: show status/invite
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

    private void handleReloadSubcommand(Player player, ConfigManager configManager) {
        if (!player.hasPermission("discordrolesync.reload")) {
            player.sendMessage(configManager.getMessage("reload.no_permission"));
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Reloading DiscordRoleSync plugin...");
        try {
            plugin.reloadPlugin();
            player.sendMessage(configManager.getMessage("reload.success"));
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error during plugin reload triggered by /discord reload command", e);
            player.sendMessage(configManager.getMessage("reload.failure"));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            if (sender.hasPermission("discordrolesync.reload")) {
                subcommands.add("reload");
            }
            // Add other subcommands here in the future if needed

            return subcommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
