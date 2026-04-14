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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class AmethystToolsPlugin extends JavaPlugin {

    private NamespacedKey toolKey;
    private NamespacedKey expiresAtKey;
    private NamespacedKey itemTypeKey;
    private AmethystToolService toolService;
    private BukkitTask loreTask;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    @Override
    public void onEnable() {
        ensureManagedFilesAreCurrent();

        this.toolKey = new NamespacedKey(this, "amethyst_tool");
        this.expiresAtKey = new NamespacedKey(this, "expires_at");
        this.itemTypeKey = new NamespacedKey(this, "tool_item_key");
        this.toolService = new AmethystToolService(this, toolKey, expiresAtKey, itemTypeKey);

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
        ensureManagedFilesAreCurrent();
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

    private void ensureManagedFilesAreCurrent() {
        String expectedVersion = getDescription().getVersion();
        ensureVersionedResource("config.yml", "config-version", expectedVersion);
        ensureVersionedResource("messages.yml", "messages-version", expectedVersion);
        reloadConfig();
        loadMessagesConfig();
    }

    private void ensureVersionedResource(String fileName, String versionKey, String expectedVersion) {
        File targetFile = new File(getDataFolder(), fileName);
        if (!targetFile.exists()) {
            saveResource(fileName, false);
            return;
        }

        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(targetFile);
        String currentVersion = currentConfig.getString(versionKey, "");
        if (expectedVersion.equals(currentVersion)) {
            return;
        }

        String fileVersionForName = normalizeVersionForFilename(currentVersion);
        File backupFile = createBackupFile(fileName, fileVersionForName);

        try {
            Files.move(targetFile.toPath(), backupFile.toPath());
        } catch (IOException ex) {
            getLogger().severe("Failed to archive " + fileName + " before regeneration: " + ex.getMessage());
            return;
        }

        try {
            saveResource(fileName, false);
            getLogger().warning(fileName + " version mismatch (" + versionKey + "=" + currentVersion + ", expected " + expectedVersion + ").");
            getLogger().warning("Archived old file as " + backupFile.getName() + " and regenerated a new " + fileName + ".");
        } catch (IllegalArgumentException ex) {
            getLogger().severe("Failed to regenerate " + fileName + ": " + ex.getMessage());
            try {
                Files.move(backupFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().warning("Restored original " + fileName + " from backup because regeneration failed.");
            } catch (IOException restoreEx) {
                getLogger().severe("Failed to restore original " + fileName + ": " + restoreEx.getMessage());
            }
        }
    }

    private File createBackupFile(String fileName, String fileVersionForName) {
        String baseName = fileName.endsWith(".yml")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;

        File backupFile = new File(getDataFolder(), "__old_" + baseName + "_" + fileVersionForName + "__.yml");
        int duplicateIndex = 1;
        while (backupFile.exists()) {
            backupFile = new File(getDataFolder(), "__old_" + baseName + "_" + fileVersionForName + "__" + duplicateIndex + ".yml");
            duplicateIndex++;
        }

        return backupFile;
    }

    private String normalizeVersionForFilename(String version) {
        if (version == null || version.isBlank()) {
            return "unknown";
        }
        return version.replaceAll("[^a-zA-Z0-9._-]", "_");
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
