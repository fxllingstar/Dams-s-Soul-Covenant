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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SoulPickUpListener implements Listener {

    private final SoulItem soulItem;
    private final SoulManager soulManager;

    public SoulPickUpListener(DSC plugin) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoulDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!soulItem.isSoul(stack)) return;

        SoulType type = soulItem.getSoulType(stack);
        if (type == null) return;

        soulManager.setHolder(type, null);
        event.getItemDrop().setItemStack(soulItem.modifySoulStack(stack, soulManager.getKarma(stack), null));
    }
}
