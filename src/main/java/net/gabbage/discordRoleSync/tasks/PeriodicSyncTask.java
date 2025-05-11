package net.gabbage.discordRoleSync.tasks;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import net.gabbage.discordRoleSync.service.RoleSyncService;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

public class PeriodicSyncTask extends BukkitRunnable {

    private final DiscordRoleSync plugin;
    private final LinkedPlayersManager linkedPlayersManager;
    private final RoleSyncService roleSyncService;

    public PeriodicSyncTask(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.linkedPlayersManager = plugin.getLinkedPlayersManager();
        this.roleSyncService = plugin.getRoleSyncService();
    }

    @Override
    public void run() {
        plugin.getLogger().fine("Starting periodic role synchronization task...");
        Map<UUID, String> allLinks = linkedPlayersManager.getAllLinks();

        if (allLinks.isEmpty()) {
            plugin.getLogger().fine("No linked players found. Skipping periodic sync.");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<UUID, String> entry : allLinks.entrySet()) {
            UUID mcUUID = entry.getKey();
            String discordId = entry.getValue();
            try {
                // RoleSyncService methods are designed to be safe and log their own specific errors
                roleSyncService.synchronizeRoles(mcUUID, discordId);
                successCount++;
            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected error during periodic sync for MC UUID: " + mcUUID + ", Discord ID: " + discordId);
                e.printStackTrace();
                failCount++;
            }
        }
        plugin.getLogger().info("Periodic role synchronization task finished. Synced: " + successCount + ", Failed: " + failCount + ", Total processed: " + allLinks.size());
    }
}
