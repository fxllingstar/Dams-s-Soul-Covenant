package me.st4r.DSC.passive;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.Objects;

@SuppressWarnings("deprecation")
public final class PassiveEffectSupport {

    private static final int PASSIVE_DURATION_TICKS = 100;

    private final JavaPlugin plugin;

    PassiveEffectSupport(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public PotionEffectType resolvePotionEffect(String... names) {
        return Arrays.stream(names)
            .map(PotionEffectType::getByName)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    public void applyEffect(Player player, PotionEffectType type, int amplifier) {
        if (player == null || type == null) return;
        player.addPotionEffect(new PotionEffect(type, PASSIVE_DURATION_TICKS, amplifier, true, false, false), true);
    }

    public void applyNearbyEffect(Player source, double radius, PotionEffectType type, int amplifier) {
        if (source == null || type == null) return;
        for (Entity entity : source.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player nearby) || nearby.getUniqueId().equals(source.getUniqueId())) continue;
            nearby.addPotionEffect(new PotionEffect(type, PASSIVE_DURATION_TICKS, amplifier, true, false, false), true);
        }
    }

    public void spawnDust(Player player, Color color, int count, double x, double y, double z, float size) {
        if (player == null || color == null) return;
        player.getWorld().spawnParticle(
            Particle.DUST,
            player.getLocation().add(0, 1.0, 0),
            count,
            x,
            y,
            z,
            new Particle.DustOptions(color, size)
        );
    }
}
