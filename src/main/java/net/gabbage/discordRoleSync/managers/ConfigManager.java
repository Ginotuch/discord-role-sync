package net.gabbage.discordRoleSync.managers;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final DiscordRoleSync plugin;
    private FileConfiguration config;

    public ConfigManager(DiscordRoleSync plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        // Add default values if they don't exist
        config.addDefault("discord.bot-token", "YOUR_BOT_TOKEN_HERE");
        config.addDefault("discord.guild-id", ""); // Optional: For guild-specific command registration
        config.addDefault("sync.interval-minutes", 5);
        config.addDefault("linking.request-timeout-minutes", 5);
        config.addDefault("roles.sync-direction", "BOTH"); // Options: INGAME_TO_DISCORD, DISCORD_TO_INGAME, BOTH
        config.addDefault("roles.mappings", new java.util.ArrayList<>()); // Example: - "ingamegroup:discordroleid"

        // Messages
        config.addDefault("messages.link.request_received_ingame", "&aRequest received to link to the Discord account \"&e%discord_user_displayname%&a\" (&e%discord_user_tag%&a). Type &e/link&a or click &e[HERE]&a to link accounts.");
        config.addDefault("messages.link.request_sent_discord", "Link request sent to Minecraft player &e%mc_username%&r. They need to type &e/link&r in-game to confirm.");
        config.addDefault("messages.link.no_pending_request_ingame", "&cYou have no pending link requests. Ask a Discord user to use /link %your_mc_username% in the Discord server.");
        config.addDefault("messages.link.already_linked_ingame", "&cYour Minecraft account is already linked to a Discord account.");
        config.addDefault("messages.link.already_linked_discord_self", "&cYour Discord account is already linked to a Minecraft account.");
        config.addDefault("messages.link.already_linked_discord_other_mc", "&cThe Minecraft account &e%mc_username%&c is already linked to another Discord account.");
        config.addDefault("messages.link.already_linked_discord_other_discord", "&cYour Discord account is already linked to the Minecraft account &e%mc_username%&c.");
        config.addDefault("messages.link.success_ingame", "&aYour Minecraft account has been successfully linked with Discord account &e%discord_user_tag%&a!");
        config.addDefault("messages.link.success_discord", "&aYour Discord account has been successfully linked with Minecraft account &e%mc_username%&a!");
        config.addDefault("messages.link.player_not_online_discord", "&cPlayer &e%mc_username%&c is not currently online.");
        config.addDefault("messages.link.error_discord", "&cAn error occurred while trying to send a link request. Please try again later.");
        config.addDefault("messages.link.request_expired_ingame", "&cYour pending link request has expired. Please ask the Discord user to send a new one.");

        config.addDefault("messages.unlink.not_linked_ingame", "&cYour Minecraft account is not linked to any Discord account.");
        config.addDefault("messages.unlink.success_ingame", "&aYour Minecraft account has been successfully unlinked.");
        config.addDefault("messages.unlink.error_ingame", "&cAn error occurred while trying to unlink your account.");


        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public String getBotToken() {
        return config.getString("discord.bot-token", "YOUR_BOT_TOKEN_HERE");
    }

    public String getDiscordGuildId() {
        return config.getString("discord.guild-id", "");
    }

    public int getSyncInterval() {
        return config.getInt("sync.interval-minutes", 5);
    }

    public String getSyncDirection() {
        return config.getString("roles.sync-direction", "BOTH");
    }

    public java.util.List<String> getRoleMappings() {
        return config.getStringList("roles.mappings");
    }

    public int getLinkRequestTimeoutMinutes() {
        return config.getInt("linking.request-timeout-minutes", 5);
    }

    public String getMessage(String key, String... replacements) {
        String message = config.getString("messages." + key, "&cMessage not found: " + key);
        if (message == null) return "&cMessage not found: messages." + key; // Should not happen with default
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i+1]);
            }
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }
}
