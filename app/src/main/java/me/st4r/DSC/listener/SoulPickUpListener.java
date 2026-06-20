package me.st4r.DSC.listener;

import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SoulPickUpListener implements Listener {

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;

    public SoulPickUpListener(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoulPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack stack = event.getItem().getItemStack();
        if (!soulItem.isSoul(stack)) return;

        SoulType type = soulItem.getSoulType(stack);
        if (type == null) return;

        UUID previousHolder = soulItem.getHolder(stack);
        soulManager.setHolder(type, player.getUniqueId());
        event.getItem().setItemStack(soulItem.modifySoulStack(stack, soulManager.getKarma(stack), player.getUniqueId()));

        if (previousHolder == null || !previousHolder.equals(player.getUniqueId())) {
            soulManager.announceSoulAcquired(player, type);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            schedulePossessionSync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            schedulePossessionSync(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoulDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!soulItem.isSoul(stack)) return;

        SoulType type = soulItem.getSoulType(stack);
        if (type == null) return;

        soulManager.setHolder(type, null);
        event.getItemDrop().setItemStack(soulItem.modifySoulStack(stack, soulManager.getKarma(stack), null));
    }

    private void schedulePossessionSync(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> synchronizePossessedSouls(player));
    }

    private void synchronizePossessedSouls(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        ItemStack cursor = player.getItemOnCursor();
        if (soulItem.isSoul(cursor)) {
            player.setItemOnCursor(claimPossessedSoul(player, cursor));
        }

        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (soulItem.isSoul(item)) {
                player.getInventory().setItem(slot, claimPossessedSoul(player, item));
            }
        }
    }

    private ItemStack claimPossessedSoul(Player player, ItemStack stack) {
        SoulType type = soulItem.getSoulType(stack);
        if (type == null) {
            return stack;
        }

        UUID playerUUID = player.getUniqueId();
        UUID previousHolder = soulManager.getHolder(stack);
        soulManager.setHolder(type, playerUUID);

        ItemStack updatedStack = soulItem.modifySoulStack(stack, soulManager.getKarma(stack), playerUUID);
        if (previousHolder == null || !previousHolder.equals(playerUUID)) {
            soulManager.announceSoulAcquired(player, type);
        }

        return updatedStack;
    }
}
