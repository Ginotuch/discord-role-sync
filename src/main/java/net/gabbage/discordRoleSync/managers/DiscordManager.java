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


public class DiscordManager {

    private final DiscordRoleSync plugin;
    private JDA jda;

    public DiscordManager(DiscordRoleSync plugin) {
        this.plugin = plugin;
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
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES) // Removed MESSAGE_CONTENT, kept DIRECT_MESSAGES for DMs
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
        if (jda != null) {
            plugin.getLogger().info("Attempting to disconnect from Discord...");
            jda.shutdown(); // Initiate shutdown
            try {
                // Wait up to 10 seconds for JDA to finish its shutdown processes
                if (!jda.awaitShutdown(Duration.ofSeconds(10))) {
                    plugin.getLogger().warning("JDA did not shut down gracefully within 10 seconds. Forcing shutdown...");
                    jda.shutdownNow(); // Forcefully shut down remaining JDA threads
                    // Optionally, wait a little longer for forced shutdown
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
        }
    }

    public JDA getJda() {
        return jda;
    }

    public void setDiscordNickname(String userId, String nickname) {
        if (jda == null || !plugin.getConfigManager().shouldSynchronizeDiscordNickname()) {
            return;
        }
        String guildId = plugin.getConfigManager().getDiscordGuildId();
        if (guildId == null || guildId.isEmpty()) {
            plugin.getLogger().fine("Cannot set Discord nickname: Guild ID not configured.");
            return;
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Cannot set Discord nickname: Guild with ID " + guildId + " not found.");
            return;
        }

        guild.retrieveMemberById(userId).queue(member -> {
            try {
                member.modifyNickname(nickname).reason("Linked to Minecraft account: " + nickname).queue(
                        success -> plugin.getLogger().info("Set nickname for " + member.getUser().getAsTag() + " to '" + nickname + "' in guild " + guild.getName()),
                        failure -> plugin.getLogger().warning("Failed to set nickname for " + member.getUser().getAsTag() + " in guild " + guild.getName() + ": " + failure.getMessage())
                );
            } catch (InsufficientPermissionException e) {
                plugin.getLogger().warning("Bot lacks 'Manage Nicknames' permission in guild " + guild.getName() + " to set nickname for " + member.getUser().getAsTag() + ".");
            } catch (HierarchyException e) {
                plugin.getLogger().warning("Cannot set nickname for " + member.getUser().getAsTag() + " in guild " + guild.getName() + " due to role hierarchy (bot's role is not high enough).");
            }
        }, failure -> plugin.getLogger().warning("Failed to retrieve member " + userId + " in guild " + guildId + " to set nickname."));
    }

    public void resetDiscordNickname(String userId) {
        if (jda == null || !plugin.getConfigManager().shouldSynchronizeDiscordNickname()) {
            return;
        }
        String guildId = plugin.getConfigManager().getDiscordGuildId();
        if (guildId == null || guildId.isEmpty()) {
            plugin.getLogger().fine("Cannot reset Discord nickname: Guild ID not configured.");
            return;
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Cannot reset Discord nickname: Guild with ID " + guildId + " not found.");
            return;
        }

        guild.retrieveMemberById(userId).queue(member -> {
            try {
                // Check if the current nickname is one that the bot might have set (e.g., matches a known player name pattern or simply reset if any nickname exists)
                // For simplicity, we'll reset it if it's not null or empty, assuming the bot might have set it.
                // A more robust check might involve storing if the bot set the nickname.
                if (member.getNickname() != null && !member.getNickname().isEmpty()) {
                    member.modifyNickname(null).reason("Unlinked from Minecraft account").queue(
                            success -> plugin.getLogger().info("Reset nickname for " + member.getUser().getAsTag() + " in guild " + guild.getName()),
                            failure -> plugin.getLogger().warning("Failed to reset nickname for " + member.getUser().getAsTag() + " in guild " + guild.getName() + ": " + failure.getMessage())
                    );
                }
            } catch (InsufficientPermissionException e) {
                plugin.getLogger().warning("Bot lacks 'Manage Nicknames' permission in guild " + guild.getName() + " to reset nickname for " + member.getUser().getAsTag() + ".");
            } catch (HierarchyException e) {
                plugin.getLogger().warning("Cannot reset nickname for " + member.getUser().getAsTag() + " in guild " + guild.getName() + " due to role hierarchy (bot's role is not high enough).");
            }
        }, failure -> plugin.getLogger().warning("Failed to retrieve member " + userId + " in guild " + guildId + " to reset nickname."));
    }

    public void sendDirectMessage(String userId, String message) {
        if (jda == null) {
            plugin.getLogger().warning("Attempted to send DM while JDA is not initialized.");
            return;
        }
        jda.retrieveUserById(userId).queue(
            user -> user.openPrivateChannel().queue(
                channel -> channel.sendMessage(message).queue(
                    success -> plugin.getLogger().info("Successfully sent DM to " + user.getAsTag()),
                    failure -> plugin.getLogger().warning("Failed to send DM to " + user.getAsTag() + ": " + failure.getMessage())
                ),
                failure -> plugin.getLogger().warning("Failed to open private channel with " + userId + ": " + failure.getMessage())
            ),
            failure -> plugin.getLogger().warning("Failed to retrieve user " + userId + " for DM: " + failure.getMessage())
        );
    }
}
