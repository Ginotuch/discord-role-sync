package net.gabbage.discordRoleSync;

import net.gabbage.discordRoleSync.commands.DenyLinkCommand;
import net.gabbage.discordRoleSync.commands.DiscordCommand; // Import DiscordCommand
import net.gabbage.discordRoleSync.commands.LinkCommand;
import net.gabbage.discordRoleSync.commands.UnlinkCommand;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.DiscordManager;
import net.gabbage.discordRoleSync.managers.LinkManager; // Import LinkManager
import net.gabbage.discordRoleSync.listeners.PlayerJoinListener; // Import the new listener
import net.gabbage.discordRoleSync.service.RoleSyncService;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import net.gabbage.discordRoleSync.tasks.PeriodicSyncTask;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask; // Import BukkitTask

import java.util.logging.Logger; // Import Logger

public final class DiscordRoleSync extends JavaPlugin {

    private static DiscordRoleSync instance;
    private ConfigManager configManager;
    private DiscordManager discordManager;
    private LinkedPlayersManager linkedPlayersManager;
    private LinkManager linkManager;
    private RoleSyncService roleSyncService;
    private static Permission vaultPermissions = null; // Static Vault Permission object
    private static final Logger log = Logger.getLogger("Minecraft"); // Static logger for setup messages
    private BukkitTask periodicSyncTask;


    @Override
    public void onEnable() {
        instance = this;
        initializePluginLogic();
        getLogger().info("DiscordRoleSync has been enabled!");
    }

    private void initializePluginLogic() {
        // Initialize Configuration Manager
        configManager = new ConfigManager(this);
        configManager.loadMainConfig(); // Load main config.yml
        configManager.loadMessagesConfig(); // Load messages.yml with update logic

        // Initialize Storage for Linked Players
        linkedPlayersManager = new LinkedPlayersManager(this);

        // Initialize Link Manager
        linkManager = new LinkManager(this, linkedPlayersManager);

        // Setup Vault FIRST
        if (!setupPermissions()) {
            log.severe(String.format("[%s] - Disabled due to no Vault dependency found or Vault failed to hook!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this); // This will trigger onDisable and its shutdown logic
            return;
        }

        // Initialize Role Sync Service AFTER Vault is confirmed to be setup
        roleSyncService = new RoleSyncService(this);

        // Initialize Discord Manager and connect the bot
        discordManager = new DiscordManager(this);
        discordManager.connect();

        // Load role mappings after JDA is connected and potentially ready
        if (discordManager.getJda() != null) {
            roleSyncService.loadAndParseRoleMappings();
        } else {
            getLogger().severe("JDA connection failed. Role mappings will be empty and synchronization might not work as expected.");
        }

        // Register Commands
        LinkCommand linkCommandExecutor = new LinkCommand(this);
        getCommand("link").setExecutor(linkCommandExecutor);
        getCommand("link").setTabCompleter(linkCommandExecutor); // Set TabCompleter for /link

        UnlinkCommand unlinkCommandExecutor = new UnlinkCommand(this);
        getCommand("unlink").setExecutor(unlinkCommandExecutor);
        getCommand("unlink").setTabCompleter(unlinkCommandExecutor); // Set TabCompleter for /unlink

        getCommand("denylink").setExecutor(new DenyLinkCommand(this));
        getCommand("discord").setExecutor(new DiscordCommand(this)); // DiscordCommand now handles its own subcommands

        // Register Event Listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // Schedule Periodic Sync Task
        long syncIntervalTicks = configManager.getSyncInterval() * 60L * 20L; // interval in minutes to ticks
        if (syncIntervalTicks > 0) {
            periodicSyncTask = new PeriodicSyncTask(this).runTaskTimerAsynchronously(this, 20L * 60, syncIntervalTicks); // Initial delay 1 minute, then repeat
            getLogger().info("Periodic role synchronization task scheduled to run every " + configManager.getSyncInterval() + " minutes.");
        } else {
            getLogger().info("Periodic sync interval is 0 or negative. Task will not be scheduled."); // Changed to info from warning
        }
    }

    private boolean setupPermissions() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            log.warning(String.format("[%s] Vault not found! Permissions integration will not work.", getDescription().getName()));
            return false;
        }
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp == null) {
            log.warning(String.format("[%s] No Vault Permission provider found. Is a permissions plugin installed?", getDescription().getName()));
            return false;
        }
        vaultPermissions = rsp.getProvider();
        if (vaultPermissions == null) {
            log.warning(String.format("[%s] Vault Permission provider was null.", getDescription().getName()));
            return false;
        }
        log.info(String.format("[%s] Hooked into Vault permissions: %s", getDescription().getName(), vaultPermissions.getName()));
        return true;
    }


    @Override
    public void onDisable() {
        shutdownPluginLogic();
        getLogger().info("DiscordRoleSync has been disabled!");
    }

    private void shutdownPluginLogic() {
        getLogger().info("Shutting down DiscordRoleSync services...");
        // Cancel Periodic Sync Task
        if (periodicSyncTask != null && !periodicSyncTask.isCancelled()) {
            periodicSyncTask.cancel();
            getLogger().info("Periodic role synchronization task cancelled.");
        }
        periodicSyncTask = null; // Clear the reference

        // Disconnect Discord Bot
        if (discordManager != null) {
            discordManager.disconnect();
        }
        discordManager = null; // Clear the reference

        // Clear other manager/service references if they hold significant resources
        // or to prevent issues if plugin is partially re-enabled without full lifecycle
        roleSyncService = null;
        linkManager = null;
        linkedPlayersManager = null;
        configManager = null;
        // vaultPermissions is static and managed by Vault, usually no need to null it here
        // instance is nulled if Bukkit fully disables, but for reload, it remains.
        getLogger().info("DiscordRoleSync services shut down.");
    }

    public void reloadPlugin() {
        getLogger().info("Reloading DiscordRoleSync...");

        // The old discordManager instance (if it exists and is active)
        // will unregister its commands during shutdownPluginLogic() -> discordManager.disconnect()

        shutdownPluginLogic(); // Gracefully shut down current operations, including unregistering commands
        initializePluginLogic(); // Re-initialize everything with new configuration, which will register commands

        // The logic for clearing commands from an old guild if the ID changed is now implicitly handled:
        // 1. The old DiscordManager instance (from before shutdown) unregisters its commands based on ITS config.
        // 2. The new DiscordManager instance (after initialization) registers commands based on the NEW config.
        // This covers transitions between global/guild commands and changes in guild ID.

        getLogger().info("DiscordRoleSync reloaded successfully.");
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

    public static Permission getVaultPermissions() { // Getter for Vault permissions
        return vaultPermissions;
    }
}
