package net.gabbage.discordRoleSync.service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.managers.DiscordManager;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class RoleSyncService {

    private final DiscordRoleSync plugin;
    private final Set<UUID> playersCurrentlyProcessing = ConcurrentHashMap.newKeySet();
    private final ConfigManager configManager;
    private final Permission vaultPerms;

    public record RoleMapping(String ingameGroup, String discordRoleId, String discordRoleName, String syncDirection) {}
    private List<RoleMapping> parsedMappings; // Made non-final, populated by loadAndParseRoleMappings

    public RoleSyncService(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.vaultPerms = DiscordRoleSync.getVaultPermissions(); // Get Vault instance
        this.parsedMappings = new ArrayList<>(); // Initialize as empty
    }

    public void loadAndParseRoleMappings() {
        List<RoleMapping> mappings = new ArrayList<>();
        List<java.util.Map<?, ?>> configuredMappings = configManager.getRoleMappings();
        Guild guild = null;
        String guildId = configManager.getDiscordGuildId();

        // Ensure DiscordManager and JDA are available before trying to use them
        DiscordManager discordManager = plugin.getDiscordManager();
        if (discordManager != null && discordManager.getJda() != null && guildId != null && !guildId.isEmpty()) {
            guild = discordManager.getJda().getGuildById(guildId);
            if (guild == null) {
                plugin.getLogger().warning("Could not find Discord guild with ID: " + guildId + " for fetching role names. Role names in logs might be incomplete.");
            }
        } else if (discordManager == null || discordManager.getJda() == null) {
            plugin.getLogger().warning("JDA not available while parsing role mappings. Discord role names will not be fetched.");
        } else if (guildId == null || guildId.isEmpty()){
            plugin.getLogger().info("Discord Guild ID not configured. Discord role names will not be fetched from API during mapping parse.");
        }

        for (java.util.Map<?, ?> mappingMap : configuredMappings) {
            String ingameGroup = getStringFromMap(mappingMap, "ingame", null);
            String discordRoleIdStr = getStringFromMap(mappingMap, "discord", null);
            String syncDirection = getStringFromMap(mappingMap, "direction", "BOTH").toUpperCase(); // Default to BOTH

            if (ingameGroup == null || discordRoleIdStr == null) {
                plugin.getLogger().warning("Invalid role mapping found: missing 'ingame' or 'discord' field. Map: " + mappingMap);
                continue;
            }
            ingameGroup = ingameGroup.trim();
            discordRoleIdStr = discordRoleIdStr.trim();

            if (!List.of("BOTH", "INGAME_TO_DISCORD", "DISCORD_TO_INGAME").contains(syncDirection)) {
                plugin.getLogger().warning("Invalid sync direction '" + syncDirection + "' for mapping '" + ingameGroup + ":" + discordRoleIdStr + "'. Defaulting to 'BOTH'.");
                syncDirection = "BOTH";
            }

            String discordRoleName = "Unknown Role (ID: " + discordRoleIdStr + ")";
            if (guild != null) {
                try {
                    Role discordRole = guild.getRoleById(discordRoleIdStr);
                    if (discordRole != null) {
                        discordRoleName = discordRole.getName();
                    } else {
                        plugin.getLogger().warning("Could not find Discord role with ID: " + discordRoleIdStr + " in guild " + guild.getName() + " for mapping: " + ingameGroup);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid Discord Role ID format: " + discordRoleIdStr + " in mapping for ingame group: " + ingameGroup);
                    continue;
                }
            }

            mappings.add(new RoleMapping(ingameGroup, discordRoleIdStr, discordRoleName, syncDirection));
        }

        this.parsedMappings.clear();
        this.parsedMappings.addAll(mappings);
        plugin.getLogger().info("Loaded " + this.parsedMappings.size() + " role mappings.");
        if (this.parsedMappings.isEmpty() && configuredMappings != null && !configuredMappings.isEmpty()) {
            plugin.getLogger().warning("No valid role mappings were parsed, but " + configuredMappings.size() + " mapping entries were found in config. Please check their format (each entry should be a map with 'ingame', 'discord', and optionally 'direction').");
        }
    }

    private String getStringFromMap(java.util.Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }

    private void finalizePlayerProcessing(UUID playerUUID, String playerName, AtomicInteger pendingOpsCounter, String operationContext, String type) {
        if (pendingOpsCounter.decrementAndGet() == 0) {
            plugin.getLogger().info("All " + type + " operations for " + playerName + " (UUID: " + playerUUID + ") completed. Releasing processing lock.");
            playersCurrentlyProcessing.remove(playerUUID);
        } else {
            plugin.getLogger().fine(type + " operation (" + operationContext + ") for " + playerName + " completed, " + pendingOpsCounter.get() + " still pending.");
        }
    }

    public void synchronizeRoles(UUID minecraftPlayerUUID, String discordUserId) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(minecraftPlayerUUID);
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : minecraftPlayerUUID.toString();

        if (!playersCurrentlyProcessing.add(minecraftPlayerUUID)) {
            plugin.getLogger().info("Role synchronization for " + playerName + " (UUID: " + minecraftPlayerUUID + ") is already in progress. Skipping this request.");
            return;
        }

        plugin.getLogger().info("Starting role synchronization for " + playerName + " (UUID: " + minecraftPlayerUUID + ", Discord ID: " + discordUserId + "). Processing lock acquired.");

        if (parsedMappings == null || parsedMappings.isEmpty()) {
            plugin.getLogger().fine("No role mappings configured or loaded. Skipping synchronization for " + playerName);
            playersCurrentlyProcessing.remove(minecraftPlayerUUID); // Release lock
            return;
        }

        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
            plugin.getLogger().warning("Cannot synchronize roles for " + playerName + ": Player data not found.");
            playersCurrentlyProcessing.remove(minecraftPlayerUUID); // Release lock
            return;
        }

        if (plugin.getDiscordManager().getJda() == null) {
            plugin.getLogger().warning("JDA not available. Cannot synchronize roles for " + playerName);
            playersCurrentlyProcessing.remove(minecraftPlayerUUID); // Release lock
            return;
        }

        String guildId = configManager.getDiscordGuildId();
        if (guildId == null || guildId.isEmpty()) {
            plugin.getLogger().warning("Discord Guild ID not configured. Cannot synchronize roles.");
            playersCurrentlyProcessing.remove(minecraftPlayerUUID); // Release lock
            return;
        }
        Guild guild = plugin.getDiscordManager().getJda().getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Discord Guild with ID " + guildId + " not found. Cannot synchronize roles.");
            playersCurrentlyProcessing.remove(minecraftPlayerUUID); // Release lock
            return;
        }

        final AtomicInteger operationsCounter = new AtomicInteger(1); // Start at 1 for the initial retrieveMemberById completion

        guild.retrieveMemberById(discordUserId).queue(
            discordMember -> {
                plugin.getLogger().fine("Synchronizing roles for MC: " + playerName + " and Discord: " + discordMember.getUser().getAsTag());

                if (configManager.shouldSynchronizeDiscordNickname()) {
                    operationsCounter.incrementAndGet();
                    plugin.getDiscordManager().setDiscordNickname(discordUserId, playerName,
                        () -> finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "Set Discord Nickname Success", "Sync"),
                        () -> finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "Set Discord Nickname Failure", "Sync")
                    );
                }

                if (parsedMappings.isEmpty()) { // Double check after lock acquisition and JDA checks
                     finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "No Mappings Post-Lock", "Sync"); // This handles the retrieveMemberById completion
                     return; // Exit if no mappings
                }

                for (RoleMapping mapping : parsedMappings) {
                    plugin.getLogger().fine("Processing mapping for sync: Ingame '" + mapping.ingameGroup() + "' <-> Discord Role '" + mapping.discordRoleName() + "' (ID: " + mapping.discordRoleId() + ") with direction: " + mapping.syncDirection());
                    switch (mapping.syncDirection()) {
                        case "INGAME_TO_DISCORD":
                            operationsCounter.incrementAndGet();
                            syncSingleIngameToDiscord(offlinePlayer, discordMember, guild, mapping, operationsCounter, playerName, minecraftPlayerUUID);
                            break;
                        case "DISCORD_TO_INGAME":
                            operationsCounter.incrementAndGet();
                            syncSingleDiscordToIngame(offlinePlayer, discordMember, guild, mapping, operationsCounter, playerName, minecraftPlayerUUID);
                            break;
                        case "BOTH":
                            plugin.getLogger().fine("Sync direction: BOTH for " + mapping.ingameGroup() + ". Syncing In-game -> Discord first.");
                            operationsCounter.incrementAndGet();
                            syncSingleIngameToDiscord(offlinePlayer, discordMember, guild, mapping, operationsCounter, playerName, minecraftPlayerUUID);
                            plugin.getLogger().fine("Sync direction: BOTH for " + mapping.ingameGroup() + ". Syncing Discord -> In-game second.");
                            operationsCounter.incrementAndGet();
                            syncSingleDiscordToIngame(offlinePlayer, discordMember, guild, mapping, operationsCounter, playerName, minecraftPlayerUUID);
                            break;
                        default:
                            plugin.getLogger().warning("Unknown sync direction '" + mapping.syncDirection() + "' for mapping '" + mapping.ingameGroup() + "'. Skipping this mapping.");
                            // If we skip, we should still decrement a counter if one was conceptually allocated for this mapping.
                            // However, the current logic increments *before* calling, so no explicit decrement needed here if we just skip.
                    }
                }
                // Final decrement for the retrieveMemberById completion itself
                finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "Main Sync Logic Completion (after processing mappings)", "Sync");
            },
            failure -> {
                plugin.getLogger().warning("Could not retrieve Discord member " + discordUserId + " in guild " + guild.getName() + " for role sync: " + failure.getMessage());
                playersCurrentlyProcessing.remove(minecraftPlayerUUID); // Release lock on failure
            }
        );
    }

    private void syncSingleIngameToDiscord(OfflinePlayer offlinePlayer, Member discordMember, Guild guild, RoleMapping mapping, AtomicInteger opsCounter, String playerName, UUID mcUUID) {
        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        String worldName = (onlinePlayer != null && onlinePlayer.getWorld() != null) ? onlinePlayer.getWorld().getName() : null;

        if (vaultPerms == null) {
            plugin.getLogger().severe("[I2D] Vault permissions not available for mapping '" + mapping.ingameGroup() + "'. Skipping.");
            finalizePlayerProcessing(mcUUID, playerName, opsCounter, "VaultPermsNull_I2D_" + mapping.ingameGroup(), "Sync");
            return;
        }

        String primaryGroup = vaultPerms.getPrimaryGroup(worldName, offlinePlayer);
        boolean playerHasIngameGroup = primaryGroup != null && primaryGroup.equalsIgnoreCase(mapping.ingameGroup());

        Role discordRole = guild.getRoleById(mapping.discordRoleId());
        if (discordRole == null) {
            plugin.getLogger().warning("[I2D] Discord role ID " + mapping.discordRoleId() + " not found. Skipping.");
            finalizePlayerProcessing(mcUUID, playerName, opsCounter, "RoleNotFound_I2D_" + mapping.discordRoleId(), "Sync");
            return;
        }

        boolean memberHasDiscordRole = discordMember.getRoles().contains(discordRole);

        if (playerHasIngameGroup && !memberHasDiscordRole) {
            plugin.getDiscordManager().getDiscordTaskQueue().submit(() -> {
                try {
                    guild.addRoleToMember(discordMember, discordRole).reason("Role Sync: User has in-game group " + mapping.ingameGroup()).complete(true);
                    plugin.getLogger().fine("[I2D] Added Discord role '" + discordRole.getName() + "' to " + discordMember.getUser().getAsTag());
                    finalizePlayerProcessing(mcUUID, playerName, opsCounter, "AddRoleSuccess_I2D_" + discordRole.getName(), "Sync");
                } catch (Exception e) { // Catches JDA exceptions like InsufficientPermissionException, HierarchyException, RateLimitException, TimeoutException
                    plugin.getLogger().log(Level.WARNING, "[I2D] Failed to add Discord role '" + discordRole.getName() + "' to " + discordMember.getUser().getAsTag() + ": " + e.getMessage(), e);
                    finalizePlayerProcessing(mcUUID, playerName, opsCounter, "AddRoleFailure_I2D_" + discordRole.getName(), "Sync");
                }
            });
        } else if (!playerHasIngameGroup && memberHasDiscordRole) {
            plugin.getDiscordManager().getDiscordTaskQueue().submit(() -> {
                try {
                    guild.removeRoleFromMember(discordMember, discordRole).reason("Role Sync: User no longer has in-game group " + mapping.ingameGroup()).complete(true);
                    plugin.getLogger().fine("[I2D] Removed Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag());
                    finalizePlayerProcessing(mcUUID, playerName, opsCounter, "RemoveRoleSuccess_I2D_" + discordRole.getName(), "Sync");
                } catch (Exception e) { // Catches JDA exceptions
                    plugin.getLogger().log(Level.WARNING, "[I2D] Failed to remove Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + ": " + e.getMessage(), e);
                    finalizePlayerProcessing(mcUUID, playerName, opsCounter, "RemoveRoleFailure_I2D_" + discordRole.getName(), "Sync");
                }
            });
        } else { // No action needed for this mapping
            finalizePlayerProcessing(mcUUID, playerName, opsCounter, "NoAction_I2D_" + mapping.ingameGroup(), "Sync");
        }
    }

    private void syncSingleDiscordToIngame(OfflinePlayer offlinePlayer, Member discordMember, Guild guild, RoleMapping mapping, AtomicInteger opsCounter, String playerNameForLog, UUID mcUUID) {
        // Vault operations are synchronous when called via Bukkit scheduler, so they don't need to increment the JDA opsCounter further.
        // The opsCounter.incrementAndGet() was done before calling this method. This method just finalizes that one count.
        String localPlayerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : mcUUID.toString(); // Use local var for clarity
        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        String worldName = (onlinePlayer != null && onlinePlayer.getWorld() != null) ? onlinePlayer.getWorld().getName() : null;

        if (vaultPerms == null) {
            plugin.getLogger().severe("[D2I] Vault permissions not available for mapping '" + mapping.ingameGroup() + "'.");
            finalizePlayerProcessing(mcUUID, localPlayerName, opsCounter, "VaultPermsNull_D2I_" + mapping.ingameGroup(), "Sync");
            return;
        }

        Role discordRole = guild.getRoleById(mapping.discordRoleId());
        if (discordRole == null) {
            plugin.getLogger().warning("[D2I] Discord role ID " + mapping.discordRoleId() + " not found.");
            finalizePlayerProcessing(mcUUID, localPlayerName, opsCounter, "RoleNotFound_D2I_" + mapping.discordRoleId(), "Sync");
            return;
        }

        boolean memberHasDiscordRole = discordMember.getRoles().contains(discordRole);
        String primaryGroup = vaultPerms.getPrimaryGroup(worldName, offlinePlayer);
        boolean playerHasIngameGroup = primaryGroup != null && primaryGroup.equalsIgnoreCase(mapping.ingameGroup());

        if (memberHasDiscordRole && !playerHasIngameGroup) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (vaultPerms.playerAddGroup(null, offlinePlayer, mapping.ingameGroup())) {
                    plugin.getLogger().fine("[D2I] Successfully added group '" + mapping.ingameGroup() + "' to " + localPlayerName);
                } else {
                    plugin.getLogger().warning("[D2I] Failed to add group '" + mapping.ingameGroup() + "' to " + localPlayerName);
                }
                finalizePlayerProcessing(mcUUID, localPlayerName, opsCounter, "AddGroupAttempt_D2I_" + mapping.ingameGroup(), "Sync");
            });
        } else if (!memberHasDiscordRole && playerHasIngameGroup) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (vaultPerms.playerRemoveGroup(null, offlinePlayer, mapping.ingameGroup())) {
                    plugin.getLogger().fine("[D2I] Successfully removed group '" + mapping.ingameGroup() + "' from " + localPlayerName);
                } else {
                    plugin.getLogger().warning("[D2I] Failed to remove group '" + mapping.ingameGroup() + "' from " + localPlayerName);
                }
                finalizePlayerProcessing(mcUUID, localPlayerName, opsCounter, "RemoveGroupAttempt_D2I_" + mapping.ingameGroup(), "Sync");
            });
        } else { // No action needed
            finalizePlayerProcessing(mcUUID, localPlayerName, opsCounter, "NoAction_D2I_" + mapping.ingameGroup(), "Sync");
        }
    }

    public void clearRolesOnUnlink(UUID minecraftPlayerUUID, String discordUserId) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(minecraftPlayerUUID);
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : minecraftPlayerUUID.toString();

        if (!playersCurrentlyProcessing.add(minecraftPlayerUUID)) {
            plugin.getLogger().info("Role clearing for " + playerName + " (UUID: " + minecraftPlayerUUID + ") is already in progress. Skipping this request.");
            return;
        }
        plugin.getLogger().info("Starting role clearing for " + playerName + " (UUID: " + minecraftPlayerUUID + ", Discord ID: " + discordUserId + "). Processing lock acquired.");

        if (parsedMappings == null) { // Check if parsedMappings is null
            plugin.getLogger().fine("No role mappings loaded. Skipping role clearing for " + playerName);
            playersCurrentlyProcessing.remove(minecraftPlayerUUID);
            return;
        }
        // No need to check parsedMappings.isEmpty() here if we handle it inside the JDA callback with the counter

        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        String worldName = (onlinePlayer != null && onlinePlayer.getWorld() != null) ? onlinePlayer.getWorld().getName() : null;

        final AtomicInteger operationsCounter = new AtomicInteger(1); // Start at 1 for retrieveMemberById completion

        // Reset Discord Nickname as part of the unlink process
        if (configManager.shouldSynchronizeDiscordNickname()) {
            operationsCounter.incrementAndGet();
            plugin.getDiscordManager().resetDiscordNickname(discordUserId,
                () -> finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "Reset Nickname Success", "Unlink"),
                () -> finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "Reset Nickname Failure", "Unlink")
            );
        }

        // Clear Discord roles
        if (plugin.getDiscordManager().getJda() != null) {
            String guildId = configManager.getDiscordGuildId();
            if (guildId != null && !guildId.isEmpty()) {
                Guild guild = plugin.getDiscordManager().getJda().getGuildById(guildId);
                if (guild != null) {
                    operationsCounter.incrementAndGet(); // For the retrieveMemberById itself for roles
                    guild.retrieveMemberById(discordUserId).queue(
                        discordMember -> { // retrieveMemberById for roles success
                            if (parsedMappings.isEmpty()) {
                                plugin.getLogger().fine("No Discord mappings to process for unlink for " + playerName);
                                finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "No Discord Mappings for Unlink", "Unlink"); // This handles the retrieveMemberById for roles
                                return; // Exit if no mappings
                            }
                            for (RoleMapping mapping : parsedMappings) {
                                if (!"INGAME_TO_DISCORD".equals(mapping.syncDirection()) && !"BOTH".equals(mapping.syncDirection())) {
                                    continue;
                                }
                                Role discordRole = guild.getRoleById(mapping.discordRoleId());
                                if (discordRole == null) {
                                    plugin.getLogger().warning("On unlink, could not find Discord role ID: " + mapping.discordRoleId() + ". Skipping removal.");
                                    continue;
                                }
                                if (discordMember.getRoles().contains(discordRole)) {
                                    operationsCounter.incrementAndGet();
                                    plugin.getDiscordManager().getDiscordTaskQueue().submit(() -> {
                                        try {
                                            guild.removeRoleFromMember(discordMember, discordRole).reason("Role Sync: User unlinked Minecraft account.").complete(true);
                                            plugin.getLogger().fine("Removed Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " on unlink.");
                                            finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "RemoveDiscordRoleSuccess_" + discordRole.getName(), "Unlink");
                                        } catch (Exception e) { // Catches JDA exceptions
                                            plugin.getLogger().log(Level.WARNING, "Failed to remove Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " on unlink: " + e.getMessage(), e);
                                            finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "RemoveDiscordRoleFailure_" + discordRole.getName(), "Unlink");
                                        }
                                    });
                                }
                            }
                            finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "Discord Role Clearing Logic Complete (after processing mappings)", "Unlink"); // For retrieveMemberById for roles
                        },
                        failure -> {
                            plugin.getLogger().warning("Could not retrieve Discord member " + discordUserId + " for clearing Discord roles on unlink: " + failure.getMessage());
                            finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "RetrieveMemberFailure_DiscordRoles", "Unlink"); // For retrieveMemberById for roles
                        }
                    );
                } else {
                    plugin.getLogger().warning("Discord Guild with ID " + guildId + " not found. Cannot clear Discord roles.");
                }
            } else {
                plugin.getLogger().warning("Discord Guild ID not configured. Cannot clear Discord roles.");
            }
        } else {
            plugin.getLogger().warning("JDA not available. Cannot clear Discord roles for " + playerName);
        }

        // Clear In-Game groups
        if (vaultPerms != null) {
            if (parsedMappings.isEmpty() && operationsCounter.get() == 1) { // Only initial retrieveMemberById pending
                 // No specific log here, covered by the final finalizePlayerProcessing
            } else if (!parsedMappings.isEmpty()) {
                 operationsCounter.incrementAndGet(); // Count the whole block of Vault operations as one
                 plugin.getServer().getScheduler().runTask(plugin, () -> {
                    int removedGroupsCount = 0;
                    for (RoleMapping mapping : parsedMappings) {
                        if ("DISCORD_TO_INGAME".equals(mapping.syncDirection()) || "BOTH".equals(mapping.syncDirection())) {
                            String primaryGroup = vaultPerms.getPrimaryGroup(worldName, offlinePlayer);
                            boolean playerHasIngameGroup = primaryGroup != null && primaryGroup.equalsIgnoreCase(mapping.ingameGroup());
                            if (playerHasIngameGroup) {
                                plugin.getLogger().fine("Attempting to remove in-game group '" + mapping.ingameGroup() + "' from " + playerName + " on unlink.");
                                if (vaultPerms.playerRemoveGroup(null, offlinePlayer, mapping.ingameGroup())) {
                                    plugin.getLogger().fine("Successfully removed in-game group '" + mapping.ingameGroup() + "' from " + playerName + " on unlink.");
                                    removedGroupsCount++;
                                } else {
                                    plugin.getLogger().warning("Failed to remove in-game group '" + mapping.ingameGroup() + "' from " + playerName + " on unlink.");
                                }
                            }
                        }
                    }
                    plugin.getLogger().fine("In-game group removal task completed for " + playerName + " on unlink. Removed " + removedGroupsCount + " groups.");
                    finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "Vault Group Clearing", "Unlink");
                });
            }
        } else {
            plugin.getLogger().warning("Vault permissions not available. Cannot clear in-game groups for " + playerName + " on unlink.");
        }
        
        // Final decrement for the initial setup of clearRolesOnUnlink
        finalizePlayerProcessing(minecraftPlayerUUID, playerName, operationsCounter, "Main Unlink Logic Completion", "Unlink");
    }

    public List<RoleMapping> getParsedMappings() {
        return new ArrayList<>(parsedMappings); // Return a copy to prevent external modification
    }
}
