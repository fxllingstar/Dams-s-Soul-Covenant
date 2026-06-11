package me.st4r.DSC.soul;

import me.st4r.DSC.DSC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SoulManager {

    private final DSC plugin;
    private final SoulItem soulItem;
    private final Map<SoulType, UUID> activeHolders = new EnumMap<>(SoulType.class);
    private final NamespacedKey shatteredKey;
    private final NamespacedKey karmaKey;
    private final NamespacedKey holderKey;
    public static final int CORRUPTION_THRESHOLD = 0;

    public SoulManager(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.shatteredKey = new NamespacedKey(plugin, "soul_shattered");
        this.karmaKey = new NamespacedKey(plugin, "soul_karma");
        this.holderKey = new NamespacedKey(plugin, "soul_holder");
    }

    /* ==========================================
       1. HOLDER / REGISTRY TRACKING METHODS
       ========================================== */

    public void setHolder(SoulType type, UUID playerUUID) {
        if (playerUUID == null) {
            activeHolders.remove(type);
        } else {
            activeHolders.put(type, playerUUID);
        }
    }

    public UUID getHolder(SoulType type) {
        return activeHolders.get(type);
    }

    public SoulType getSoulOfPlayer(UUID playerUUID) {
        if (playerUUID == null) return null;
        for (Map.Entry<SoulType, UUID> entry : activeHolders.entrySet()) {
            if (playerUUID.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public boolean isHoldingSoul(UUID playerUUID) {
        return activeHolders.containsValue(playerUUID);
    }

    public void clearAll() {
        activeHolders.clear();
    }

    public Map<SoulType, UUID> getLiveRegistryView() {
        return Collections.unmodifiableMap(activeHolders);
    }

    /* ==========================================
       2. KARMA & CORRUPTION MUTATION METHODS
       ========================================== */

    public ItemStack addKarma(ItemStack item, int amount) {
        return modifyKarmaValue(item, amount);
    }

    public ItemStack removeKarma(ItemStack item, int amount) {
        return modifyKarmaValue(item, -amount);
    }

    public boolean isCorrupted(ItemStack item) {
        if (!soulItem.isSoul(item) || isShattered(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        Integer karma = meta.getPersistentDataContainer().get(karmaKey, PersistentDataType.INTEGER);
        return karma != null && karma < CORRUPTION_THRESHOLD;
    }

    public boolean isShattered(ItemStack item) {
        if (!soulItem.isSoul(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        Byte shatteredByte = meta.getPersistentDataContainer().get(shatteredKey, PersistentDataType.BYTE);
        return shatteredByte != null && shatteredByte == (byte) 1;
    }

    public ItemStack shatter(ItemStack item) {
        if (!soulItem.isSoul(item)) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().set(shatteredKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        return refreshVisuals(item);
    }

    private ItemStack modifyKarmaValue(ItemStack item, int delta) {
        if (!soulItem.isSoul(item) || isShattered(item)) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int currentKarma = pdc.getOrDefault(karmaKey, PersistentDataType.INTEGER, 0);
        pdc.set(karmaKey, PersistentDataType.INTEGER, currentKarma + delta);
        item.setItemMeta(meta);

        return refreshVisuals(item);
    }

    /* ==========================================
       3. VISUAL METADATA REFRESH ENGINE
       ========================================== */

    public ItemStack refreshVisuals(ItemStack item) {
        SoulType type = soulItem.getSoulType(item);
        if (type == null) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int karma = pdc.getOrDefault(karmaKey, PersistentDataType.INTEGER, 0);
        
        String holderStr = pdc.get(holderKey, PersistentDataType.STRING);
        String holderName = "None";
        if (holderStr != null) {
            var player = Bukkit.getOfflinePlayer(UUID.fromString(holderStr));
            holderName = player.getName() != null ? player.getName() : "Unknown";
        }

        List<String> lore = new ArrayList<>();
        
        if (isShattered(item)) {
            meta.setDisplayName(ChatColor.DARK_RED + "§l✕ Shattered Soul of " + type.getDisplayName() + " ✕");
            lore.add("§4§m---------------------------------------");
            lore.add("§cThis soul has been fractured by irredeemable acts.");
            lore.add("§7All passive traits and dynamic effects are completely §cdormant§7.");
            lore.add("");
            lore.add("§6Requires a collective Restoration Ritual at the Soul Altar.");
            lore.add("§4§m---------------------------------------");
        } else if (karma < CORRUPTION_THRESHOLD) {
            meta.setDisplayName(ChatColor.DARK_GRAY + "§k!§r " + ChatColor.RED + "Corrupted Soul of " + type.getDisplayName() + ChatColor.DARK_GRAY + " §k!");
            lore.add("");
            lore.add("§7Karma State: " + ChatColor.DARK_RED + karma + " §4[CORRUPTED]");
            lore.add("§7Current Holder: §f" + holderName);
            lore.add("");
            lore.add(ChatColor.RED + "§oThe air grows cold. Its original light has completely inverted...");
        } else {
            meta.setDisplayName(type.getColor() + "Soul of " + type.getDisplayName());
            lore.add("");
            lore.add("§7Karma State: " + ChatColor.GREEN + "+" + karma + " §a[HEALTHY]");
            lore.add("§7Current Holder: §f" + holderName);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}