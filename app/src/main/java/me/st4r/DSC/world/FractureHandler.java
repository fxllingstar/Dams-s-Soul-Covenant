package me.st4r.DSC.world;

import me.st4r.DSC.world.SoulStateManager.SoulState;
import me.st4r.DSC.world.SoulStateManager.SoulStateSnapshot;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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
    private SoulState previousState = SoulState.HEALTHY;

    public void applySoulState(SoulStateSnapshot snapshot) {
        previousState = currentState;
        currentState = snapshot.state();

        if (currentState != previousState) {
            handleStateTransition(previousState, currentState);
        }

        if (currentState == SoulState.FRACTURE) {
            for (World world : org.bukkit.Bukkit.getWorlds()) {
                if (!isOverworld(world)) continue;
                world.setTime(18_000L);
                world.setStorm(true);
                world.setWeatherDuration(Integer.MAX_VALUE);
                world.setThunderDuration(Integer.MAX_VALUE);
            }
        }

        for (World world : org.bukkit.Bukkit.getWorlds()) {
            if (!isOverworld(world)) continue;
            for (Player player : world.getPlayers()) {
                applyPlayerAtmosphere(player);
            }
        }
    }

    private void handleStateTransition(SoulState from, SoulState to) {

        if (to == SoulState.FRACTURE) {
            for (World world : org.bukkit.Bukkit.getWorlds()) {
                if (!isOverworld(world)) continue;
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            }
            return;
        }

       
        if (from == SoulState.FRACTURE) {
            for (World world : org.bukkit.Bukkit.getWorlds()) {
                if (!isOverworld(world)) continue;
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                world.setStorm(false);
                world.setWeatherDuration(0);
                world.setThunderDuration(0);
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
            PotionEffectType strength = Registry.EFFECT.get(NamespacedKey.minecraft("strength"));
            if (strength != null) {
                monster.addPotionEffect(new PotionEffect(
                    strength,
                    Integer.MAX_VALUE,
                    currentState == SoulState.FRACTURE ? 1 : 0,
                    true, false, false
                ));
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
        PotionEffectType darkness = Registry.EFFECT.get(NamespacedKey.minecraft("darkness"));
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