package me.st4r.ASC.passive.effects;

import org.bukkit.Color;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import me.st4r.ASC.passive.PassiveEffectSupport;
import me.st4r.ASC.tracker.JusticeTracker;

public class JusticePassive {

    private final PassiveEffectSupport support;
    private final JusticeTracker justiceTracker;

    public JusticePassive(PassiveEffectSupport support, JusticeTracker justiceTracker) {
        this.support = support;
        this.justiceTracker = justiceTracker;
    }

    public void apply(Player holder) {
        PotionEffectType glow = support.resolvePotionEffect("GLOWING");
        for (Entity entity : holder.getNearbyEntities(3.0D, 3.0D, 3.0D)) {
            if (!(entity instanceof Player nearby)) continue;
            if (justiceTracker.isMostWanted(nearby.getUniqueId()) || justiceTracker.isBountyHolder(nearby.getUniqueId())) {
                support.applyEffect(nearby, glow, 0);
            }
        }
        support.spawnDust(holder, Color.YELLOW, 6, 0.25, 0.45, 0.25, 1.0F);
    }
}
