package net.gabbage.discordRoleSync.commands.discord;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface IDiscordSubCommand {
    /**
     * Gets the name of the subcommand.
     * @return The name of the subcommand (e.g., "reload", "status").
     */
    String getName();

    /**
     * Gets the permission required to execute this subcommand.
     * @return The permission string, or null if no specific permission is required beyond the base command.
     */
    String getPermission();

    /**
     * Executes the subcommand.
     * @param plugin The main plugin instance.
     * @param sender The command sender.
     * @param args The arguments passed to the subcommand (excluding the subcommand name itself).
     */
    void execute(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args);

    /**
     * Provides tab completion suggestions for this subcommand.
     * @param plugin The main plugin instance.
     * @param sender The command sender.
     * @param args The arguments passed to the subcommand (excluding the subcommand name itself).
     * @return A list of tab completion suggestions.
     */
    List<String> onTabComplete(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args);
}
