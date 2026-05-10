package com.amethysttools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AmethystToolService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final AmethystToolsPlugin plugin;
    private final NamespacedKey toolKey;
    private final NamespacedKey expiresAtKey;
    private final NamespacedKey itemTypeKey;
    private final Set<Material> blockedMaterials = new HashSet<>();
    private final Set<Material> excludedFromArea = new HashSet<>();

    public AmethystToolService(AmethystToolsPlugin plugin, NamespacedKey toolKey, NamespacedKey expiresAtKey, NamespacedKey itemTypeKey) {
        this.plugin = plugin;
        this.toolKey = toolKey;
        this.expiresAtKey = expiresAtKey;
        this.itemTypeKey = itemTypeKey;
        reloadBlockedMaterials();
        reloadExcludedFromArea();
    }

    public void reloadBlockedMaterials() {
        blockedMaterials.clear();
        for (String name : plugin.getConfig().getStringList("blocked-materials")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                blockedMaterials.add(material);
            } else {
                plugin.getLogger().warning("Unknown material in blocked-materials: " + name);
            }
        }
    }

    public void reloadExcludedFromArea() {
        excludedFromArea.clear();
        for (String name : plugin.getConfig().getStringList("mining.excluded-materials")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                excludedFromArea.add(material);
            } else {
                plugin.getLogger().warning("Unknown material in mining.excluded-materials: " + name);
            }
        }
    }

    public boolean isExcludedFromArea(Material material) {
        return excludedFromArea.contains(material);
    }

    public ItemStack createItem(String itemKey, int amount) {
        ConfigurationSection itemSection = getItemSection(itemKey);
        String materialName = itemSection != null ? itemSection.getString("material", "NETHERITE_PICKAXE") : "NETHERITE_PICKAXE";
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.NETHERITE_PICKAXE;
        }

        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        long expiresAt = Instant.now().plus(Duration.ofDays(getExpiryDays())).toEpochMilli();

        meta.displayName(
            color(itemSection != null ? itemSection.getString("name", "&dAmethyst Pickaxe") : "&dAmethyst Pickaxe")
                .decoration(TextDecoration.ITALIC, false)
        );
        if (itemSection != null && itemSection.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }

        boolean hasConfiguredEnchants = applyConfiguredEnchants(meta, itemSection, itemKey);

        if (!hasConfiguredEnchants && itemSection != null && itemSection.getBoolean("glow", true)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(toolKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(expiresAtKey, PersistentDataType.LONG, expiresAt);
        pdc.set(itemTypeKey, PersistentDataType.STRING, itemKey);

        meta.lore(buildLore(expiresAt, itemSection));
        item.setItemMeta(meta);
        return item;
    }

    public String resolveItemKey(String inputKey) {
        String normalizedInput = normalizeKey(inputKey == null || inputKey.isBlank() ? "amethyst_pickaxe" : inputKey);
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                if (normalizeKey(key).equals(normalizedInput)) {
                    return key;
                }

                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) {
                    continue;
                }

                for (String alias : itemSection.getStringList("aliases")) {
                    if (normalizeKey(alias).equals(normalizedInput)) {
                        return key;
                    }
                }
            }
            return null;
        }

        if (!plugin.getConfig().isConfigurationSection("item")) {
            return null;
        }

        if (normalizedInput.equals("pickaxe")
                || normalizedInput.equals("amethystpickaxe")) {
            return "pickaxe";
        }
        return null;
    }

    public List<String> getItemInputOptions() {
        Set<String> options = new LinkedHashSet<>();
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                options.add(key.toLowerCase(Locale.ROOT));
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) {
                    continue;
                }
                for (String alias : itemSection.getStringList("aliases")) {
                    if (alias != null && !alias.isBlank()) {
                        options.add(alias.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        if (options.isEmpty() && plugin.getConfig().isConfigurationSection("item")) {
            options.add("pickaxe");
            options.add("amethystpickaxe");
            options.add("amethyst_pickaxe");
        }

        return new ArrayList<>(options);
    }

    public boolean isAmethystTool(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(toolKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isExpired(ItemStack item) {
        long expiresAt = getExpiresAt(item);
        return expiresAt > 0 && Instant.now().toEpochMilli() >= expiresAt;
    }

    public String getToolItemKey(ItemStack item) {
        if (!isAmethystTool(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String stored = meta.getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            return stored;
        }

        return resolveItemKey("amethyst_pickaxe");
    }

    public boolean isToolItem(ItemStack item, String expectedKey) {
        String actualKey = getToolItemKey(item);
        if (actualKey == null || expectedKey == null || expectedKey.isBlank()) {
            return false;
        }

        String resolvedExpected = resolveItemKey(expectedKey);
        if (resolvedExpected == null) {
            resolvedExpected = expectedKey;
        }

        return normalizeKey(actualKey).equals(normalizeKey(resolvedExpected));
    }

    public long getExpiresAt(ItemStack item) {
        if (!isAmethystTool(item)) {
            return -1L;
        }
        ItemMeta meta = item.getItemMeta();
        Long expires = meta.getPersistentDataContainer().get(expiresAtKey, PersistentDataType.LONG);
        return expires == null ? -1L : expires;
    }

    public ItemStack updateOrExpire(ItemStack item, Player owner) {
        if (!isAmethystTool(item)) {
            return item;
        }
        if (isExpired(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.lore(buildLore(getExpiresAt(item), null));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBlocked(Material material) {
        return blockedMaterials.contains(material);
    }

    public int getRadius() {
        return Math.max(0, plugin.getConfig().getInt("mining.radius", 1));
    }

    public int getDepth() {
        return Math.max(1, plugin.getConfig().getInt("mining.depth", 1));
    }

    public long getExpiryDays() {
        return Math.max(1, plugin.getConfig().getLong("expiry.days", 3));
    }

    public String msg(String key) {
        String raw = plugin.getMessagesConfig().getString("messages." + key, key);
        return LEGACY.serialize(color(raw));
    }

    private boolean applyConfiguredEnchants(ItemMeta meta, ConfigurationSection itemSection, String itemKey) {
        if (itemSection == null) {
            return false;
        }

        ConfigurationSection enchants = itemSection.getConfigurationSection("enchantments");
        if (enchants == null) {
            return false;
        }

        boolean addedAny = false;
        for (String enchantName : enchants.getKeys(false)) {
            Enchantment enchantment = resolveEnchantment(enchantName);
            if (enchantment == null) {
                plugin.getLogger().warning("Unknown enchantment in items." + itemKey + ".enchantments: " + enchantName);
                continue;
            }

            int level = Math.max(1, enchants.getInt(enchantName, 1));
            meta.addEnchant(enchantment, level, true);
            addedAny = true;
        }

        return addedAny;
    }

    private Enchantment resolveEnchantment(String enchantName) {
        String normalized = enchantName.toUpperCase(Locale.ROOT);
        if (normalized.equals("SILKTOUCH")) {
            normalized = "SILK_TOUCH";
        }
        return Enchantment.getByName(normalized);
    }

    private List<Component> buildLore(long expiresAt, ConfigurationSection itemSection) {
        List<Component> lore = new ArrayList<>();
        List<String> configured = itemSection != null
                ? itemSection.getStringList("lore")
                : plugin.getConfig().getStringList("item.lore");
        if (configured.isEmpty()) {
            configured = List.of("&7Expires in: &e%time%");
        }
        String remaining = formatRemaining(expiresAt);
        for (String line : configured) {
            lore.add(color(line.replace("%time%", remaining)));
        }
        return lore;
    }

    private ConfigurationSection getItemSection(String itemKey) {
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("items");
        if (itemsSection != null) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
            if (itemSection != null) {
                return itemSection;
            }

            String resolved = resolveItemKey(itemKey);
            if (resolved != null) {
                return itemsSection.getConfigurationSection(resolved);
            }
            return null;
        }

        return plugin.getConfig().getConfigurationSection("item");
    }

    private String normalizeKey(String input) {
        return input.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private String formatRemaining(long expiresAt) {
        long millisLeft = Math.max(0L, expiresAt - Instant.now().toEpochMilli());
        Duration duration = Duration.ofMillis(millisLeft);

        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusDays(days).minusHours(hours).toMinutes();

        return String.format(Locale.ROOT, "%dd %dh %dm", days, hours, minutes);
    }

    private Component color(String input) {
        return LEGACY.deserialize(input);
    }
}
