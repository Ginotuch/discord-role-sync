package net.gabbage.discordRoleSync.storage;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class LinkedPlayersManager {

    private final DiscordRoleSync plugin;
    private final File linkedPlayersFile;
    private FileConfiguration linkedPlayersConfig;
    private final Map<UUID, String> mcToDiscordLinks; // Minecraft UUID -> Discord ID
    private final Map<String, UUID> discordToMcLinks; // Discord ID -> Minecraft UUID

    public LinkedPlayersManager(DiscordRoleSync plugin) {
        this.plugin = plugin;
        this.linkedPlayersFile = new File(plugin.getDataFolder(), "linked_players.yml");
        this.mcToDiscordLinks = new HashMap<>();
        this.discordToMcLinks = new HashMap<>();
        loadLinkedPlayers();
    }

    public void loadLinkedPlayers() {
        if (!linkedPlayersFile.exists()) {
            try {
                // Ensure the plugin's data folder exists
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                // Create an empty linked_players.yml if it doesn't exist
                if (linkedPlayersFile.createNewFile()) {
                    plugin.getLogger().info("Created new empty linked_players.yml file.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create linked_players.yml file.", e);
            }
        }
        linkedPlayersConfig = YamlConfiguration.loadConfiguration(linkedPlayersFile);
        mcToDiscordLinks.clear();
        discordToMcLinks.clear();

        ConfigurationSection linksSection = linkedPlayersConfig.getConfigurationSection("links");
        if (linksSection != null) {
            for (String mcUUIDStr : linksSection.getKeys(false)) {
                try {
                    UUID mcUUID = UUID.fromString(mcUUIDStr);
                    String discordId = linksSection.getString(mcUUIDStr);
                    if (discordId != null && !discordId.isEmpty()) {
                        mcToDiscordLinks.put(mcUUID, discordId);
                        discordToMcLinks.put(discordId, mcUUID);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid Minecraft UUID found in linked_players.yml: " + mcUUIDStr);
                }
            }
        }
        plugin.getLogger().info("Loaded " + mcToDiscordLinks.size() + " player links.");
    }

    public void saveLinkedPlayers() {
        // Create a fresh config to avoid keeping old removed entries if we only set new ones
        linkedPlayersConfig = new YamlConfiguration();
        ConfigurationSection linksSection = linkedPlayersConfig.createSection("links");
        for (Map.Entry<UUID, String> entry : mcToDiscordLinks.entrySet()) {
            linksSection.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            linkedPlayersConfig.save(linkedPlayersFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save linked players to " + linkedPlayersFile, e);
        }
    }

    public void addLink(UUID mcUUID, String discordId) {
        // Remove any existing links for this mcUUID or discordId to prevent duplicates/conflicts
        removeLinkByMcUUID(mcUUID);
        removeLinkByDiscordId(discordId);

        mcToDiscordLinks.put(mcUUID, discordId);
        discordToMcLinks.put(discordId, mcUUID);
        saveLinkedPlayers();
    }

    public void removeLinkByMcUUID(UUID mcUUID) {
        String discordId = mcToDiscordLinks.remove(mcUUID);
        if (discordId != null) {
            discordToMcLinks.remove(discordId);
            saveLinkedPlayers();
        }
    }

    public void removeLinkByDiscordId(String discordId) {
        UUID mcUUID = discordToMcLinks.remove(discordId);
        if (mcUUID != null) {
            mcToDiscordLinks.remove(mcUUID);
            saveLinkedPlayers();
        }
    }

    public String getDiscordId(UUID mcUUID) {
        return mcToDiscordLinks.get(mcUUID);
    }

    public UUID getMcUUID(String discordId) {
        return discordToMcLinks.get(discordId);
    }

    public boolean isMcAccountLinked(UUID mcUUID) {
        return mcToDiscordLinks.containsKey(mcUUID);
    }

    public boolean isDiscordAccountLinked(String discordId) {
        return discordToMcLinks.containsKey(discordId);
    }

    public Map<UUID, String> getAllLinks() {
        return new HashMap<>(mcToDiscordLinks); // Return a copy
    }
}
