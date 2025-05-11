package net.gabbage.discordRoleSync.service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
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

    private record RoleMapping(String ingameGroup, String discordRoleId, String discordRoleName) {}
    private final List<RoleMapping> parsedMappings; // Made non-final, populated by loadAndParseRoleMappings

    public RoleSyncService(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.vaultPerms = DiscordRoleSync.getVaultPermissions(); // Get Vault instance
        this.parsedMappings = new ArrayList<>(); // Initialize as empty
    }

    public void loadAndParseRoleMappings() {
        List<RoleMapping> mappings = new ArrayList<>();
        List<String> configuredMappings = configManager.getRoleMappings();
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


        for (String mappingStr : configuredMappings) {
            String[] parts = mappingStr.split(":", 2);
            if (parts.length == 2) {
                String ingameGroup = parts[0].trim();
                String discordRoleIdStr = parts[1].trim();
                String discordRoleName = "Unknown Role (ID: " + discordRoleIdStr + ")";

                if (guild != null) {
                    try {
                        Role discordRole = guild.getRoleById(discordRoleIdStr);
                        if (discordRole != null) {
                            discordRoleName = discordRole.getName();
                        } else {
                            plugin.getLogger().warning("Could not find Discord role with ID: " + discordRoleIdStr + " in guild " + guild.getName() + " for mapping: " + mappingStr);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid Discord Role ID format: " + discordRoleIdStr + " in mapping: " + mappingStr);
                        continue;
                    }
                } else {
                     // This case is covered by the JDA/guild availability checks at the start of the method.
                     // If guild is null here, it means either JDA was unavailable, guildId was empty, or guild wasn't found.
                     // The role name will remain "Unknown Role (ID: ...)".
                }

                mappings.add(new RoleMapping(ingameGroup, discordRoleIdStr, discordRoleName));
            } else {
                plugin.getLogger().warning("Invalid role mapping format: " + mappingStr + ". Expected 'ingamegroup:discordroleid'.");
            }
        }

        this.parsedMappings.clear();
        this.parsedMappings.addAll(mappings);
        plugin.getLogger().info("Loaded " + this.parsedMappings.size() + " role mappings.");
        if (this.parsedMappings.isEmpty() && !configuredMappings.isEmpty()) {
            plugin.getLogger().warning("No valid role mappings were parsed, but " + configuredMappings.size() + " mappings were found in config. Please check their format.");
        }
    }

    public void synchronizeRoles(UUID minecraftPlayerUUID, String discordUserId) {
        if (parsedMappings.isEmpty()) {
            plugin.getLogger().fine("No role mappings configured. Skipping synchronization for " + minecraftPlayerUUID);
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
                plugin.getLogger().info("Synchronizing roles for MC: " + playerName + " (UUID: " + minecraftPlayerUUID + ") and Discord: " + discordMember.getUser().getAsTag() + " (ID: " + discordUserId + ")");
                String syncDirection = configManager.getSyncDirection().toUpperCase();

                switch (syncDirection) {
                    case "INGAME_TO_DISCORD":
                        syncIngameToDiscord(offlinePlayer, discordMember, guild);
                        break;
                    case "DISCORD_TO_INGAME":
                        syncDiscordToIngame(offlinePlayer, discordMember, guild);
                        break;
                    case "BOTH":
                        plugin.getLogger().fine("Sync direction: BOTH. Syncing In-game -> Discord first for " + playerName);
                        syncIngameToDiscord(offlinePlayer, discordMember, guild);
                        plugin.getLogger().fine("Sync direction: BOTH. Syncing Discord -> In-game second for " + playerName);
                        syncDiscordToIngame(offlinePlayer, discordMember, guild);
                        break;
                    default:
                        plugin.getLogger().warning("Unknown sync direction: " + syncDirection + ". Defaulting to no sync.");
                }
            },
            failure -> plugin.getLogger().warning("Could not retrieve Discord member " + discordUserId + " in guild " + guild.getName() + " for role sync: " + failure.getMessage())
        );
    }

    private void syncIngameToDiscord(OfflinePlayer offlinePlayer, Member discordMember, Guild guild) {
        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        String worldName = (onlinePlayer != null && onlinePlayer.getWorld() != null) ? onlinePlayer.getWorld().getName() : null; // Get world name if player is online, can be null for some offline Vault operations

        if (vaultPerms == null) {
            plugin.getLogger().severe("[I2D] Vault permissions not available. Skipping in-game group check for " + offlinePlayer.getName());
            return;
        }

        for (RoleMapping mapping : parsedMappings) {
            boolean playerHasIngameGroup = vaultPerms.playerInGroup(worldName, offlinePlayer, mapping.ingameGroup());
            plugin.getLogger().fine("[I2D] Player " + offlinePlayer.getName() + " in group " + mapping.ingameGroup() + " (World context: " + worldName + "): " + playerHasIngameGroup);

            Role discordRole = guild.getRoleById(mapping.discordRoleId());
            if (discordRole == null) {
                plugin.getLogger().warning("[I2D] Discord role ID " + mapping.discordRoleId() + " (for ingame group " + mapping.ingameGroup() + ") not found in guild " + guild.getName() + ". Skipping.");
                continue;
            }

            boolean memberHasDiscordRole = discordMember.getRoles().contains(discordRole);

            if (playerHasIngameGroup && !memberHasDiscordRole) {
                try {
                    guild.addRoleToMember(discordMember, discordRole).reason("Role Sync: User has in-game group " + mapping.ingameGroup()).queue(
                        success -> plugin.getLogger().info("[I2D] Added Discord role '" + discordRole.getName() + "' to " + discordMember.getUser().getAsTag() + " because they have in-game group '" + mapping.ingameGroup() + "'."),
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
                        success -> plugin.getLogger().info("[I2D] Removed Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " because they no longer have in-game group '" + mapping.ingameGroup() + "'."),
                        failure -> plugin.getLogger().log(Level.WARNING, "[I2D] Failed to remove Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag(), failure)
                    );
                } catch (InsufficientPermissionException e) {
                    plugin.getLogger().warning("[I2D] Bot lacks permission to remove role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + ".");
                } catch (HierarchyException e) {
                    plugin.getLogger().warning("[I2D] Bot cannot remove role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " due to role hierarchy.");
                }
            }
        }
    }

    private void syncDiscordToIngame(OfflinePlayer offlinePlayer, Member discordMember, Guild guild) {
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString();
        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        String worldName = (onlinePlayer != null && onlinePlayer.getWorld() != null) ? onlinePlayer.getWorld().getName() : null;

        if (vaultPerms == null) {
            plugin.getLogger().severe("[D2I] Vault permissions not available. Skipping in-game group modification for " + playerName);
            return;
        }

        for (RoleMapping mapping : parsedMappings) {
            Role discordRole = guild.getRoleById(mapping.discordRoleId());
            if (discordRole == null) {
                plugin.getLogger().warning("[D2I] Discord role ID " + mapping.discordRoleId() + " (for ingame group " + mapping.ingameGroup() + ") not found in guild " + guild.getName() + ". Skipping.");
                continue;
            }

            boolean memberHasDiscordRole = discordMember.getRoles().contains(discordRole);
            boolean playerHasIngameGroup = vaultPerms.playerInGroup(worldName, offlinePlayer, mapping.ingameGroup());
            plugin.getLogger().fine("[D2I] Player " + playerName + " in group " + mapping.ingameGroup() + " (World context: " + worldName + "): " + playerHasIngameGroup);


            if (memberHasDiscordRole && !playerHasIngameGroup) {
                plugin.getLogger().info("[D2I] Granting in-game group '" + mapping.ingameGroup() + "' to " + playerName + " because they have Discord role '" + discordRole.getName() + "'.");
                // Run on main thread for Bukkit API calls that modify player data
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (vaultPerms.playerAddGroup(null, offlinePlayer, mapping.ingameGroup())) { // Using null for world often means global or default world context for Vault
                        plugin.getLogger().info("[D2I] Successfully added group '" + mapping.ingameGroup() + "' to " + playerName);
                    } else {
                        plugin.getLogger().warning("[D2I] Failed to add group '" + mapping.ingameGroup() + "' to " + playerName + ". Check Vault-compatible permissions plugin logs.");
                    }
                });

            } else if (!memberHasDiscordRole && playerHasIngameGroup) {
                plugin.getLogger().info("[D2I] Removing in-game group '" + mapping.ingameGroup() + "' from " + playerName + " because they no longer have Discord role '" + discordRole.getName() + "'.");
                // Run on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (vaultPerms.playerRemoveGroup(null, offlinePlayer, mapping.ingameGroup())) { // Using null for world
                        plugin.getLogger().info("[D2I] Successfully removed group '" + mapping.ingameGroup() + "' from " + playerName);
                    } else {
                        plugin.getLogger().warning("[D2I] Failed to remove group '" + mapping.ingameGroup() + "' from " + playerName + ". Check Vault-compatible permissions plugin logs.");
                    }
                });
            }
        }
    }


    public void clearRolesOnUnlink(UUID minecraftPlayerUUID, String discordUserId) {
        plugin.getLogger().info("Attempting to clear/reset roles for unlinked player MC UUID: " + minecraftPlayerUUID + ", Discord ID: " + discordUserId);
        if (parsedMappings.isEmpty()) {
            plugin.getLogger().fine("No role mappings configured. Skipping role clearing for " + minecraftPlayerUUID);
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
                                            success -> plugin.getLogger().info("Removed Discord role '" + discordRole.getName() + "' from " + discordMember.getUser().getAsTag() + " due to unlinking."),
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

        // Clear In-Game roles
        if (vaultPerms == null) {
            plugin.getLogger().severe("Vault permissions not available. Skipping in-game role clearing for " + playerName);
            return;
        }

        for (RoleMapping mapping : parsedMappings) {
            // We attempt to remove the group if it was part of the mappings,
            // as we assume it might have been added by this plugin.
            // Check if player is in group before attempting removal.
            if (vaultPerms.playerInGroup(worldName, offlinePlayer, mapping.ingameGroup())) {
                plugin.getLogger().info("Removing in-game group '" + mapping.ingameGroup() + "' from " + playerName + " due to unlinking.");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (vaultPerms.playerRemoveGroup(null, offlinePlayer, mapping.ingameGroup())) { // Using null for world
                        plugin.getLogger().info("Successfully removed group '" + mapping.ingameGroup() + "' from " + playerName + " on unlink.");
                    } else {
                        plugin.getLogger().warning("Failed to remove group '" + mapping.ingameGroup() + "' from " + playerName + " on unlink. Check Vault-compatible permissions plugin logs.");
                    }
                });
            }
        }
    }
}
