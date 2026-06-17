package me.st4r.DSC.soul;

import me.st4r.DSC.DSC;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

public class SoulParticleTask extends BukkitRunnable {

    private final DSC plugin;
    private final SoulManager soulManager;

    public SoulParticleTask(DSC plugin) {
        this.plugin = plugin;
        this.soulManager = plugin.getSoulManager();
    }

    @Override
    public void run() {
        for (Map.Entry<SoulType, UUID> entry : soulManager.getLiveRegistryView().entrySet()) {
            SoulType type = entry.getKey();
            UUID playerUUID = entry.getValue();

            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline()) continue;
            ItemStack soulItem = findSoulInInventory(player, type);
            if (soulItem == null) continue;

            Location loc = player.getLocation().add(0, 1.0, 0);

            if (soulManager.isShattered(soulItem)) {
                player.getWorld().spawnParticle(Particle.ASH, loc, 5, 0.35, 0.45, 0.35, 0.02);
                player.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.25, 0), 2, 0.2, 0.2, 0.2, 0.005);
            } else if (soulManager.isCorrupted(soulItem)) {
                player.getWorld().spawnParticle(Particle.SMOKE, loc, 4, 0.25, 0.45, 0.25, 0.01);
                player.getWorld().spawnParticle(Particle.WITCH, loc, 2, 0.2, 0.35, 0.2, 0.0);
            } else {

                Color color = getBukkitColor(type);
                Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.2F);
                player.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.3, 0.45, 0.3, dustOptions);
                player.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.35, 0), 3, 0.15, 0.2, 0.15, new Particle.DustOptions(color, 0.9F));
            }
        }
    }

    private ItemStack findSoulInInventory(Player player, SoulType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && plugin.getSoulItem().isSoul(item)) {
                if (plugin.getSoulItem().getSoulType(item) == type) {
                    return item;
                }
            }
        }
        return null;
    }

    private Color getBukkitColor(SoulType type) {
        return switch (type) {
            case KINDNESS -> Color.GREEN;
            case DETERMINATION -> Color.RED;
            case BRAVERY -> Color.ORANGE;
            case JUSTICE -> Color.YELLOW;
            case PATIENCE -> Color.AQUA;
            case INTEGRITY -> Color.PURPLE;
            case PERSEVERANCE -> Color.FUCHSIA;
        };
    }
}
