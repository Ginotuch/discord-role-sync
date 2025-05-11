package net.gabbage.discordRoleSync.managers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.gabbage.discordRoleSync.DiscordRoleSync;

import javax.security.auth.login.LoginException;

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
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT) // Add intents as needed
                    .setMemberCachePolicy(MemberCachePolicy.ALL) // Cache all members for easier role management
                    // Add event listeners here later (e.g., for slash commands)
                    .build();
            jda.awaitReady(); // Wait for JDA to be fully connected
            plugin.getLogger().info("Successfully connected to Discord as " + jda.getSelfUser().getAsTag());
        } catch (LoginException e) {
            plugin.getLogger().severe("Failed to log in to Discord: Invalid token?");
            e.printStackTrace();
        } catch (InterruptedException e) {
            plugin.getLogger().severe("JDA connection was interrupted.");
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("An unexpected error occurred while connecting to Discord.");
            e.printStackTrace();
        }
    }

    public void disconnect() {
        if (jda != null) {
            jda.shutdown();
            plugin.getLogger().info("Disconnected from Discord.");
        }
    }

    public JDA getJda() {
        return jda;
    }
}
