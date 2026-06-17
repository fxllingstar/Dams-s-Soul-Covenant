package me.st4r.DSC.soul;

import me.st4r.DSC.DSC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class SoulItem {

    private final DSC plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey karmaKey;
    private final NamespacedKey holderKey;
    private final NamespacedKey shatteredKey;

    public SoulItem(DSC plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "soul_type");
        this.karmaKey = new NamespacedKey(plugin, "soul_karma");
        this.holderKey = new NamespacedKey(plugin, "soul_holder");
        this.shatteredKey = new NamespacedKey(plugin, "soul_shattered");
    }

    public ItemStack create(SoulType type) {
        return create(type, null);
    }

    public ItemStack create(SoulType type, UUID holderUUID) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(type.getColor() + "Soul of " + type.getDisplayName());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, type.name());
        pdc.set(karmaKey, PersistentDataType.INTEGER, type.getDefaultKarma());
        if (holderUUID != null) {
            pdc.set(holderKey, PersistentDataType.STRING, holderUUID.toString());
        } else {
            pdc.remove(holderKey);
        }

        item.setItemMeta(meta);
        return updateItemDisplay(item, type, type.getDefaultKarma(), holderUUID);
    }

    public boolean isSoul(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(typeKey, PersistentDataType.STRING);
    }

    public UUID getHolder(ItemStack item) {
        if (!isSoul(item) || !item.hasItemMeta()) return null;
        String holderStr = item.getItemMeta().getPersistentDataContainer().get(holderKey, PersistentDataType.STRING);
        if (holderStr == null) return null;

        try {
            return UUID.fromString(holderStr);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public SoulType getSoulType(ItemStack item) {
        if (!isSoul(item)) return null;
        String typeStr = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        try {
            return SoulType.valueOf(typeStr);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    public ItemStack modifySoulStack(ItemStack item, int newKarma, UUID newHolder) {
        SoulType type = getSoulType(item);
        if (type == null) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(karmaKey, PersistentDataType.INTEGER, newKarma);

        if (newHolder != null) {
            pdc.set(holderKey, PersistentDataType.STRING, newHolder.toString());
        } else {
            pdc.remove(holderKey);
        }

        item.setItemMeta(meta);
        return updateItemDisplay(item, type, newKarma, newHolder);
    }

    private ItemStack updateItemDisplay(ItemStack item, SoulType type, int karma, UUID holderUUID) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (holderUUID == null) {
            holderUUID = getHolder(item);
        }
        if (holderUUID == null && plugin.getSoulManager() != null) {
            holderUUID = plugin.getSoulManager().getHolder(type);
        }

        List<String> lore = new ArrayList<>();
        lore.addAll(getSoulLore(type));
        lore.add("");
        lore.add(ChatColor.GRAY + "Karma State: " + (karma >= 0 ? ChatColor.GREEN : ChatColor.RED) + (karma >= 0 ? "+" : "") + karma);

        String holderName = "None";
        if (holderUUID != null) {
            var player = Bukkit.getOfflinePlayer(holderUUID);
            holderName = player.getName() != null ? player.getName() : holderUUID.toString();
        }
        lore.add(ChatColor.GRAY + "Current Holder: " + ChatColor.WHITE + holderName);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> getSoulLore(SoulType type) {
        List<String> lore = new ArrayList<>();
        switch (type) {
            case BRAVERY -> lore.add(ChatColor.GRAY + "\"It does not ask if you are ready. It only asks if you will move.\"");
            case DETERMINATION -> lore.add(ChatColor.GRAY + "\"The mountain does not care how many times you have fallen. Neither does this soul.\"");
            case JUSTICE -> lore.add(ChatColor.GRAY + "\"It has no mercy. It has no cruelty. It has only balance.\"");
            case PERSEVERANCE -> lore.add(ChatColor.GRAY + "\"It was not forged in fire. It was worn smooth by rain - centuries of it.\"");
            case PATIENCE -> lore.add(ChatColor.GRAY + "\"It has waited longer than you have been alive. It can wait a little longer.\"");
            case INTEGRITY -> lore.add(ChatColor.GRAY + "\"It does not reward you for what you do when people are watching.\"");
            case KINDNESS -> lore.add(ChatColor.GRAY + "\"It is not soft. It takes more strength than any of the others.\"");
        }
        return lore;
    }
}
