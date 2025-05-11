package net.gabbage.discordRoleSync.listeners;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import net.gabbage.discordRoleSync.service.RoleSyncService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final DiscordRoleSync plugin;
    private final LinkedPlayersManager linkedPlayersManager;
    private final RoleSyncService roleSyncService;

    public PlayerJoinListener(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.linkedPlayersManager = plugin.getLinkedPlayersManager();
        this.roleSyncService = plugin.getRoleSyncService();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (linkedPlayersManager.isMcAccountLinked(playerUUID)) {
            String discordId = linkedPlayersManager.getDiscordId(playerUUID);
            if (discordId != null && !discordId.isEmpty()) {
                plugin.getLogger().info("Linked player " + player.getName() + " joined. Triggering role synchronization and nickname update.");

                // Nickname synchronization is now handled within RoleSyncService.synchronizeRoles,
                // which is called below. Removing direct call here to prevent duplicate actions.

                // Run role synchronization async to avoid blocking login and to match RoleSyncService's async nature for JDA calls
                final String finalDiscordIdForRoles = discordId; // Capture for async task
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    roleSyncService.synchronizeRoles(playerUUID, finalDiscordIdForRoles);
                });
            } else {
                // This case should ideally not happen if isMcAccountLinked is true, but good for robustness
                plugin.getLogger().warning("Player " + player.getName() + " is marked as linked but no Discord ID found. Skipping sync on join.");
            }
        }
    }
}
