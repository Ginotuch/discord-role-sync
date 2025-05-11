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
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;

import java.util.UUID;
import java.util.logging.Level;

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
        User discordUser = event.getUser();

        if (event.getName().equals("link")) {
            handleLinkCommand(event, discordUser);
        } else if (event.getName().equals("unlink")) {
            handleUnlinkCommand(event, discordUser);
        }
    }

    private void handleLinkCommand(@NotNull SlashCommandInteractionEvent event, User discordUser) {
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
            event.reply(configManager.getDiscordMessage("link.already_linked_discord_other_discord", "%mc_username%", linkedMcName)).setEphemeral(true).queue();
            return;
        }

        Player targetPlayer = Bukkit.getPlayerExact(minecraftUsername);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            event.reply(configManager.getDiscordMessage("link.player_not_online_discord", "%mc_username%", minecraftUsername)).setEphemeral(true).queue();
            return;
        }

        UUID targetMinecraftUUID = targetPlayer.getUniqueId();

        // Check if Minecraft account is already linked to someone else
        if (linkedPlayersManager.isMcAccountLinked(targetMinecraftUUID)) {
            String linkedDiscordId = linkedPlayersManager.getDiscordId(targetMinecraftUUID);
            if (!linkedDiscordId.equals(discordUser.getId())) { // Should always be true due to the first check, but good for safety
                event.reply(configManager.getDiscordMessage("link.already_linked_discord_other_mc", "%mc_username%", minecraftUsername)).setEphemeral(true).queue();
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

        // Send message to in-game player
        String ingameMessage = configManager.getMessage("link.request_received_ingame",
                "%discord_user_displayname%", discordUser.getGlobalName() != null ? discordUser.getGlobalName() : discordUser.getName(), // Use global name if available
                "%discord_user_tag%", discordUser.getAsTag(),
                "%link_code%", newRequest.getConfirmationCode()
        );
        // Run on Bukkit's main thread to interact with player
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // ingameMessage already has color codes translated (e.g., &e -> §e)
            // and placeholders like %link_code% replaced with the actual code.

            String hereClickableText = "[HERE]";
            String linkCommandText = "/link " + newRequest.getConfirmationCode();
            String denyClickableText = "[DENY]";

            // Find the colored versions in the message string
            String herePattern = ChatColor.YELLOW + hereClickableText; // &e[HERE]
            String linkCommandPattern = ChatColor.YELLOW + linkCommandText; // &e/link CODE
            String denyPattern = ChatColor.RED + denyClickableText;   // &c[DENY]

            int hereStartIndex = ingameMessage.indexOf(herePattern);
            int linkCommandStartIndex = ingameMessage.indexOf(linkCommandPattern);
            int denyStartIndex = ingameMessage.indexOf(denyPattern);

            // Build the message component by component
            List<TextComponent> components = new ArrayList<>();
            int currentPos = 0;

            // Determine the order of clickable elements
            // For simplicity, assuming a fixed order in the message string: ...[HERE].../link CODE...[DENY]...
            // A more robust solution might involve more complex parsing if the order can vary wildly.

            if (hereStartIndex != -1 && linkCommandStartIndex != -1 && denyStartIndex != -1 &&
                hereStartIndex < linkCommandStartIndex && linkCommandStartIndex < denyStartIndex) {

                // Part 1: Before [HERE]
                components.add(new TextComponent(TextComponent.fromLegacyText(ingameMessage.substring(currentPos, hereStartIndex))));
                currentPos = hereStartIndex;

                // Part 2: [HERE]
                TextComponent hereComp = new TextComponent(hereClickableText);
                hereComp.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                hereComp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/link " + newRequest.getConfirmationCode()));
                components.add(hereComp);
                currentPos += herePattern.length();

                // Part 3: Between [HERE] and /link CODE
                components.add(new TextComponent(TextComponent.fromLegacyText(ingameMessage.substring(currentPos, linkCommandStartIndex))));
                currentPos = linkCommandStartIndex;

                // Part 4: /link CODE
                TextComponent linkCmdComp = new TextComponent(linkCommandText); // Display the actual command text
                linkCmdComp.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                linkCmdComp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/link " + newRequest.getConfirmationCode()));
                components.add(linkCmdComp);
                currentPos += linkCommandPattern.length();
                
                // Part 5: Between /link CODE and [DENY]
                components.add(new TextComponent(TextComponent.fromLegacyText(ingameMessage.substring(currentPos, denyStartIndex))));
                currentPos = denyStartIndex;

                // Part 6: [DENY]
                TextComponent denyComp = new TextComponent(denyClickableText);
                denyComp.setColor(net.md_5.bungee.api.ChatColor.RED);
                denyComp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/denylink"));
                components.add(denyComp);
                currentPos += denyPattern.length();

                // Part 7: After [DENY]
                if (currentPos < ingameMessage.length()) {
                    components.add(new TextComponent(TextComponent.fromLegacyText(ingameMessage.substring(currentPos))));
                }
                targetPlayer.spigot().sendMessage(components.toArray(new TextComponent[0]));

            } else if (hereStartIndex != -1) { // Fallback if only [HERE] is found or others are in wrong order
                String part1Str = ingameMessage.substring(0, hereStartIndex);
                String part2Str = ingameMessage.substring(hereStartIndex + herePattern.length());

                TextComponent part1Component = new TextComponent(TextComponent.fromLegacyText(part1Str));
                TextComponent clickableComponent = new TextComponent(hereClickableText);
                clickableComponent.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                clickableComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/link " + newRequest.getConfirmationCode()));
                TextComponent part2Component = new TextComponent(TextComponent.fromLegacyText(part2Str));

                targetPlayer.spigot().sendMessage(part1Component, clickableComponent, part2Component);
                plugin.getLogger().info("Sent link request to " + targetPlayer.getName() + " with only [HERE] clickable as full pattern was not matched as expected.");

            } else {
                // Fallback: No clickable parts found, send plain message
                targetPlayer.sendMessage(ingameMessage);
                plugin.getLogger().info("Sent plain link request message to " + targetPlayer.getName() + " as no clickable patterns were found in: " + ingameMessage);
            }
        });

        event.reply(configManager.getDiscordMessage("link.request_sent_discord", "%mc_username%", minecraftUsername, "%link_code%", newRequest.getConfirmationCode())).setEphemeral(true).queue();
    }

    private void handleUnlinkCommand(@NotNull SlashCommandInteractionEvent event, User discordUser) {
        String discordUserId = discordUser.getId();

        if (!linkedPlayersManager.isDiscordAccountLinked(discordUserId)) {
            event.reply(configManager.getDiscordMessage("unlink.not_linked_discord")).setEphemeral(true).queue();
            return;
        }

        UUID mcUUID = linkedPlayersManager.getMcUUID(discordUserId);
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(mcUUID);
        String mcUsername = offlinePlayer.getName() != null ? offlinePlayer.getName() : mcUUID.toString();

        try {
            // Remove the link from storage
            linkedPlayersManager.removeLinkByDiscordId(discordUserId); // This also saves to file

            // Reset Discord Nickname if feature is enabled
            if (plugin.getConfigManager().shouldSynchronizeDiscordNickname()) {
                plugin.getDiscordManager().resetDiscordNickname(discordUserId);
            }

            // Clear roles
            plugin.getRoleSyncService().clearRolesOnUnlink(mcUUID, discordUserId);

            plugin.getLogger().info("Discord user " + discordUser.getAsTag() + " (ID: " + discordUserId + ") unlinked from Minecraft account " + mcUsername + " (UUID: " + mcUUID + ") via Discord command.");
            event.reply(configManager.getDiscordMessage("unlink.success_discord", "%mc_username%", mcUsername)).setEphemeral(true).queue();

            // Optionally, notify the Minecraft player if they are online
            Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
            if (onlinePlayer != null) {
                // Using a generic message, or you could add a new config message for this scenario
                onlinePlayer.sendMessage(ChatColor.YELLOW + "Your Minecraft account has been unlinked from the Discord account " + discordUser.getAsTag() + " by a command from Discord.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during Discord /unlink command for user " + discordUser.getAsTag(), e);
            event.reply(configManager.getDiscordMessage("unlink.error_discord")).setEphemeral(true).queue();
        }
    }
}
