# DiscordRoleSync

DiscordRoleSync is a Spigot/Paper plugin designed to synchronize in-game Minecraft player groups (via Vault) with Discord roles. It allows players to link their Minecraft and Discord accounts, and based on configurable mappings, their roles can be kept in sync between the two platforms. The plugin also supports Discord nickname synchronization with Minecraft usernames and provides several administrative commands.

## Features

*   **Account Linking**: Players can link their Minecraft account to their Discord account.
    *   Initiate linking from Discord using a slash command (`/link <minecraft_username>`).
    *   Confirm linking in-game using `/link <CODE>`.
    *   Deny pending link requests in-game using `/denylink`.
*   **Role Synchronization**:
    *   Syncs roles based on the player's **primary in-game group**.
    *   Configurable synchronization direction:
        *   `INGAME_TO_DISCORD`: In-game group grants Discord role.
        *   `DISCORD_TO_INGAME`: Discord role grants in-game group.
        *   `BOTH`: Synchronization occurs in both directions.
    *   Periodic synchronization to ensure roles are up-to-date.
    *   Synchronization on player join.
*   **Discord Nickname Synchronization**:
    *   Optionally sets a player's Discord server nickname to their Minecraft username upon linking.
    *   Updates the Discord nickname if it differs when a linked player joins the server or during periodic sync.
    *   Resets the Discord nickname (removes it) when accounts are unlinked.
    *   Requires the bot to have "Manage Nicknames" permission in Discord.
*   **Default Role Assignment on Link**:
    *   Optionally assign a specific in-game group to players when they first link their accounts, but only if their current primary group is one of a configurable list (e.g., "default", "guest").
*   **Commands**:
    *   Player commands for linking, unlinking, denying links, and checking status.
    *   Admin commands for reloading the plugin, inspecting player links, and manually linking accounts.
*   **Configurable Messages**: All player-facing messages are configurable via `messages.yml`, with support for color codes.
*   **Message File Auto-Update**: `messages.yml` can automatically update to include new default messages from newer plugin versions, while backing up the user's existing file (unless explicitly disabled).

## Prerequisites

*   A Spigot/Paper Minecraft server.
*   [Vault](https://www.spigotmc.org/resources/vault.34315/) plugin installed.
*   A Vault-compatible permissions plugin (e.g., LuckPerms).
*   A Discord Bot application and its token.

## Setup and Configuration

1.  **Download** the `DiscordRoleSync.jar` file and place it into your server's `plugins` folder.
2.  **Start** your Minecraft server once to generate the default configuration files (`config.yml` and `messages.yml`) in the `plugins/DiscordRoleSync/` directory.
3.  **Configure your Discord Bot**:
    *   Go to the [Discord Developer Portal](https://discord.com/developers/applications).
    *   Create a new application or use an existing one.
    *   Navigate to the "Bot" tab for your application.
    *   **Copy the Bot Token**.
    *   Enable **Privileged Gateway Intents**:
        *   **Server Members Intent**: Required for the bot to see guild members and their roles.
        *   (Optional) **Message Content Intent**: Only if you plan to extend the bot to read message content beyond slash commands. Not strictly needed for current features.
    *   Ensure your bot is invited to your Discord server with necessary permissions:
        *   **Manage Roles**: Essential for adding/removing roles.
        *   **Manage Nicknames**: Required if `synchronize-discord-nickname` is enabled.
        *   **View Channels** & **Send Messages**: For slash command interaction.
4.  **Edit `config.yml`**:
    *   `discord.bot-token`: Paste your Discord Bot Token here.
    *   `discord.guild-id`: (Recommended) Your Discord Server/Guild ID. This allows slash commands to register instantly for your server. If left empty, commands register globally and can take up to an hour to appear.
    *   `discord.invite-link`: (Optional) The invite link for your Discord server, displayed by the `/discord` command.
    *   `sync.interval-minutes`: How often (in minutes) to run the periodic role synchronization. Set to `0` to disable.
    *   `linking.request-timeout-minutes`: How long a link request code is valid.
    *   `linking.synchronize-discord-nickname`: Set to `true` to enable Discord nickname syncing, `false` to disable.
    *   `linking.default-role-assignment`:
        *   `enabled`: `true` or `false` to enable/disable assigning a default role on link.
        *   `if-in-groups`: A list of current primary group names (lowercase) that will trigger the assignment if the player is in one of them when they link.
        *   `assign-group`: The in-game group to assign if the conditions are met.
    *   `roles.mappings`: Define your role synchronization rules here.
        *   `ingame`: The name of the in-game group (case-sensitive, as defined in your permissions plugin).
        *   `discord`: The ID of the corresponding Discord role.
        *   `direction`:
            *   `INGAME_TO_DISCORD`: In-game group membership adds/removes the Discord role.
            *   `DISCORD_TO_INGAME`: Discord role membership adds/removes the in-game group.
            *   `BOTH`: Synchronization occurs in both directions (in-game to Discord, then Discord to in-game).
        ```yaml
        # Example Role Mappings:
        roles:
          mappings:
            - ingame: "member"
              discord: "123456789012345678" # Replace with your 'Member' Discord Role ID
              direction: "BOTH"
            - ingame: "vip"
              discord: "876543210987654321" # Replace with your 'VIP' Discord Role ID
              direction: "BOTH"
            - ingame: "staff"
              discord: "112233445566778899"
              direction: "INGAME_TO_DISCORD" # Example: Staff role only syncs from game to Discord
        ```
5.  **Edit `messages.yml`**:
    *   This file contains all user-facing messages. You can customize them using standard Minecraft color codes (`&`).
    *   **Auto-Update**: The `_messages_version` field is used by the plugin. If a newer version of the plugin includes updated default messages, and your `_messages_version` is lower (and `_messages_version_do_not_overwrite` is `false`), your current `messages.yml` will be backed up (e.g., to `messages.yml.old_version_X`) and replaced with the new defaults.
    *   Set `_messages_version_do_not_overwrite: true` if you want to manually manage message updates and prevent automatic overwriting.
6.  **Restart/Reload**: After configuring, restart your server or use `/discord reload` (if you have permission).

## Usage

### Player Commands

*   **Linking Account (from Discord)**:
    1.  In your Discord server, type `/link <your_minecraft_username>`.
    2.  The bot will reply, and if your Minecraft account is found online, you will receive a message in-game.
*   **Linking Account (in-game confirmation)**:
    1.  After initiating the link from Discord, you'll see a message in Minecraft like:
        `[DISCORD] Request received to link to <DiscordUser#Tag>. Click [HERE] or type /link <CODE> to link, or click [DENY] to reject.`
    2.  Click `[HERE]` or the `/link <CODE>` part, or manually type `/link <CODE>` (where `<CODE>` is the unique code provided in the message).
*   `/link <CODE>`: Confirms a pending link request using the provided code.
    *   Permission: `discordrolesync.link` (default: true)
*   `/unlink`: Unlinks your currently linked Minecraft and Discord accounts.
    *   Permission: `discordrolesync.unlink` (default: true)
*   `/denylink`: Denies an incoming link request.
    *   Permission: `discordrolesync.denylink` (default: true)
*   `/discord`: Shows your current link status, the server's Discord invite link (if configured), and available actions.
    *   Permission: `discordrolesync.discord` (default: true)

### Admin Commands (Subcommands of `/discord`)

*   `/discord reload`: Reloads the plugin's configuration files (`config.yml` and `messages.yml`) and re-initializes the plugin.
    *   Permission: `discordrolesync.reload` (default: op)
*   `/discord inspect <minecraft_username>`: Shows the link status for the specified Minecraft player, including their linked Discord ID and tag (if retrievable).
    *   Permission: `discordrolesync.inspect` (default: op)
    *   Tab completion is available for online player usernames.
*   `/discord manuallink <minecraft_username> <discord_id>`: Manually links a Minecraft account to a Discord ID. This bypasses the normal confirmation process.
    *   Permission: `discordrolesync.manuallink` (default: op)
    *   Tab completion is available for online player usernames.

## Permissions

*   `discordrolesync.link`: Allows use of the `/link <CODE>` command to confirm a link. (Default: `true`)
*   `discordrolesync.unlink`: Allows use of the `/unlink` command. (Default: `true`)
*   `discordrolesync.denylink`: Allows use of the `/denylink` command. (Default: `true`)
*   `discordrolesync.discord`: Allows use of the base `/discord` command to view status and invite. (Default: `true`)
*   `discordrolesync.reload`: Allows use of the `/discord reload` subcommand. (Default: `op`)
*   `discordrolesync.inspect`: Allows use of the `/discord inspect <player>` subcommand. (Default: `op`)
*   `discordrolesync.manuallink`: Allows use of the `/discord manuallink <player> <discord_id>` subcommand. (Default: `op`)

## How Role Synchronization Works

*   **Primary Group Based**: Synchronization is based on the player's **primary group** as determined by Vault. If a player's primary group matches an `ingame` group in a mapping, that mapping is considered active for them. Inheritance is not directly considered for activating a mapping; only the primary group matters.
*   **Directions**:
    *   `INGAME_TO_DISCORD`: If a player's primary in-game group is mapped, the corresponding Discord role is added. If they lose that primary group, the Discord role is removed.
    *   `DISCORD_TO_INGAME`: If a player has a mapped Discord role, their primary in-game group is set to the corresponding mapped group. If they lose the Discord role, their primary in-game group is set to the mapped group (this effectively means the plugin will try to remove them from the mapped group, and your permissions plugin will likely revert them to a default group).
    *   `BOTH`:
        1.  First, `INGAME_TO_DISCORD` logic applies.
        2.  Then, `DISCORD_TO_INGAME` logic applies. This can be useful for ensuring consistency if changes happen on either platform.
*   **Events Triggering Sync**:
    *   Player join.
    *   Successful account linking.
    *   Periodic sync task (configurable interval).
    *   Manual linking by an admin.
*   **Unlinking**: When accounts are unlinked:
    *   Mapped Discord roles are removed from the Discord user.
    *   In-game groups are **not** removed (as per current configuration).
    *   The synchronized Discord nickname is removed.

## Troubleshooting

*   **"Discord Bot Token is not configured..."**: Ensure your bot token is correctly pasted into `config.yml`.
*   **"JDA connection failed..." / Disallowed Intents (Error 4014)**:
    *   Verify your bot token is correct.
    *   Ensure you have enabled "Server Members Intent" (and any other necessary privileged intents) in your Discord Bot's settings on the Discord Developer Portal.
    *   Check that your server's firewall is not blocking outbound connections for JDA.
*   **"Could not find Discord guild with ID..."**:
    *   Ensure the `guild-id` in `config.yml` is correct.
    *   Make sure your bot has been invited to and is a member of that Discord server.
*   **Roles not syncing / Nicknames not changing**:
    *   Check bot permissions in Discord: "Manage Roles" and "Manage Nicknames".
    *   Check role hierarchy: The bot's highest role in Discord must be above the roles it needs to manage and above the highest role of members whose nicknames it needs to change.
    *   Verify your `roles.mappings` in `config.yml` are correct (in-game group names are case-sensitive, Discord role IDs are correct).
    *   Check server console logs for errors from DiscordRoleSync or Vault.
*   **"Message not found: ..."**:
    *   This usually means a message key is missing from your `messages.yml`. If you've recently updated the plugin, ensure your `messages.yml` is up-to-date. If `_messages_version_do_not_overwrite` is `false`, the plugin should attempt to update it. Otherwise, you may need to manually add new message keys from the plugin's default `messages.yml`.
*   **Commands not appearing in Discord**:
    *   If using a `guild-id`, ensure it's correct and the bot is in the guild.
    *   If not using a `guild-id` (global commands), it can take up to an hour for Discord to propagate them.
    *   Ensure the bot has the "Use Slash Commands" permission in the relevant channels/server.

## Building from Source

If you wish to build the plugin from source:
1.  Clone the repository.
2.  Ensure you have JDK (Java Development Kit) 17 or higher installed.
3.  Run `./gradlew shadowJar` (Linux/macOS) or `gradlew.bat shadowJar` (Windows) in the root directory of the project.
4.  The compiled JAR will be located in `build/libs/`.

---

This README provides a comprehensive guide to setting up, configuring, and using the DiscordRoleSync plugin.
