package net.gabbage.discordRoleSync.commands.discord;

import net.gabbage.discordRoleSync.DiscordRoleSync;
import net.gabbage.discordRoleSync.managers.ConfigManager;
import net.gabbage.discordRoleSync.service.RoleSyncService;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class RolesSubCommand implements IDiscordSubCommand {

    @Override
    public String getName() {
        return "roles";
    }

    @Override
    public String getPermission() {
        return "discordrolesync.roles";
    }

    @Override
    public void execute(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        ConfigManager configManager = plugin.getConfigManager();

        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage(configManager.getMessage("roles.no_permission"));
            return;
        }

        RoleSyncService roleSyncService = plugin.getRoleSyncService();
        List<RoleSyncService.RoleMapping> mappings = roleSyncService.getParsedMappings();

        sender.sendMessage(configManager.getMessage("roles.header"));

        if (mappings.isEmpty()) {
            sender.sendMessage(configManager.getMessage("roles.no_mappings"));
        } else {
            for (RoleSyncService.RoleMapping mapping : mappings) {
                sender.sendMessage(configManager.getMessage("roles.mapping_entry",
                        "%ingame_group%", mapping.ingameGroup(),
                        "%discord_role_name%", mapping.discordRoleName(),
                        "%discord_role_id%", mapping.discordRoleId(),
                        "%direction%", mapping.syncDirection()
                ));
            }
        }
        sender.sendMessage(configManager.getMessage("roles.footer")); // Using generic footer
    }

    @Override
    public List<String> onTabComplete(@NotNull DiscordRoleSync plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        return Collections.emptyList(); // No arguments for this command
    }
}
