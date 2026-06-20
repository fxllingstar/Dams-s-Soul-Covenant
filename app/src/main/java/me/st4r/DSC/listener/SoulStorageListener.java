package me.st4r.DSC.listener;

import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulItem;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class SoulStorageListener implements Listener {

    private final SoulItem soulItem;

    public SoulStorageListener(DSC plugin) {
        this.soulItem = plugin.getSoulItem();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStorageClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isBlockedStorage(topInventory)) {
            return;
        }

        if (wouldStoreSoul(event, topInventory)) {
            event.setCancelled(true);
            notifyPlayer(event.getWhoClicked() instanceof Player player ? player : null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStorageDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isBlockedStorage(topInventory)) {
            return;
        }

        int topSize = topInventory.getSize();
        for (var entry : event.getNewItems().entrySet()) {
            if (entry.getKey() < topSize && soulItem.isSoul(entry.getValue())) {
                event.setCancelled(true);
                notifyPlayer(event.getWhoClicked() instanceof Player player ? player : null);
                return;
            }
        }
    }

    private boolean wouldStoreSoul(InventoryClickEvent event, Inventory topInventory) {
        Inventory clickedInventory = event.getClickedInventory();
        Inventory bottomInventory = event.getView().getBottomInventory();
        InventoryAction action = event.getAction();
        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < topInventory.getSize();

        if (clickedTop && soulItem.isSoul(event.getView().getCursor())) {
            return true;
        }

        if (clickedTop && isHotbarSwap(action)) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && event.getWhoClicked() instanceof Player player) {
                return soulItem.isSoul(player.getInventory().getItem(hotbarButton));
            }
        }

        return clickedInventory != null
                && clickedInventory.equals(bottomInventory)
                && action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && soulItem.isSoul(event.getCurrentItem());
    }

    private boolean isBlockedStorage(Inventory inventory) {
        InventoryType type = inventory.getType();
        return type == InventoryType.CHEST || type == InventoryType.ENDER_CHEST;
    }

    private boolean isHotbarSwap(InventoryAction action) {
        return action == InventoryAction.HOTBAR_SWAP || "HOTBAR_MOVE_AND_READD".equals(action.name());
    }

    private void notifyPlayer(Player player) {
        if (player != null) {
            player.sendMessage(ChatColor.RED + "Souls cannot be stored in chests or ender chests.");
        }
    }
}
