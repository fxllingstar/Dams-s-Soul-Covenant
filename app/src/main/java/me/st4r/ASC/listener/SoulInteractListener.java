package me.st4r.ASC.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import me.st4r.ASC.ASC;
import me.st4r.ASC.altar.SoulAltar;
import me.st4r.ASC.soul.SoulItem;

public class SoulInteractListener implements Listener {

    private final SoulAltar soulAltar;
    private final SoulItem soulItem;

    public SoulInteractListener(ASC plugin) {
        this.soulAltar = plugin.getSoulAltar();
        this.soulItem = plugin.getSoulItem();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoulInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (!soulAltar.isAltar(clickedBlock)) return;

        ItemStack heldItem = event.getPlayer().getInventory().getItemInMainHand();
        if (!soulItem.isSoul(heldItem) && heldItem.getType() != Material.AIR) return;

        if (soulAltar.handleInteract(event.getPlayer(), clickedBlock, heldItem)) {
            event.setCancelled(true);
        }
    }
}
