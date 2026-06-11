package me.st4r.DSC.world;

import me.st4r.DSC.world.SoulStateManager.SoulState;
import me.st4r.DSC.world.SoulStateManager.SoulStateSnapshot;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Monster;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FractureHandler implements Listener {

    private SoulState currentState = SoulState.HEALTHY;

    public void applySoulState(SoulStateSnapshot snapshot) {
        currentState = snapshot.state();

        for (World world : org.bukkit.Bukkit.getWorlds()) {
            if (!isOverworld(world)) continue;

            if (currentState == SoulState.FRACTURE) {
                world.setTime(18_000L);
                world.setStorm(true);
            }

            for (Player player : world.getPlayers()) {
                applyPlayerAtmosphere(player);
            }
        }
    }

    public boolean isFractured() {
        return currentState == SoulState.FRACTURE;
    }

    public boolean isDegradedOrWorse() {
        return currentState == SoulState.DEGRADED || currentState == SoulState.FRACTURE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        if (!isOverworld(event.getBlock().getWorld())) return;

        if (currentState == SoulState.FRACTURE) {
            event.setCancelled(true);
            return;
        }

        if (currentState == SoulState.DEGRADED && Math.random() < 0.5D) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isOverworld(event.getLocation().getWorld())) return;

        if (event.getEntity() instanceof Monster monster && isDegradedOrWorse()) {
            PotionEffectType strength = PotionEffectType.getByName("STRENGTH");
            if (strength == null) {
                strength = PotionEffectType.getByName("INCREASE_DAMAGE");
            }
            if (strength != null) {
                monster.addPotionEffect(new PotionEffect(strength, Integer.MAX_VALUE, currentState == SoulState.FRACTURE ? 1 : 0, true, false, false));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (currentState != SoulState.FRACTURE) return;
        if (!isOverworld(event.getLocation().getWorld())) return;

        if (event.getEntity() instanceof Animals || event.getEntity() instanceof NPC) {
            event.setCancelled(true);
        }
    }

    private void applyPlayerAtmosphere(Player player) {
        PotionEffectType darkness = PotionEffectType.getByName("DARKNESS");
        if (darkness == null) return;

        if (currentState == SoulState.FRACTURE) {
            player.addPotionEffect(new PotionEffect(darkness, 120, 0, true, false, false), true);
        } else if (currentState == SoulState.DEGRADED) {
            player.addPotionEffect(new PotionEffect(darkness, 60, 0, true, false, false), true);
        }
    }

    private boolean isOverworld(World world) {
        return world != null && world.getEnvironment() == World.Environment.NORMAL;
    }
}
