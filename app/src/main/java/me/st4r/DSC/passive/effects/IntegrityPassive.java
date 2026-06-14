package me.st4r.DSC.passive.effects;

import me.st4r.DSC.passive.PassiveEffectSupport;
import me.st4r.DSC.tracker.IntegrityTracker;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class IntegrityPassive {

    private final PassiveEffectSupport support;
    private final IntegrityTracker integrityTracker;

    public IntegrityPassive(PassiveEffectSupport support, IntegrityTracker integrityTracker) {
        this.support = support;
        this.integrityTracker = integrityTracker;
    }

    public void apply(Player holder) {
        if (!integrityTracker.isEligibleForIntegrity(holder.getUniqueId())) {
            return;
        }

        PotionEffectType healthBoost = support.resolvePotionEffect("HEALTH_BOOST");
        support.applyEffect(holder, healthBoost, 0);
        support.applyNearbyEffect(holder, 3.0D, healthBoost, 0);
        support.spawnDust(holder, Color.PURPLE, 6, 0.25, 0.45, 0.25, 1.0F);
    }
}
