package me.st4r.DSC.soul;

import me.st4r.DSC.DSC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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

@SuppressWarnings("deprecation")
public class SoulManager {

    private final DSC plugin;
    private final SoulItem soulItem;
    private final Map<SoulType, UUID> activeHolders = new EnumMap<>(SoulType.class);
    private final NamespacedKey shatteredKey;
    private final NamespacedKey karmaKey;
    private final NamespacedKey holderKey;
    public static final int CORRUPTION_THRESHOLD = -5;

    public SoulManager(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.shatteredKey = new NamespacedKey(plugin, "soul_shattered");
        this.karmaKey = new NamespacedKey(plugin, "soul_karma");
        this.holderKey = new NamespacedKey(plugin, "soul_holder");
    }

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

    public UUID getHolder(ItemStack item) {
        if (!soulItem.isSoul(item) || !item.hasItemMeta()) return null;

        String holderStr = item.getItemMeta().getPersistentDataContainer().get(holderKey, PersistentDataType.STRING);
        if (holderStr == null) return null;

        try {
            return UUID.fromString(holderStr);
        } catch (IllegalArgumentException exception) {
            return null;
        }
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

    public int getKarma(ItemStack item) {
        if (!soulItem.isSoul(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        return meta.getPersistentDataContainer().getOrDefault(karmaKey, PersistentDataType.INTEGER, 0);
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

    public ItemStack cleanse(ItemStack item) {
        if (!soulItem.isSoul(item)) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(shatteredKey);
        pdc.set(karmaKey, PersistentDataType.INTEGER, 0);
        item.setItemMeta(meta);
        return refreshVisuals(item);
    }

    public void resynchronizeOnlineHolders() {
        activeHolders.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || !soulItem.isSoul(item)) continue;
                SoulType type = soulItem.getSoulType(item);
                if (type != null) {
                    activeHolders.put(type, player.getUniqueId());
                }
            }
        }
    }

    public void announceSoulAcquired(Player player, SoulType type) {
        if (player == null || type == null) return;
        Bukkit.broadcastMessage(ChatColor.GOLD + player.getName() + ChatColor.GRAY + " has recieved the soul of " + ChatColor.WHITE + type.getDisplayName() + ChatColor.GRAY + ".");
    }

    public void announceSoulPurified(Player player, SoulType type) {
        if (player == null || type == null) return;
        Bukkit.broadcastMessage(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has restored the Soul of " + ChatColor.WHITE + type.getDisplayName() + ChatColor.GRAY + " at the altar.");
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
            try {
                UUID holderUUID = UUID.fromString(holderStr);
                var player = Bukkit.getOfflinePlayer(holderUUID);
                holderName = player.getName() != null ? player.getName() : "Unknown";
            } catch (IllegalArgumentException exception) {
                holderName = "Unknown";
            }
        }

        List<String> lore = new ArrayList<>();

        if (isShattered(item)) {
            meta.setDisplayName(ChatColor.DARK_RED + "Shattered Soul of " + type.getDisplayName());
            lore.add(ChatColor.DARK_RED + "---------------------------------------");
            lore.add(ChatColor.RED + "This soul has been fractured by irredeemable acts.");
            lore.add(ChatColor.GRAY + "All passive traits and dynamic effects are dormant.");
            lore.add("");
            lore.add(ChatColor.GOLD + "Requires a collective restoration ritual at the Soul Altar.");
            lore.add(ChatColor.DARK_RED + "---------------------------------------");
        } else if (karma < CORRUPTION_THRESHOLD) {
            meta.setDisplayName(ChatColor.DARK_GRAY + "Corrupted Soul of " + type.getDisplayName());
            lore.add("");
            lore.add(ChatColor.GRAY + "Karma State: " + ChatColor.DARK_RED + karma + ChatColor.DARK_RED + " [CORRUPTED]");
            lore.add(ChatColor.GRAY + "Current Holder: " + ChatColor.WHITE + holderName);
            lore.add("");
            lore.add(ChatColor.RED + "The air grows cold. Its original light has inverted.");
        } else {
            meta.setDisplayName(type.getColor() + "Soul of " + type.getDisplayName());
            lore.add("");
            lore.add(ChatColor.GRAY + "Karma State: " + ChatColor.GREEN + "+" + karma + ChatColor.GREEN + " [HEALTHY]");
            lore.add(ChatColor.GRAY + "Current Holder: " + ChatColor.WHITE + holderName);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
