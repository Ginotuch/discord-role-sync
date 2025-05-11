package net.gabbage.discordRoleSync.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.LinkManager;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import net.gabbage.discordRoleSync.util.LinkRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DiscordCommandListener extends ListenerAdapter {

    private final DiscordRoleSync plugin;
    private final LinkManager linkManager;
    private final LinkedPlayersManager linkedPlayersManager;
    private final ConfigManager configManager;

    public DiscordCommandListener(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.linkManager = plugin.getLinkManager();
        this.linkedPlayersManager = plugin.getLinkedPlayersManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("link")) {
            return;
        }

        User discordUser = event.getUser();
        OptionMapping usernameOption = event.getOption("username");

        if (usernameOption == null) {
            event.reply(configManager.getMessage("link.error_discord") + " (Missing username parameter)").setEphemeral(true).queue();
            return;
        }
        String minecraftUsername = usernameOption.getAsString();

        // Check if Discord user is already linked
        if (linkedPlayersManager.isDiscordAccountLinked(discordUser.getId())) {
            UUID linkedMcUUID = linkedPlayersManager.getMcUUID(discordUser.getId());
            Player linkedPlayer = Bukkit.getPlayer(linkedMcUUID);
            String linkedMcName = linkedPlayer != null ? linkedPlayer.getName() : "another Minecraft account"; // Fallback if player not online / data not available
            event.reply(configManager.getMessage("link.already_linked_discord_other_discord", "%mc_username%", linkedMcName)).setEphemeral(true).queue();
            return;
        }

        Player targetPlayer = Bukkit.getPlayerExact(minecraftUsername);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            event.reply(configManager.getMessage("link.player_not_online_discord", "%mc_username%", minecraftUsername)).setEphemeral(true).queue();
            return;
        }

        UUID targetMinecraftUUID = targetPlayer.getUniqueId();

        // Check if Minecraft account is already linked to someone else
        if (linkedPlayersManager.isMcAccountLinked(targetMinecraftUUID)) {
            String linkedDiscordId = linkedPlayersManager.getDiscordId(targetMinecraftUUID);
            if (!linkedDiscordId.equals(discordUser.getId())) { // Should always be true due to the first check, but good for safety
                event.reply(configManager.getMessage("link.already_linked_discord_other_mc", "%mc_username%", minecraftUsername)).setEphemeral(true).queue();
                return;
            }
        }
        
        // Check if there's already a pending request for this Minecraft player from another Discord user
        if (linkManager.hasPendingRequest(targetMinecraftUUID)) {
            LinkRequest existingRequest = linkManager.getPendingRequest(targetMinecraftUUID);
            if (existingRequest != null && !existingRequest.getDiscordUserId().equals(discordUser.getId())) {
                event.reply("There is already a pending link request for " + minecraftUsername + " from a different Discord user. Please wait or ask them to cancel.").setEphemeral(true).queue();
                return;
            }
        }


        LinkRequest newRequest = new LinkRequest(discordUser.getId(), discordUser.getName(), discordUser.getDiscriminator(), targetMinecraftUUID);
        linkManager.addPendingRequest(newRequest);

        // Send message to in-game player (plain for now, clickable TextComponent is a later step)
        String ingameMessage = configManager.getMessage("link.request_received_ingame",
                "%discord_user_displayname%", discordUser.getGlobalName() != null ? discordUser.getGlobalName() : discordUser.getName(), // Use global name if available
                "%discord_user_tag%", discordUser.getAsTag()
        );
        // Run on Bukkit's main thread to interact with player
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            targetPlayer.sendMessage(ingameMessage);
            // TODO: Implement clickable message here
        });

        event.reply(configManager.getMessage("link.request_sent_discord", "%mc_username%", minecraftUsername)).setEphemeral(true).queue();
    }
}
