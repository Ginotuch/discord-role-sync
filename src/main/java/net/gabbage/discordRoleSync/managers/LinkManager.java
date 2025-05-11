package net.gabbage.discordRoleSync.managers;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import net.gabbage.discordRoleSync.util.LinkRequest;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LinkManager {

    private final DiscordRoleSync plugin;
    private final LinkedPlayersManager linkedPlayersManager;
    private final Map<UUID, LinkRequest> pendingRequests; // Minecraft UUID -> LinkRequest
    private final long linkRequestTimeoutMillis;

    public LinkManager(DiscordRoleSync plugin, LinkedPlayersManager linkedPlayersManager) {
        this.plugin = plugin;
        this.linkedPlayersManager = linkedPlayersManager;
        this.pendingRequests = new ConcurrentHashMap<>();
        // Example: 5 minutes timeout for link requests, make this configurable
        this.linkRequestTimeoutMillis = plugin.getConfigManager().getLinkRequestTimeoutMinutes() * 60 * 1000;
        scheduleRequestCleanupTask();
    }

    public boolean hasPendingRequest(UUID minecraftPlayerUUID) {
        LinkRequest request = pendingRequests.get(minecraftPlayerUUID);
        if (request != null && request.isExpired(linkRequestTimeoutMillis)) {
            pendingRequests.remove(minecraftPlayerUUID); // Remove expired request
            return false;
        }
        return request != null;
    }

    public LinkRequest getPendingRequest(UUID minecraftPlayerUUID) {
        if (hasPendingRequest(minecraftPlayerUUID)) { // This also handles expiry check
            return pendingRequests.get(minecraftPlayerUUID);
        }
        return null;
    }

    public void addPendingRequest(LinkRequest request) {
        pendingRequests.put(request.getMinecraftPlayerUUID(), request);
    }

    public boolean confirmLink(UUID minecraftPlayerUUID) {
        LinkRequest request = getPendingRequest(minecraftPlayerUUID);
        if (request == null) {
            return false; // No valid pending request
        }

        linkedPlayersManager.addLink(request.getMinecraftPlayerUUID(), request.getDiscordUserId());
        pendingRequests.remove(minecraftPlayerUUID);
        // TODO: Trigger initial role sync here
        plugin.getLogger().info("Player " + minecraftPlayerUUID + " successfully linked with Discord user " + request.getFullDiscordName() + " (" + request.getDiscordUserId() + ").");
        return true;
    }

    public void removePendingRequest(UUID minecraftPlayerUUID) {
        pendingRequests.remove(minecraftPlayerUUID);
    }

    private void scheduleRequestCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            pendingRequests.entrySet().removeIf(entry -> entry.getValue().isExpired(linkRequestTimeoutMillis));
        }, 20L * 60, 20L * 60 * 5); // Check every 5 minutes, after an initial 1 minute delay
    }

    // Called by UnlinkCommand
    public boolean unlinkPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (linkedPlayersManager.isMcAccountLinked(playerUUID)) {
            String discordId = linkedPlayersManager.getDiscordId(playerUUID);
            linkedPlayersManager.removeLinkByMcUUID(playerUUID);
            plugin.getLogger().info("Player " + player.getName() + " (" + playerUUID + ") unlinked from Discord ID " + discordId + ".");
            // Optionally, attempt to remove roles from Discord/Ingame after unlinking
            // plugin.getRoleSyncService().clearRolesOnUnlink(playerUUID, discordId);
            return true;
        }
        return false;
    }
}
