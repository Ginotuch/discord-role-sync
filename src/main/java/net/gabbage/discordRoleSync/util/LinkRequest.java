package net.gabbage.discordRoleSync.util;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public class LinkRequest {
    private final String discordUserId;
    private final String discordUserName; // Discord global name or username
    private final String discordUserDiscriminator; // Discord discriminator (e.g., #0001 or empty if new username system)
    private final UUID minecraftPlayerUUID;
    private final long requestTimeMillis;
    private final String confirmationCode;

    public LinkRequest(String discordUserId, String discordUserName, String discordUserDiscriminator, UUID minecraftPlayerUUID) {
        this.discordUserId = discordUserId;
        this.discordUserName = discordUserName;
        this.discordUserDiscriminator = discordUserDiscriminator;
        this.minecraftPlayerUUID = minecraftPlayerUUID;
        this.requestTimeMillis = System.currentTimeMillis();
        this.confirmationCode = generateConfirmationCode();
    }

    private String generateConfirmationCode() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[6]; // 6 bytes will give 8 Base64 characters
        random.nextBytes(bytes);
        // Using URL and Filename safe Base64 encoder to avoid '+' and '/' characters if the code is ever used in URLs.
        // Removing padding '=' as it's not needed for a short code.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 6).toUpperCase(); // Take first 6 chars, uppercase
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public String getDiscordUserId() {
        return discordUserId;
    }

    public String getDiscordUserName() {
        return discordUserName;
    }

    public String getDiscordUserDiscriminator() {
        return discordUserDiscriminator;
    }

    public String getFullDiscordName() {
        if (discordUserDiscriminator == null || discordUserDiscriminator.isEmpty() || "0".equals(discordUserDiscriminator) || "0000".equals(discordUserDiscriminator)) {
            return discordUserName; // New username system
        }
        return discordUserName + "#" + discordUserDiscriminator;
    }

    public UUID getMinecraftPlayerUUID() {
        return minecraftPlayerUUID;
    }

    public long getRequestTimeMillis() {
        return requestTimeMillis;
    }

    // Optional: check if request expired
    public boolean isExpired(long timeoutMillis) {
        return (System.currentTimeMillis() - requestTimeMillis) > timeoutMillis;
    }
}
