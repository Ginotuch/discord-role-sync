package net.gabbage.discordRoleSync.managers;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.storage.LinkedPlayersManager;
import net.gabbage.discordRoleSync.util.LinkRequest;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

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

        // Assign default role if configured and conditions met
        if (plugin.getConfigManager().isDefaultRoleAssignmentEnabled()) {
            assignDefaultRoleIfNeeded(request.getMinecraftPlayerUUID());
        }

        // Set Discord Nickname if feature is enabled
        if (plugin.getConfigManager().shouldSynchronizeDiscordNickname()) {
            final String discordUserId = request.getDiscordUserId(); // Effectively final for lambda
            // The method parameter 'minecraftPlayerUUID' will be captured by the lambda.
            // The UUID from request.getMinecraftPlayerUUID() should be identical to the parameter.

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                Player player = plugin.getServer().getPlayer(minecraftPlayerUUID);
                if (player != null && player.isOnline()) { // Ensure player is online to get current name
                    plugin.getDiscordManager().setDiscordNickname(discordUserId, player.getName());
                } else {
                    OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(minecraftPlayerUUID);
                    if (offlinePlayer.getName() != null) {
                        plugin.getDiscordManager().setDiscordNickname(discordUserId, offlinePlayer.getName());
                    } else {
                        plugin.getLogger().warning("Could not set Discord nickname for " + discordUserId + ": Minecraft player " + minecraftPlayerUUID + " name not found (player offline and name not retrievable).");
                    }
                }
            });
        }

        plugin.getRoleSyncService().synchronizeRoles(request.getMinecraftPlayerUUID(), request.getDiscordUserId());
        plugin.getLogger().info("Player " + minecraftPlayerUUID + " successfully linked with Discord user " + request.getFullDiscordName() + " (" + request.getDiscordUserId() + "). Initial role sync triggered.");
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

    // Called by UnlinkCommand for self-unlinking
    public boolean unlinkPlayer(Player player) {
        return unlinkPlayer((OfflinePlayer) player);
    }

    // Core unlinking logic, can be called for self or others
    public boolean unlinkPlayer(OfflinePlayer targetPlayer) {
        UUID playerUUID = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName(); // Can be null if player never joined

        if (playerName == null) { // Should ideally be checked by the command before calling this
            plugin.getLogger().warning("Attempted to unlink player with UUID " + playerUUID + " but their name is null (likely never joined).");
            return false;
        }

        if (linkedPlayersManager.isMcAccountLinked(playerUUID)) {
            String discordId = linkedPlayersManager.getDiscordId(playerUUID);
            linkedPlayersManager.removeLinkByMcUUID(playerUUID); // This also removes from discordToMcLinks

            // Reset Discord Nickname if feature is enabled
            if (plugin.getConfigManager().shouldSynchronizeDiscordNickname()) {
                plugin.getDiscordManager().resetDiscordNickname(discordId);
            }

            plugin.getLogger().info("Player " + playerName + " (" + playerUUID + ") unlinked from Discord ID " + discordId + ".");
            plugin.getRoleSyncService().clearRolesOnUnlink(playerUUID, discordId);
            return true;
        }
        return false;
    }

    private void assignDefaultRoleIfNeeded(UUID minecraftPlayerUUID) {
        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(minecraftPlayerUUID);
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
            plugin.getLogger().warning("Cannot assign default role to " + minecraftPlayerUUID + ": Player data not found.");
            return;
        }

        net.milkbowl.vault.permission.Permission vaultPerms = DiscordRoleSync.getVaultPermissions();
        if (vaultPerms == null) {
            plugin.getLogger().severe("Vault permissions not available. Cannot assign default role for " + offlinePlayer.getName());
            return;
        }

        String primaryGroup = vaultPerms.getPrimaryGroup(null, offlinePlayer); // null for world means global/default context
        java.util.List<String> triggerGroups = plugin.getConfigManager().getDefaultRoleAssignmentIfInGroups()
                                                     .stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toList());
        String groupToAssign = plugin.getConfigManager().getDefaultRoleAssignmentAssignGroup();

        if (groupToAssign.isEmpty()) {
            plugin.getLogger().warning("Default role assignment is enabled, but 'assign-group' is not configured.");
            return;
        }

        boolean shouldAssign = false;
        if (primaryGroup == null) { // Player has no primary group
            shouldAssign = true;
            plugin.getLogger().info("Player " + offlinePlayer.getName() + " has no primary group. Attempting to assign default role: " + groupToAssign);
        } else if (triggerGroups.contains(primaryGroup.toLowerCase())) {
            shouldAssign = true;
            plugin.getLogger().info("Player " + offlinePlayer.getName() + "'s primary group (" + primaryGroup + ") is in the default assignment trigger list. Attempting to assign role: " + groupToAssign);
        }

        if (shouldAssign) {
            // Ensure Vault operations are on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (vaultPerms.playerAddGroup(null, offlinePlayer, groupToAssign)) {
                    plugin.getLogger().info("Successfully assigned default group '" + groupToAssign + "' to " + offlinePlayer.getName() + " on link.");
                } else {
                    plugin.getLogger().warning("Failed to assign default group '" + groupToAssign + "' to " + offlinePlayer.getName() + " on link. Check Vault-compatible permissions plugin logs.");
                }
            });
        }
    }
}
