package ru.hogeltbellai.automine.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import ru.hogeltbellai.automine.AutoMineMain;

public class AutoMineCommand implements CommandExecutor {

    private final AutoMineMain plugin;

    public AutoMineCommand(AutoMineMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command only for players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /automine <create|remove> <regionName>");
            return true;
        }

        String subCommand = args[0];

        if (subCommand.equalsIgnoreCase("create")) {
            if (!player.hasPermission("automine.create")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Usage: /automine create <regionName>");
                return true;
            }

            String regionName = args[1];
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(player.getWorld()));
            if (regionManager == null) {
                player.sendMessage(ChatColor.RED + "Failed to get RegionManager for this world!");
                return true;
            }

            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                player.sendMessage(ChatColor.RED + "Unknown region name!");
                return true;
            }

            plugin.addAutoMineRegion(region, player.getWorld());
            player.sendMessage(ChatColor.GREEN + "Automine has been created in region: " + region.getId());
            return true;

        } else if (subCommand.equalsIgnoreCase("remove")) {
            if (!player.hasPermission("automine.remove")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Usage: /automine remove <regionName>");
                return true;
            }

            String regionName = args[1];
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(player.getWorld()));
            if (regionManager == null) {
                player.sendMessage(ChatColor.RED + "Failed to get RegionManager for this world!");
                return true;
            }

            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                player.sendMessage(ChatColor.RED + "Region with the specified name not found!");
                return true;
            }

            plugin.removeAutoMineRegion(region);
            player.sendMessage(ChatColor.GREEN + "Automine has been removed from region: " + region.getId());
            return true;
            
        } else if (subCommand.equalsIgnoreCase("reload")) {
            if (!player.hasPermission("automine.reload")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            plugin.reloadConfigs();
            player.sendMessage(ChatColor.GREEN + "AutoMine configurations have been reloaded!");
            return true;
        }
        
        player.sendMessage(ChatColor.RED + "Invalid command! Usage: /automine <create|remove|reload> <regionName>");
        return true;
    }
}
