package net.gabbage.discordRoleSync.managers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import java.time.Duration;
import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.discord.DiscordCommandListener;
import net.gabbage.discordRoleSync.util.DiscordTaskQueue;


public class DiscordManager {

    private final DiscordRoleSync plugin;
    private JDA jda;
    private final DiscordTaskQueue discordTaskQueue;

    public DiscordManager(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.discordTaskQueue = new DiscordTaskQueue(plugin);
    }

    public void connect() {
        String botToken = plugin.getConfigManager().getBotToken();

        if (botToken == null || botToken.isEmpty() || "YOUR_BOT_TOKEN_HERE".equals(botToken)) {
            plugin.getLogger().severe("Discord Bot Token is not configured or is invalid. Please set it in config.yml.");
            plugin.getLogger().severe("Discord integration will be disabled.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES) // Removed DIRECT_MESSAGES
                    .setMemberCachePolicy(MemberCachePolicy.ALL) // Cache all members for easier role management
                    .addEventListeners(new DiscordCommandListener(plugin)) // Register command listener
                    .build();
            jda.awaitReady(); // Wait for JDA to be fully connected
            plugin.getLogger().info("Successfully connected to Discord as " + jda.getSelfUser().getAsTag());

            registerCommands();

        } catch (InterruptedException e) {
            plugin.getLogger().severe("JDA connection was interrupted.");
            Thread.currentThread().interrupt();
        } catch (Exception e) { // Catching generic Exception for LoginException and others
            plugin.getLogger().severe("An unexpected error occurred while connecting to Discord: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for detailed debugging
        }
    }

    private void registerCommands() {
        if (jda == null) return;

        String guildId = plugin.getConfigManager().getDiscordGuildId();
        if (guildId != null && !guildId.isEmpty()) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                guild.updateCommands().addCommands(
                        Commands.slash("link", "Links your Discord account to a Minecraft account.")
                                .addOption(OptionType.STRING, "username", "Your Minecraft username", true),
                        Commands.slash("unlink", "Unlinks your Discord account from a Minecraft account.")
                ).queue(
                        cmds -> plugin.getLogger().info("Successfully registered guild slash commands for guild " + guildId),
                        error -> plugin.getLogger().severe("Failed to register guild slash commands for guild " + guildId + ": " + error.getMessage())
                );
            } else {
                plugin.getLogger().warning("Could not find Discord Guild with ID: " + guildId + ". Slash commands will be registered globally. This might take up to an hour to propagate.");
                registerGlobalCommands();
            }
        } else {
            plugin.getLogger().info("No Discord Guild ID configured. Slash commands will be registered globally. This might take up to an hour to propagate.");
            registerGlobalCommands();
        }
    }

    private void registerGlobalCommands() {
        if (jda == null) return;
        jda.updateCommands().addCommands(
                Commands.slash("link", "Links your Discord account to a Minecraft account.")
                        .addOption(OptionType.STRING, "username", "Your Minecraft username", true),
                Commands.slash("unlink", "Unlinks your Discord account from a Minecraft account.")
        ).queue(
                cmds -> plugin.getLogger().info("Successfully registered global slash commands."),
                error -> plugin.getLogger().severe("Failed to register global slash commands: " + error.getMessage())
        );
    }


    public void disconnect() {
        if (discordTaskQueue != null) {
            discordTaskQueue.shutdown();
        }

        if (jda != null && jda.getStatus() != JDA.Status.SHUTDOWN && jda.getStatus() != JDA.Status.SHUTTING_DOWN) {
            try {
                String currentGuildId = plugin.getConfigManager().getDiscordGuildId(); // Get guild ID from config this JDA instance was using
                if (currentGuildId != null && !currentGuildId.isEmpty()) {
                    Guild guild = jda.getGuildById(currentGuildId);
                    if (guild != null) {
                        plugin.getLogger().info("Unregistering commands from guild: " + guild.getName() + " (" + currentGuildId + ")");
                        guild.updateCommands().addCommands(java.util.Collections.emptyList()).complete(true);
                        plugin.getLogger().info("Successfully unregistered commands from guild " + currentGuildId);
                    } else {
                        plugin.getLogger().warning("Could not find guild " + currentGuildId + " to unregister commands during shutdown. Bot might have been removed or Guild ID was incorrect for this JDA session.");
                    }
                } else {
                    plugin.getLogger().info("Unregistering global commands.");
                    jda.updateCommands().addCommands(java.util.Collections.emptyList()).complete(true);
                    plugin.getLogger().info("Successfully unregistered global commands.");
                }
            } catch (Exception e) { // Catch exceptions from .complete(), e.g., InsufficientPermissionException, TimeoutException
                plugin.getLogger().warning("Failed to unregister commands during shutdown: " + e.getMessage());
            }

            plugin.getLogger().info("Attempting to disconnect from Discord (JDA shutdown)...");
            jda.shutdown(); // Initiate shutdown
            try {
                // Wait up to 10 seconds for JDA to finish its shutdown processes
                if (!jda.awaitShutdown(Duration.ofSeconds(10))) {
                    plugin.getLogger().warning("JDA did not shut down gracefully within 10 seconds. Forcing shutdown...");
                    jda.shutdownNow(); // Forcefully shut down remaining JDA threads
                    if (!jda.awaitShutdown(Duration.ofSeconds(5))) {
                        plugin.getLogger().severe("JDA did not shut down even after forcing. There might be lingering threads.");
                    }
                }
                plugin.getLogger().info("Successfully disconnected from Discord.");
            } catch (InterruptedException e) {
                plugin.getLogger().warning("Interrupted while waiting for JDA to shut down. Forcing shutdown now.");
                jda.shutdownNow();
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
        } else if (jda != null) {
            plugin.getLogger().info("JDA already shutting down or shutdown. Skipping command unregistration and JDA shutdown sequence.");
        }
        // Ensure jda is nullified if it was previously not null, even if shutdown was skipped due to status
        this.jda = null; 
    }

    public JDA getJda() {
        return jda;
    }

    public DiscordTaskQueue getDiscordTaskQueue() {
        return discordTaskQueue;
    }

    public void setDiscordNickname(String userId, String nickname, Runnable onCompleteSuccess, Runnable onCompleteFailure) {
        if (jda == null || !plugin.getConfigManager().shouldSynchronizeDiscordNickname()) {
            if (onCompleteFailure != null) onCompleteFailure.run();
            return;
        }
        String guildId = plugin.getConfigManager().getDiscordGuildId();
        if (guildId == null || guildId.isEmpty()) {
            plugin.getLogger().fine("Cannot set Discord nickname: Guild ID not configured.");
            if (onCompleteFailure != null) onCompleteFailure.run();
            return;
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Cannot set Discord nickname: Guild with ID " + guildId + " not found.");
            if (onCompleteFailure != null) onCompleteFailure.run();
            return;
        }

        discordTaskQueue.submit(() -> {
            try {
                net.dv8tion.jda.api.entities.Member member = guild.retrieveMemberById(userId).complete(true);
                if (member == null) { // Should not happen with complete(true) as it throws on failure
                    plugin.getLogger().warning("Failed to retrieve member " + userId + " in guild " + guildId + " to set nickname (member was null post-complete).");
                    if (onCompleteFailure != null) onCompleteFailure.run();
                    return;
                }

                String currentNickname = member.getNickname();
                if (nickname.equals(currentNickname)) {
                    plugin.getLogger().fine("Discord nickname for " + member.getUser().getAsTag() + " is already '" + nickname + "'. No update needed.");
                    if (onCompleteSuccess != null) onCompleteSuccess.run();
                    return;
                }

                member.modifyNickname(nickname).reason("Linked to Minecraft account: " + nickname).complete(true);
                plugin.getLogger().info("Set nickname for " + member.getUser().getAsTag() + " to '" + nickname + "' in guild " + guild.getName());
                if (onCompleteSuccess != null) onCompleteSuccess.run();

            } catch (InsufficientPermissionException e) {
                plugin.getLogger().warning("Bot lacks 'Manage Nicknames' permission in guild " + guild.getName() + " to set nickname for user ID " + userId + ".");
                if (onCompleteFailure != null) onCompleteFailure.run();
            } catch (HierarchyException e) {
                plugin.getLogger().warning("Cannot set nickname for user ID " + userId + " in guild " + guild.getName() + " due to role hierarchy (bot's role is not high enough).");
                if (onCompleteFailure != null) onCompleteFailure.run();
            } catch (Exception e) { // Catches other JDA exceptions like TimeoutException, RateLimitException from .complete(true)
                plugin.getLogger().warning("Failed to set nickname for user ID " + userId + " in guild " + guildId + ": " + e.getMessage());
                if (onCompleteFailure != null) onCompleteFailure.run();
            }
        });
    }

    public void resetDiscordNickname(String userId, Runnable onCompleteSuccess, Runnable onCompleteFailure) {
        if (jda == null || !plugin.getConfigManager().shouldSynchronizeDiscordNickname()) {
            if (onCompleteFailure != null) onCompleteFailure.run(); // Or success if "not enabled" is not a failure
            return;
        }
        String guildId = plugin.getConfigManager().getDiscordGuildId();
        if (guildId == null || guildId.isEmpty()) {
            plugin.getLogger().fine("Cannot reset Discord nickname: Guild ID not configured.");
            if (onCompleteFailure != null) onCompleteFailure.run();
            return;
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Cannot reset Discord nickname: Guild with ID " + guildId + " not found.");
            if (onCompleteFailure != null) onCompleteFailure.run();
            return;
        }

        discordTaskQueue.submit(() -> {
            try {
                net.dv8tion.jda.api.entities.Member member = guild.retrieveMemberById(userId).complete(true);
                if (member == null) { // Should not happen with complete(true)
                    plugin.getLogger().warning("Failed to retrieve member " + userId + " in guild " + guildId + " to reset nickname (member was null post-complete).");
                    if (onCompleteFailure != null) onCompleteFailure.run();
                    return;
                }

                if (member.getNickname() != null && !member.getNickname().isEmpty()) {
                    member.modifyNickname(null).reason("Unlinked from Minecraft account").complete(true);
                    plugin.getLogger().info("Reset nickname for " + member.getUser().getAsTag() + " in guild " + guild.getName());
                    if (onCompleteSuccess != null) onCompleteSuccess.run();
                } else {
                    plugin.getLogger().fine("Nickname for " + member.getUser().getAsTag() + " in guild " + guild.getName() + " was already null or empty. No reset action needed.");
                    if (onCompleteSuccess != null) onCompleteSuccess.run();
                }
            } catch (InsufficientPermissionException e) {
                plugin.getLogger().warning("Bot lacks 'Manage Nicknames' permission in guild " + guild.getName() + " to reset nickname for user ID " + userId + ".");
                if (onCompleteFailure != null) onCompleteFailure.run();
            } catch (HierarchyException e) {
                plugin.getLogger().warning("Cannot reset nickname for user ID " + userId + " in guild " + guild.getName() + " due to role hierarchy (bot's role is not high enough).");
                if (onCompleteFailure != null) onCompleteFailure.run();
            } catch (Exception e) { // Catches other JDA exceptions
                plugin.getLogger().warning("Failed to reset nickname for user ID " + userId + " in guild " + guildId + ": " + e.getMessage());
                if (onCompleteFailure != null) onCompleteFailure.run();
            }
        });
    }

    public void sendDirectMessage(String userId, String message) {
        // DM functionality has been removed as per user request.
        // plugin.getLogger().fine("DM sending is disabled. Attempted to send DM to " + userId + " with message: " + message);
    }

    public void sendLinkDeniedDM(String userId, String minecraftUsername) {
        // DM functionality has been removed as per user request.
        // plugin.getLogger().fine("DM sending is disabled. Attempted to send Link Denied DM to " + userId + " regarding MC user: " + minecraftUsername);
    }

    public void clearCommandsFromGuild(String guildIdToClear) {
        if (jda == null) {
            plugin.getLogger().warning("Cannot clear commands from guild " + guildIdToClear + ": JDA is not initialized.");
            return;
        }
        if (guildIdToClear == null || guildIdToClear.isEmpty()) {
            // This case should ideally be handled by the caller, but good to have a safeguard.
            plugin.getLogger().fine("Cannot clear commands: No specific guild ID provided to clear from.");
            return;
        }

        Guild guild = jda.getGuildById(guildIdToClear);
        if (guild != null) {
            plugin.getLogger().info("Attempting to clear slash commands from old guild: " + guild.getName() + " (" + guildIdToClear + ")");
            guild.updateCommands().addCommands().queue(
                success -> plugin.getLogger().info("Successfully cleared slash commands from guild: " + guildIdToClear),
                error -> plugin.getLogger().severe("Failed to clear slash commands from guild " + guildIdToClear + ": " + error.getMessage())
            );
        } else {
            // This can happen if the bot was removed from the old guild, or the ID was for a guild it was never in.
            plugin.getLogger().warning("Could not find old guild with ID '" + guildIdToClear + "' to clear commands. Bot might have been removed or ID is incorrect. This is not necessarily an error if the bot was intentionally removed from that guild.");
        }
    }
}
