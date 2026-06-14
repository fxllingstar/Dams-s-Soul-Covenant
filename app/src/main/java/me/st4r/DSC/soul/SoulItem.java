package me.st4r.DSC.soul;


import me.st4r.DSC.DSC;
import org.bukkit.Bukkit;
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
    public SoulItem(DSC plugin){
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "soul_type");
        this.karmaKey = new NamespacedKey(plugin, "soul_karma");
        this.holderKey = new NamespacedKey(plugin, "soul_holder");
        this.shatteredKey = new NamespacedKey(plugin, "soul_shattered");
    }

    //================================
    //Method to generate a Soul item
    //=================================
    public ItemStack create(SoulType type){
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(type.getColor() + "Soul of " + type.getDisplayName());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, type.name());
        pdc.set(karmaKey, PersistentDataType.INTEGER, type.getDefaultKarma());

        item.setItemMeta(meta);


        return updateItemDisplay(item, type, type.getDefaultKarma(), null);
    }

    public boolean isSoul(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(typeKey, PersistentDataType.STRING);
    }

    public SoulType getSoulType(ItemStack item){
        if (!isSoul(item)) return null;
        String typeStr = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        try {
            return SoulType.valueOf(typeStr);
        } catch (IllegalArgumentException | NullPointerException e){
            return null;
        }
    }

    public ItemStack modifySoulStack(ItemStack item, int newKarma, UUID newHolder){
        SoulType type = getSoulType(item);
        if (type == null) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(karmaKey, PersistentDataType.INTEGER, newKarma);

        if (newHolder != null){
            pdc.set(holderKey, PersistentDataType.STRING, newHolder.toString());
        }else {
            pdc.remove(holderKey);
        }

        item.setItemMeta(meta);
        return updateItemDisplay(item, type, newKarma, newHolder);
    }

    private ItemStack updateItemDisplay(ItemStack item, SoulType type, int karma, UUID holderUUID){
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = new ArrayList<>();
        lore.add(type.getColor() + "Soul");
        lore.add("");
        lore.add("§7Karma State: " + (karma >= 0 ? "§a" : "§c") + karma);
        
        String holderName = "None";
        if (holderUUID != null){
            var player = Bukkit.getOfflinePlayer(holderUUID);
            holderName = player.getName() != null ? player.getName() : holderUUID.toString();
        }
        lore.add("§7Current Holder: §f" + holderName);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
   
    }


}
