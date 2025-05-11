package net.gabbage.discordRoleSync;

import net.gabbage.discordRoleSync.commands.LinkCommand;
import net.gabbage.discordRoleSync.commands.UnlinkCommand;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.DiscordManager;
import net.gabbage.discordRoleSync.managers.LinkManager;
import net.gabbage.discordRoleSync.service.RoleSyncService;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordRoleSync extends JavaPlugin {

    private static DiscordRoleSync instance;
    private ConfigManager configManager;
    private DiscordManager discordManager;
    private LinkedPlayersManager linkedPlayersManager;
    private LinkManager linkManager;
    private RoleSyncService roleSyncService;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize Configuration Manager
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // Initialize Storage for Linked Players
        linkedPlayersManager = new LinkedPlayersManager(this);

        // Initialize Link Manager
        linkManager = new LinkManager(this, linkedPlayersManager);

        // Initialize Role Sync Service
        roleSyncService = new RoleSyncService(this);

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

    public LinkedPlayersManager getLinkedPlayersManager() {
        return linkedPlayersManager;
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public RoleSyncService getRoleSyncService() {
        return roleSyncService;
    }
}
