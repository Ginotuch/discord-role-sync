package net.gabbage.discordRoleSync.service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RoleSyncService {

    private final DiscordRoleSync plugin;
    private final ConfigManager configManager;

    private record RoleMapping(String ingameGroup, String discordRoleId, String discordRoleName) {}
    private final List<RoleMapping> parsedMappings;

    public RoleSyncService(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.parsedMappings = parseRoleMappings();
    }

    private List<RoleMapping> parseRoleMappings() {
        List<RoleMapping> mappings = new ArrayList<>();
        List<String> configuredMappings = configManager.getRoleMappings();
        Guild guild = null;
        String guildId = configManager.getDiscordGuildId();
        if (plugin.getDiscordManager().getJda() != null && guildId != null && !guildId.isEmpty()) {
            guild = plugin.getDiscordManager().getJda().getGuildById(guildId);
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
                } else if (plugin.getDiscordManager().getJda() != null) {
                     plugin.getLogger().warning("Discord Guild ID not configured or guild not found. Cannot verify Discord role names for mapping: " + mappingStr);
                }


                mappings.add(new RoleMapping(ingameGroup, discordRoleIdStr, discordRoleName));
            } else {
                plugin.getLogger().warning("Invalid role mapping format: " + mappingStr + ". Expected 'ingamegroup:discordroleid'.");
            }
        }
        plugin.getLogger().info("Loaded " + mappings.size() + " role mappings.");
        return mappings;
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

        for (RoleMapping mapping : parsedMappings) {
            // Placeholder for checking in-game group. Assumes Vault or similar.
            // For Vault: net.milkbowl.vault.permission.Permission perms = ...; perms.playerInGroup(player, mapping.ingameGroup());
            boolean playerHasIngameGroup;
            if (onlinePlayer != null) {
                // This is a basic permission check, real group checks are more complex via Vault/LuckPerms API
                playerHasIngameGroup = onlinePlayer.hasPermission("group." + mapping.ingameGroup());
                plugin.getLogger().fine("[I2D] Player " + onlinePlayer.getName() + " hasPermission(group." + mapping.ingameGroup() + "): " + playerHasIngameGroup);
            } else {
                // Offline player group checking is more complex and highly dependent on the permissions plugin.
                // For now, we'll log and skip if player is offline for this part of the sync.
                plugin.getLogger().warning("[I2D] Player " + offlinePlayer.getName() + " is offline. Accurate in-game group check for '" + mapping.ingameGroup() + "' requires specific offline permission plugin support (e.g., LuckPerms API). Skipping Discord role update for this mapping.");
                continue;
            }

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

        for (RoleMapping mapping : parsedMappings) {
            Role discordRole = guild.getRoleById(mapping.discordRoleId());
            if (discordRole == null) {
                plugin.getLogger().warning("[D2I] Discord role ID " + mapping.discordRoleId() + " (for ingame group " + mapping.ingameGroup() + ") not found in guild " + guild.getName() + ". Skipping.");
                continue;
            }

            boolean memberHasDiscordRole = discordMember.getRoles().contains(discordRole);
            // Placeholder for checking in-game group.
            boolean playerHasIngameGroup = false;
            if (onlinePlayer != null) {
                playerHasIngameGroup = onlinePlayer.hasPermission("group." + mapping.ingameGroup());
                 plugin.getLogger().fine("[D2I] Player " + onlinePlayer.getName() + " hasPermission(group." + mapping.ingameGroup() + "): " + playerHasIngameGroup);
            } else {
                // Offline group checking is complex. For now, assume false if offline for this check.
                // A real implementation would query the permissions plugin's API for offline players.
                plugin.getLogger().fine("[D2I] Player " + playerName + " is offline. Assuming does not have group '" + mapping.ingameGroup() + "' for this check unless specific offline support is added.");
            }


            if (memberHasDiscordRole && !playerHasIngameGroup) {
                // Placeholder for adding in-game group. Assumes Vault or specific perm API.
                // Example: Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " parent add " + mapping.ingameGroup());
                plugin.getLogger().info("[D2I] Granting in-game group '" + mapping.ingameGroup() + "' to " + playerName + " because they have Discord role '" + discordRole.getName() + "'. (Permissions command placeholder)");
                // TODO: Implement actual command dispatch or API call to permissions plugin
                // Ensure this runs on the main server thread if it modifies player data or calls Bukkit API
                final String commandToAdd = "lp user " + playerName + " parent add " + mapping.ingameGroup(); // Example for LuckPerms
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToAdd);
                     plugin.getLogger().info("[D2I] Dispatched command: '" + commandToAdd + "'. Success: " + success);
                });


            } else if (!memberHasDiscordRole && playerHasIngameGroup) {
                // Placeholder for removing in-game group.
                // Example: Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " parent remove " + mapping.ingameGroup());
                plugin.getLogger().info("[D2I] Removing in-game group '" + mapping.ingameGroup() + "' from " + playerName + " because they no longer have Discord role '" + discordRole.getName() + "'. (Permissions command placeholder)");
                // TODO: Implement actual command dispatch or API call to permissions plugin
                final String commandToRemove = "lp user " + playerName + " parent remove " + mapping.ingameGroup(); // Example for LuckPerms
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRemove);
                    plugin.getLogger().info("[D2I] Dispatched command: '" + commandToRemove + "'. Success: " + success);
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

        if (plugin.getDiscordManager().getJda() == null) {
            plugin.getLogger().warning("JDA not available. Cannot clear Discord roles for " + playerName + " (Discord ID: " + discordUserId + ")");
            return; // Cannot clear Discord roles if JDA is down
        }
        String guildId = configManager.getDiscordGuildId();
        if (guildId == null || guildId.isEmpty()) {
            plugin.getLogger().warning("Discord Guild ID not configured. Cannot clear Discord roles.");
            return;
        }
        Guild guild = plugin.getDiscordManager().getJda().getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Discord Guild with ID " + guildId + " not found. Cannot clear Discord roles.");
            return;
        }

        // Clear Discord roles
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

        // Clear In-Game roles (placeholder)
        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        for (RoleMapping mapping : parsedMappings) {
            boolean playerHadIngameGroup = false; // Assume they might have had it
            if (onlinePlayer != null) {
                // This is a basic permission check, real group checks are more complex
                playerHadIngameGroup = onlinePlayer.hasPermission("group." + mapping.ingameGroup());
            } else {
                // Offline check is complex, for unlinking, we might just try to remove if it was a synced role.
                // For safety, only remove if we are sure it was a synced role.
                // This part needs careful consideration with the chosen permissions plugin.
                // For now, we'll assume if it's in mappings, it *could* have been synced.
                playerHadIngameGroup = true; // Tentatively assume true for offline to attempt removal
                plugin.getLogger().fine("Player " + playerName + " is offline. Assuming they might have group '" + mapping.ingameGroup() + "' for unlink cleanup.");
            }

            if (playerHadIngameGroup) {
                plugin.getLogger().info("Removing in-game group '" + mapping.ingameGroup() + "' from " + playerName + " due to unlinking. (Permissions command placeholder)");
                // TODO: Implement actual command dispatch or API call to permissions plugin
                final String commandToRemove = "lp user " + playerName + " parent remove " + mapping.ingameGroup(); // Example for LuckPerms
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRemove);
                    plugin.getLogger().info("Dispatched unlink command: '" + commandToRemove + "'. Success: " + success);
                });
            }
        }
    }
}
