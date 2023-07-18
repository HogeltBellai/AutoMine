package ru.hogeltbellai.automine;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import net.milkbowl.vault.economy.Economy;
import ru.hogeltbellai.automine.commands.AutoMineCommand;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AutoMineMain extends JavaPlugin implements Listener {

    private Economy economy;
    private FileConfiguration config;
    private File configFile;
    private Map<Material, Integer> cooldowns;
    private Map<UUID, Map<Material, Long>> playerCooldowns;
    private Map<Material, Double> moneyMap;
    private Map<Location, Material> changedBlocks;
    private Map<ProtectedRegion, Double> autoMineRegions;
    private WorldGuardPlugin worldGuard;
    private FileConfiguration mineConfig;
    private File mineFile;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!setupEconomy()) {
                getLogger().warning("Failed to initialize Vault. The plugin will be disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (!setupWorldGuard()) {
                getLogger().warning("Failed to initialize WorldGuard. The plugin will be disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                saveDefaultConfig();
            }
            mineFile = new File(getDataFolder(), "mine.yml");
            if (!mineFile.exists()) {
                saveResource("mine.yml", false);
            }

            mineConfig = YamlConfiguration.loadConfiguration(mineFile);

            config = getConfig();
            cooldowns = new HashMap<>();
            playerCooldowns = new HashMap<>();
            moneyMap = new HashMap<>();
            changedBlocks = new HashMap<>();
            autoMineRegions = new HashMap<>();
            loadCooldownsFromConfig();
            loadMoneyFromConfig();
            loadAutoMineRegionsFromConfig();

            getServer().getPluginManager().registerEvents(this, this);

            getCommand("automine").setExecutor(new AutoMineCommand(this));

            getLogger().info("=======================================");
            getLogger().info("=                                     =");
            getLogger().info("=               AutoMine              =");
            getLogger().info("=          Author HogeltBella         =");
            getLogger().info("=                                     =");
            getLogger().info("=======================================");

            restoreChangedBlocks();
        }, 20L);
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
        return (economy != null);
    }

    private boolean setupWorldGuard() {
        worldGuard = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");
        return (worldGuard != null);
    }
    
    public void reloadConfigs() {
        reloadConfig();
        config = getConfig();
        loadCooldownsFromConfig();
        loadMoneyFromConfig();

        mineConfig = YamlConfiguration.loadConfiguration(mineFile);
        autoMineRegions.clear();
        loadAutoMineRegionsFromConfig();
    }

    public void loadCooldownsFromConfig() {
        config = getConfig();
        cooldowns.clear();

        for (String materialString : config.getConfigurationSection("blocks").getKeys(false)) {
            Material material = Material.getMaterial(materialString);
            int cooldown = config.getInt("blocks." + materialString + ".cooldowns");
            cooldowns.put(material, cooldown);
        }
    }

    public void loadMoneyFromConfig() {
        config = getConfig();
        if (config == null) {
            getLogger().warning("Config is null!");
            return;
        }

        moneyMap.clear();

        ConfigurationSection moneySection = config.getConfigurationSection("blocks");
        if (moneySection != null) {
            for (String materialString : moneySection.getKeys(false)) {
                Material material = Material.getMaterial(materialString);
                if (material == null) {
                    getLogger().warning("Invalid material in config: " + materialString);
                    continue;
                }

                double money = config.getDouble("blocks." + materialString + ".money");
                moneyMap.put(material, money);
            }
        }
    }
    
    public void loadAutoMineRegionsFromConfig() {
        autoMineRegions.clear();

        if (mineConfig == null) {
            getLogger().warning("Mine config is null!");
            return;
        }

        ConfigurationSection regionSection = mineConfig.getConfigurationSection("mines");
        if (regionSection != null) {
            for (String regionIndex : regionSection.getKeys(false)) {
                ConfigurationSection regionData = regionSection.getConfigurationSection(regionIndex);
                if (regionData != null) {
                    String regionName = regionData.getString("region");
                    double moneyMultiplier = regionData.getDouble("multiplier");
                    String worldName = regionData.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        getLogger().warning("Failed to get Region for this world: " + worldName);
                        continue;
                    }
                    RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
                    if (regionManager == null) {
                        getLogger().warning("Failed to get RegionManager for this world: " + worldName);
                        continue;
                    }
                    ProtectedRegion region = regionManager.getRegion(regionName);
                    if (region != null) {
                        autoMineRegions.put(region, moneyMultiplier);
                    } else {
                        getLogger().warning("Region not found for name: " + regionName);
                    }
                }
            }
        }
    }

    private void setPlayerCooldown(UUID playerId, Material material) {
        long currentTime = System.currentTimeMillis();
        Map<Material, Long> playerCooldownMap = playerCooldowns.getOrDefault(playerId, new HashMap<>());
        playerCooldownMap.put(material, currentTime);
        playerCooldowns.put(playerId, playerCooldownMap);
    }

    public void addAutoMineRegion(ProtectedRegion region, World world) {
        autoMineRegions.put(region, 1.0);
        saveAutoMineRegionsToConfig(region.getId(), world.getName());
    }
    
    public void removeAutoMineRegion(ProtectedRegion region) {
        autoMineRegions.remove(region);
        removeAutoMineRegionFromConfig(region.getId());
    }

    private void removeAutoMineRegionFromConfig(String regionName) {
        FileConfiguration mineConfig = YamlConfiguration.loadConfiguration(mineFile);
        ConfigurationSection regionSection = mineConfig.getConfigurationSection("mines");
        if (regionSection != null) {
            for (String regionIndex : regionSection.getKeys(false)) {
                ConfigurationSection regionData = regionSection.getConfigurationSection(regionIndex);
                if (regionData != null) {
                    String storedRegionName = regionData.getString("region");
                    if (regionName.equals(storedRegionName)) {
                        regionSection.set(regionIndex, null);
                        try {
                            mineConfig.save(mineFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                }
            }
        }
    }

    private void saveAutoMineRegionsToConfig(String regionName, String worldName) {
        ConfigurationSection mineSection = mineConfig.createSection("mines");
        int regionIndex = 0;
        
        mineSection.set(regionIndex + ".region", regionName);
        mineSection.set(regionIndex + ".multiplier", 1.0);
        mineSection.set(regionIndex + ".world", worldName);

        regionIndex++;

        try {
            mineConfig.save(mineFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Material material = event.getBlock().getType();
        double money = moneyMap.getOrDefault(material, 0.0);
        
        String noBreakMessage = config.getString("messages.no-break");
        noBreakMessage = ChatColor.translateAlternateColorCodes('&', noBreakMessage);

        Location blockLocation = event.getBlock().getLocation();
        for (Map.Entry<ProtectedRegion, Double> entry : autoMineRegions.entrySet()) {
            ProtectedRegion region = entry.getKey();

            if (region.contains(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ())) {
                if (config.contains("blocks." + material.name())) {
                	Integer cooldown = cooldowns.get(material);
                    if (cooldown == null) {
                        event.setCancelled(true);
                        player.sendMessage(noBreakMessage);
                        return;
                    }
                    Block block = event.getBlock();
                    Material originalMaterial = block.getType();
                    changedBlocks.put(blockLocation, originalMaterial);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (changedBlocks.containsKey(blockLocation)) {
                                Material storedMaterial = changedBlocks.get(blockLocation);
                                block.setType(storedMaterial);
                                changedBlocks.remove(blockLocation);
                            }
                        }
                    }.runTaskLater(this, cooldown * 10L);

                    block.setType(Material.BEDROCK);
                    event.setCancelled(true);
                    economy.depositPlayer(player, money);

                    String moneyMessage = config.getString("messages.break");
                    moneyMessage = moneyMessage.replace("{money}", String.valueOf(money));
                    moneyMessage = ChatColor.translateAlternateColorCodes('&', moneyMessage);
                    moneyMessage = moneyMessage.replace("{material}", material.name());
                    player.sendMessage(moneyMessage);

                    setPlayerCooldown(playerId, material);
                } else {
                	event.setCancelled(true);
                    player.sendMessage(noBreakMessage);
                }
            }
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().equals(this)) {
            restoreChangedBlocks();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(this)) {
            restoreChangedBlocks();
        }
    }

    private void restoreChangedBlocks() {
        for (Map.Entry<Location, Material> entry : changedBlocks.entrySet()) {
            Location location = entry.getKey();
            Material material = entry.getValue();
            Block block = location.getBlock();
            block.setType(material);
        }
        changedBlocks.clear();
    }
}
