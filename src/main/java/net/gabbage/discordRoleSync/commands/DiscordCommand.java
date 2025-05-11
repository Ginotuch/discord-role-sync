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
            TextComponent inviteMessage = new TextComponent(TextComponent.fromLegacyText(ChatColor.GRAY + "Our Discord: "));
            TextComponent linkText = new TextComponent(configManager.getMessage("discord_command.invite_link_text")); // Already colored by getMessage
            linkText.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, discordInviteLink));
            linkText.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to join our Discord!")));
            inviteMessage.addExtra(linkText);
            player.spigot().sendMessage(inviteMessage);
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
