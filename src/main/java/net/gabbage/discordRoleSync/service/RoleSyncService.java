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
import java.util.UUID;
import java.util.logging.Level;

public class RoleSyncService {

    private final DiscordRoleSync plugin;
    private final ConfigManager configManager;
    private final Permission vaultPerms;

    private record RoleMapping(String ingameGroup, String discordRoleId, String discordRoleName, String syncDirection) {}
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

    public void synchronizeRoles(UUID minecraftPlayerUUID, String discordUserId) {
        if (parsedMappings == null || parsedMappings.isEmpty()) { // Check if parsedMappings is null
            plugin.getLogger().fine("No role mappings configured or loaded. Skipping synchronization for " + minecraftPlayerUUID);
            return;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(minecraftPlayerUUID);
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
            plugin.getLogger().warning("Cannot synchronize roles for " + minecraftPlayerUUID + ": Player data not found.");
            return;
        }
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : minecraftPlayerUUID.toString();


        if (plugin.getDiscordManager().getJda() == null) {
            plugin.getLogger().warning("JDA not available. Cannot synchronize roles for " + playerName + " (Discord ID: " + discordUserId + ")");
            return;
        }

        String guildId = configManager.getDiscordGuildId();
        if (guildId == null || guildId.isEmpty()) {
            plugin.getLogger().warning("Discord Guild ID not configured. Cannot synchronize roles.");
            return;
        }
        Guild guild = plugin.getDiscordManager().getJda().getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Discord Guild with ID " + guildId + " not found. Cannot synchronize roles.");
            return;
        }

        guild.retrieveMemberById(discordUserId).queue(
            discordMember -> {
                plugin.getLogger().fine("Synchronizing roles for MC: " + playerName + " (UUID: " + minecraftPlayerUUID + ") and Discord: " + discordMember.getUser().getAsTag() + " (ID: " + discordUserId + ")");

                // Synchronize Discord Nickname if feature is enabled
                if (configManager.shouldSynchronizeDiscordNickname()) {
                    // The synchronizeRoles method is already called asynchronously, so this JDA call is fine here.
                    plugin.getDiscordManager().setDiscordNickname(discordUserId, playerName);
                }

                for (RoleMapping mapping : parsedMappings) {
                    plugin.getLogger().fine("Processing mapping: Ingame '" + mapping.ingameGroup() + "' <-> Discord Role '" + mapping.discordRoleName() + "' (ID: " + mapping.discordRoleId() + ") with direction: " + mapping.syncDirection());
                    switch (mapping.syncDirection()) {
                        case "INGAME_TO_DISCORD":
                            syncSingleIngameToDiscord(offlinePlayer, discordMember, guild, mapping);
                            break;
                        case "DISCORD_TO_INGAME":
                            syncSingleDiscordToIngame(offlinePlayer, discordMember, guild, mapping);
                            break;
                        case "BOTH":
                            plugin.getLogger().fine("Sync direction: BOTH for " + mapping.ingameGroup() + ". Syncing In-game -> Discord first.");
                            syncSingleIngameToDiscord(offlinePlayer, discordMember, guild, mapping);
                            plugin.getLogger().fine("Sync direction: BOTH for " + mapping.ingameGroup() + ". Syncing Discord -> In-game second.");
                            syncSingleDiscordToIngame(offlinePlayer, discordMember, guild, mapping);
                            break;
                        default:
                            plugin.getLogger().warning("Unknown sync direction '" + mapping.syncDirection() + "' for mapping '" + mapping.ingameGroup() + "'. Skipping this mapping.");
                    }
                }
            },
            failure -> plugin.getLogger().warning("Could not retrieve Discord member " + discordUserId + " in guild " + guild.getName() + " for role sync: " + failure.getMessage())
        );
    }

    private void syncSingleIngameToDiscord(OfflinePlayer offlinePlayer, Member discordMember, Guild guild, RoleMapping mapping) {
        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        String worldName = (onlinePlayer != null && onlinePlayer.getWorld() != null) ? onlinePlayer.getWorld().getName() : null;

        if (vaultPerms == null) {
            plugin.getLogger().severe("[I2D] Vault permissions not available for mapping '" + mapping.ingameGroup() + "'. Skipping in-game group check for " + offlinePlayer.getName());
            return;
        }

        String primaryGroup = vaultPerms.getPrimaryGroup(worldName, offlinePlayer);
        boolean playerHasIngameGroup = primaryGroup != null && primaryGroup.equalsIgnoreCase(mapping.ingameGroup());
        plugin.getLogger().fine("[I2D] Player " + offlinePlayer.getName() + " primary group: " + (primaryGroup != null ? primaryGroup : "None") + ". Checking against mapped group: " + mapping.ingameGroup() + ". Has mapped group as primary: " + playerHasIngameGroup);

        Role discordRole = guild.getRoleById(mapping.discordRoleId());
        if (discordRole == null) {
            plugin.getLogger().warning("[I2D] Discord role ID " + mapping.discordRoleId() + " (for ingame group " + mapping.ingameGroup() + ") not found in guild " + guild.getName() + ". Skipping this part of sync.");
            return;
        }

        boolean memberHasDiscordRole = discordMember.getRoles().contains(discordRole);

        if (playerHasIngameGroup && !memberHasDiscordRole) {
            try {
                guild.addRoleToMember(discordMember, discordRole).reason("Role Sync: User has in-game group " + mapping.ingameGroup()).queue(
                    success -> plugin.getLogger().fine("[I2D] Added Discord role '" + discordRole.getName() + "' to " + discordMember.getUser().getAsTag() + " because they have in-game group '" + mapping.ingameGroup() + "'."),
                    failure -> plugin.getLogger().log(Level.WARNING, "[I2D] Failed to add Discord role '" + discordRole.getName() + "' to " + discordMember.getUser().getAsTag(), failure)
                );
            } catch (InsufficientPermissionException e) {
                plugin.getLogger().warning("[I2D] Bot lacks permission to add role '" + discordRole.getName() + "' to " + discordMember.getUser().getAsTag() + ".");
            } catch (HierarchyException e) {
                plugin.getLogger().warning("[I2D] Bot cannot add role '" + discordRole.getName() + "' to " + discordMember.getUser().getAsTag() + " due to role hierarchy.");
            }
        } else if (!playerHasIngameGroup && memberHasDiscordRole) {
            try {
                guild.removeRoleFromMember(discordMember, discordRole).reason("Role Sync: User no longer has in-game group " + mapping.ingameGroup()).queue(
                    success -> plugin.getLogger().fine("[I2D] Removed Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " because they no longer have in-game group '" + mapping.ingameGroup() + "'."),
                    failure -> plugin.getLogger().log(Level.WARNING, "[I2D] Failed to remove Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag(), failure)
                );
            } catch (InsufficientPermissionException e) {
                plugin.getLogger().warning("[I2D] Bot lacks permission to remove role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + ".");
            } catch (HierarchyException e) {
                plugin.getLogger().warning("[I2D] Bot cannot remove role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " due to role hierarchy.");
            }
        }
    }

    private void syncSingleDiscordToIngame(OfflinePlayer offlinePlayer, Member discordMember, Guild guild, RoleMapping mapping) {
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString();
        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        String worldName = (onlinePlayer != null && onlinePlayer.getWorld() != null) ? onlinePlayer.getWorld().getName() : null;

        if (vaultPerms == null) {
            plugin.getLogger().severe("[D2I] Vault permissions not available for mapping '" + mapping.ingameGroup() + "'. Skipping in-game group modification for " + playerName);
            return;
        }

        Role discordRole = guild.getRoleById(mapping.discordRoleId());
        if (discordRole == null) {
            plugin.getLogger().warning("[D2I] Discord role ID " + mapping.discordRoleId() + " (for ingame group " + mapping.ingameGroup() + ") not found in guild " + guild.getName() + ". Skipping this part of sync.");
            return;
        }

        boolean memberHasDiscordRole = discordMember.getRoles().contains(discordRole);

        String primaryGroup = vaultPerms.getPrimaryGroup(worldName, offlinePlayer);
        boolean playerHasIngameGroup = primaryGroup != null && primaryGroup.equalsIgnoreCase(mapping.ingameGroup());
        plugin.getLogger().fine("[D2I] Player " + playerName + " primary group: " + (primaryGroup != null ? primaryGroup : "None") + ". Checking against mapped group: " + mapping.ingameGroup() + ". Has mapped group as primary: " + playerHasIngameGroup);

        if (memberHasDiscordRole && !playerHasIngameGroup) {
            plugin.getLogger().fine("[D2I] Granting in-game group '" + mapping.ingameGroup() + "' to " + playerName + " because they have Discord role '" + discordRole.getName() + "'.");
            // Run on main thread for Bukkit API calls that modify player data
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (vaultPerms.playerAddGroup(null, offlinePlayer, mapping.ingameGroup())) { // Using null for world often means global or default world context for Vault
                    plugin.getLogger().fine("[D2I] Successfully added group '" + mapping.ingameGroup() + "' to " + playerName);
                } else {
                    plugin.getLogger().warning("[D2I] Failed to add group '" + mapping.ingameGroup() + "' to " + playerName + ". Check Vault-compatible permissions plugin logs.");
                }
            });

        } else if (!memberHasDiscordRole && playerHasIngameGroup) {
            plugin.getLogger().fine("[D2I] Removing in-game group '" + mapping.ingameGroup() + "' from " + playerName + " because they no longer have Discord role '" + discordRole.getName() + "'.");
            // Run on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (vaultPerms.playerRemoveGroup(null, offlinePlayer, mapping.ingameGroup())) { // Using null for world
                    plugin.getLogger().fine("[D2I] Successfully removed group '" + mapping.ingameGroup() + "' from " + playerName);
                } else {
                    plugin.getLogger().warning("[D2I] Failed to remove group '" + mapping.ingameGroup() + "' from " + playerName + ". Check Vault-compatible permissions plugin logs.");
                }
            });
        }
    }


    public void clearRolesOnUnlink(UUID minecraftPlayerUUID, String discordUserId) {
        plugin.getLogger().fine("Attempting to clear/reset roles for unlinked player MC UUID: " + minecraftPlayerUUID + ", Discord ID: " + discordUserId);
        if (parsedMappings == null || parsedMappings.isEmpty()) {
            plugin.getLogger().fine("No role mappings configured or loaded. Skipping role clearing for " + minecraftPlayerUUID);
            return;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(minecraftPlayerUUID);
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : minecraftPlayerUUID.toString();
        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        String worldName = (onlinePlayer != null && onlinePlayer.getWorld() != null) ? onlinePlayer.getWorld().getName() : null;


        // Clear Discord roles first
        if (plugin.getDiscordManager().getJda() != null) {
            String guildId = configManager.getDiscordGuildId();
            if (guildId != null && !guildId.isEmpty()) {
                Guild guild = plugin.getDiscordManager().getJda().getGuildById(guildId);
                if (guild != null) {
                    guild.retrieveMemberById(discordUserId).queue(
                        discordMember -> {
                            for (RoleMapping mapping : parsedMappings) {
                                Role discordRole = guild.getRoleById(mapping.discordRoleId());
                                if (discordRole != null && discordMember.getRoles().contains(discordRole)) {
                                    try {
                                        guild.removeRoleFromMember(discordMember, discordRole).reason("Role Sync: User unlinked Minecraft account.").queue(
                                            success -> plugin.getLogger().fine("Removed Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " due to unlinking."),
                                            failure -> plugin.getLogger().log(Level.WARNING, "Failed to remove Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " on unlink.", failure)
                                        );
                                    } catch (InsufficientPermissionException e) {
                                        plugin.getLogger().warning("Bot lacks permission to remove role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " on unlink.");
                                    } catch (HierarchyException e) {
                                        plugin.getLogger().warning("Bot cannot remove role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " on unlink due to role hierarchy.");
                                    }
                                }
                            }
                        },
                        failure -> plugin.getLogger().warning("Could not retrieve Discord member " + discordUserId + " for clearing roles on unlink: " + failure.getMessage())
                    );
                } else {
                    plugin.getLogger().warning("Discord Guild with ID " + guildId + " not found. Cannot clear Discord roles.");
                }
            } else {
                plugin.getLogger().warning("Discord Guild ID not configured. Cannot clear Discord roles.");
            }
        } else {
            plugin.getLogger().warning("JDA not available. Cannot clear Discord roles for " + playerName + " (Discord ID: " + discordUserId + ")");
        }

        // In-game group removal upon unlinking is disabled.
    }
}
