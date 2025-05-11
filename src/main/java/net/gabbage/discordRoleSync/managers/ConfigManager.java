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
        config.addDefault("sync.interval-minutes", 5);
        config.addDefault("roles.sync-direction", "BOTH"); // Options: INGAME_TO_DISCORD, DISCORD_TO_INGAME, BOTH
        config.addDefault("roles.mappings", new java.util.ArrayList<>()); // Example: - "ingamegroup:discordroleid"
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public String getBotToken() {
        return config.getString("discord.bot-token", "YOUR_BOT_TOKEN_HERE");
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
}
