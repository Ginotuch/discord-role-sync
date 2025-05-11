package net.gabbage.discordRoleSync.managers;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;

public class ConfigManager {

    private final DiscordRoleSync plugin;
    private FileConfiguration config;

    public ConfigManager(DiscordRoleSync plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        // This copies the config.yml from your resources to the plugin's data folder if it doesn't exist
        plugin.saveDefaultConfig();

        // Load the configuration from disk
        config = plugin.getConfig();

        // Load the defaults from the JAR's config.yml to ensure copyDefaults works correctly
        // for new keys added in future plugin updates.
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfigValues = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
            config.setDefaults(defaultConfigValues);
        }

        // This will add any new default options from the JAR's config.yml to the user's existing config.yml
        // without overwriting their existing settings.
        config.options().copyDefaults(true);
        plugin.saveConfig(); // Save the config if any new defaults were merged
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

    public java.util.List<java.util.Map<?, ?>> getRoleMappings() {
        return config.getMapList("roles.mappings");
    }

    public int getLinkRequestTimeoutMinutes() {
        return config.getInt("linking.request-timeout-minutes", 5);
    }

    public boolean shouldSynchronizeDiscordNickname() {
        return config.getBoolean("linking.synchronize-discord-nickname", true);
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
