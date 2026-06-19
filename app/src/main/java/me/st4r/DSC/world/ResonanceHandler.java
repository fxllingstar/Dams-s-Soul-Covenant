package me.st4r.DSC.world;

import me.st4r.DSC.world.SoulStateManager.SoulStateSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

@SuppressWarnings("deprecation")

public class ResonanceHandler implements Listener {

    private SoulStateSnapshot currentSnapshot;

    public void applySoulState(SoulStateSnapshot snapshot) {
        currentSnapshot = snapshot;
    }

    public boolean canEnterResonance() {
        return currentSnapshot != null
            && currentSnapshot.allSoulsExist()
            && currentSnapshot.corruptedSouls() < 3;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!isResonanceWorld(event.getTo() == null ? null : event.getTo().getWorld())) {
            return;
        }

        if (canEnterResonance()) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "The Resonance refuses to open. The souls are not aligned.");
    }

    private boolean isResonanceWorld(World world) {
        return world != null && world.getName().toLowerCase().contains("resonance");
    }
}
