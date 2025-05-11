package net.gabbage.discordRoleSync;

import net.gabbage.discordRoleSync.commands.LinkCommand;
import net.gabbage.discordRoleSync.commands.UnlinkCommand;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.DiscordManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordRoleSync extends JavaPlugin {

    private static DiscordRoleSync instance;
    private ConfigManager configManager;
    private DiscordManager discordManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize Configuration Manager
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // Initialize Discord Manager and connect the bot
        // Note: The DiscordManager class itself was already provided by you and is assumed to be correct.
        discordManager = new DiscordManager(this);
        discordManager.connect();

        // Register Commands
        getCommand("link").setExecutor(new LinkCommand(this));
        getCommand("unlink").setExecutor(new UnlinkCommand(this));

        getLogger().info("DiscordRoleSync has been enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (discordManager != null) {
            discordManager.disconnect();
        }
        getLogger().info("DiscordRoleSync has been disabled!");
    }

    public static DiscordRoleSync getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DiscordManager getDiscordManager() {
        return discordManager;
    }
}
