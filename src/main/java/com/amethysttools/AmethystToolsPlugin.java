package com.amethysttools;

import com.amethysttools.command.AmethystToolsCommand;
import com.amethysttools.listener.AmethystPickaxeListener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

public final class AmethystToolsPlugin extends JavaPlugin {

    private NamespacedKey toolKey;
    private NamespacedKey expiresAtKey;
    private AmethystToolService toolService;
    private BukkitTask loreTask;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        loadMessagesConfig();
        validateConfigVersions();

        this.toolKey = new NamespacedKey(this, "amethyst_tool");
        this.expiresAtKey = new NamespacedKey(this, "expires_at");
        this.toolService = new AmethystToolService(this, toolKey, expiresAtKey);

        PluginCommand command = getCommand("amethysttools");
        if (command != null) {
            AmethystToolsCommand executor = new AmethystToolsCommand(this, toolService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        Bukkit.getPluginManager().registerEvents(new AmethystPickaxeListener(this, toolService), this);
        startLoreUpdateTask();
    }

    @Override
    public void onDisable() {
        if (loreTask != null) {
            loreTask.cancel();
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        loadMessagesConfig();
        validateConfigVersions();
        toolService.reloadBlockedMaterials();
    }

    public FileConfiguration getMessagesConfig() {
        if (messagesConfig == null) {
            loadMessagesConfig();
        }
        return messagesConfig;
    }

    private void loadMessagesConfig() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void validateConfigVersions() {
        String pluginVersion = getDescription().getVersion();
        checkVersion("config.yml", "config-version", getConfig().getString("config-version", ""), pluginVersion);
        checkVersion("messages.yml", "messages-version", getMessagesConfig().getString("messages-version", ""), pluginVersion);
    }

    private void checkVersion(String fileName, String key, String currentVersion, String expectedVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            getLogger().warning(fileName + " is missing " + key + ". Expected: " + expectedVersion + ".");
            getLogger().warning("Please merge new keys from the latest default " + fileName + ".");
            return;
        }

        if (currentVersion.equals(expectedVersion)) {
            return;
        }

        getLogger().warning(fileName + " version mismatch (" + key + "=" + currentVersion + ", expected " + expectedVersion + ").");
        getLogger().warning("Please merge new keys from the latest default " + fileName + ".");
    }

    private void startLoreUpdateTask() {
        // Update lore and remove expired items every minute.
        loreTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack[] contents = player.getInventory().getContents();
                updateInventory(contents, player);
                player.getInventory().setContents(contents);

                ItemStack[] armor = player.getInventory().getArmorContents();
                updateInventory(armor, player);
                player.getInventory().setArmorContents(armor);

                ItemStack offhand = player.getInventory().getItemInOffHand();
                if (offhand != null) {
                    player.getInventory().setItemInOffHand(toolService.updateOrExpire(offhand, player));
                }
            }
        }, 20L, 1200L);
    }

    private void updateInventory(ItemStack[] contents, Player owner) {
        for (int i = 0; i < contents.length; i++) {
            contents[i] = toolService.updateOrExpire(contents[i], owner);
        }
    }
}
