package me.st4r.DSC.pledge;

import me.st4r.DSC.DSC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class PledgeClaimListener implements Listener {

    private final DSC plugin;

    public PledgeClaimListener(DSC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClaimClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof PledgeClaimHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.getOwnerUUID())) {
            event.setCancelled(true);
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        if (clickedInventory.equals(event.getView().getBottomInventory())) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        if (!clickedInventory.equals(topInventory)) {
            event.setCancelled(true);
            return;
        }

        if (!isTakingAction(event.getAction())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClaimDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof PledgeClaimHolder)) {
            return;
        }

        int topSize = topInventory.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClaimClose(InventoryCloseEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof PledgeClaimHolder holder) {
            plugin.getPledgeManager().saveClaimInventory(holder, topInventory);
        }
    }

    private boolean isTakingAction(InventoryAction action) {
        return action == InventoryAction.PICKUP_ALL
            || action == InventoryAction.PICKUP_SOME
            || action == InventoryAction.PICKUP_HALF
            || action == InventoryAction.PICKUP_ONE
            || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
            || action == InventoryAction.DROP_ALL_SLOT
            || action == InventoryAction.DROP_ONE_SLOT;
    }
}
