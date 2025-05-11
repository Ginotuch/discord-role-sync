package net.gabbage.discordRoleSync.managers;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigManager {

    private final DiscordRoleSync plugin;
    private FileConfiguration mainConfig; // Renamed from 'config'
    private FileConfiguration messagesConfig;

    public ConfigManager(DiscordRoleSync plugin) {
        this.plugin = plugin;
    }

    public void loadMainConfig() {
        plugin.saveDefaultConfig(); // Saves config.yml if not present
        plugin.reloadConfig(); // Force Bukkit to reload the config.yml from disk
        mainConfig = plugin.getConfig(); // Now this gets the reloaded config

        // Ensure defaults are applied for main config (though less critical now for messages)
        InputStream mainDefaultConfigStream = plugin.getResource("config.yml");
        if (mainDefaultConfigStream != null) {
            YamlConfiguration mainDefaultConfigValues = YamlConfiguration.loadConfiguration(new InputStreamReader(mainDefaultConfigStream));
            mainConfig.setDefaults(mainDefaultConfigValues);
        } else {
            plugin.getLogger().severe("Could not load default config.yml from JAR!");
        }
        // Apply defaults in memory but do not save back to disk immediately.
        // This prevents reformatting/comment loss in the user's config.yml unless it's the first time it's created.
        mainConfig.options().copyDefaults(true);
        // plugin.saveConfig(); // Removed this line
    }

    public void loadMessagesConfig() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        String messagesResourcePath = "messages.yml";

        InputStream defaultMessagesStream = plugin.getResource(messagesResourcePath);
        if (defaultMessagesStream == null) {
            plugin.getLogger().severe("Could not find default messages.yml in JAR! Messages will not work correctly.");
            messagesConfig = new YamlConfiguration(); // Empty config to prevent NPEs
            return;
        }
        YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultMessagesStream));
        int jarMessagesVersion = defaultMessages.getInt("_messages_version", 1); // Default to 1 if missing in JAR (should not happen)

        if (!messagesFile.exists()) {
            plugin.getLogger().info("messages.yml not found, creating a new one from defaults (version " + jarMessagesVersion + ").");
            plugin.saveResource(messagesResourcePath, false);
            messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        } else {
            messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
            int userMessagesVersion = messagesConfig.getInt("_messages_version", 0); // Default to 0 if missing
            boolean userWantsNoOverwrite = messagesConfig.getBoolean("_messages_version_do_not_overwrite", false);

            if (!userWantsNoOverwrite && userMessagesVersion < jarMessagesVersion) {
                plugin.getLogger().info("Your messages.yml (version " + userMessagesVersion + ") is outdated. Updating to version " + jarMessagesVersion + ".");
                File backupFile = new File(plugin.getDataFolder(), "messages.yml.old_version_" + userMessagesVersion);
                try {
                    if (backupFile.exists()) {
                        backupFile.delete(); // Simple backup, overwrite old backup
                    }
                    Files.copy(messagesFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Backed up your old messages.yml to messages.yml.old_version_" + userMessagesVersion);
                    plugin.saveResource(messagesResourcePath, true); // true to replace existing
                    messagesConfig = YamlConfiguration.loadConfiguration(messagesFile); // Reload the new one
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not backup or update messages.yml: " + e.getMessage());
                    // Continue with the user's potentially outdated messagesConfig
                }
            } else if (userWantsNoOverwrite) {
                plugin.getLogger().info("messages.yml auto-update skipped as '_messages_version_do_not_overwrite' is true.");
            } else if (userMessagesVersion >= jarMessagesVersion) {
                 plugin.getLogger().info("messages.yml is up to date (version " + userMessagesVersion + ").");
            }
        }
    }


    public String getBotToken() {
        return mainConfig.getString("discord.bot-token", "YOUR_BOT_TOKEN_HERE");
    }

    public String getDiscordGuildId() {
        return mainConfig.getString("discord.guild-id", "");
    }

    public String getDiscordInviteLink() {
        return mainConfig.getString("discord.invite-link", "");
    }

    public int getSyncInterval() {
        return mainConfig.getInt("sync.interval-minutes", 5);
    }

    public java.util.List<java.util.Map<?, ?>> getRoleMappings() {
        return mainConfig.getMapList("roles.mappings");
    }

    public int getLinkRequestTimeoutMinutes() {
        return mainConfig.getInt("linking.request-timeout-minutes", 5);
    }

    public boolean shouldSynchronizeDiscordNickname() {
        return mainConfig.getBoolean("linking.synchronize-discord-nickname", true);
    }

    public String getMessage(String key, String... replacements) {
        // Ensure messagesConfig is loaded, fallback to a default error message if not.
        if (messagesConfig == null) {
            plugin.getLogger().severe("messagesConfig is null when trying to get message: " + key);
            return "&cMessages not loaded, contact admin.";
        }
        String message = messagesConfig.getString("messages." + key, "&cMessage not found: " + key);
        if (message == null) return "&cMessage not found: messages." + key; // Should not happen with default
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i+1]);
            }
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getDiscordMessage(String key, String... replacements) {
        if (messagesConfig == null) {
            plugin.getLogger().severe("messagesConfig is null when trying to get Discord message: " + key);
            return "Messages not loaded, contact admin.";
        }
        String message = messagesConfig.getString("messages." + key, "Message not found: " + key); // Get raw message
        if (message == null) return "Message not found: messages." + key;
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i+1]);
            }
        }
        // First translate & codes to §, then strip all § color codes for Discord
        return org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', message));
    }
}
