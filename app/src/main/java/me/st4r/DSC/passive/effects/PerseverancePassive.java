package me.st4r.DSC.passive.effects;

import me.st4r.DSC.passive.PassiveEffectSupport;
import me.st4r.DSC.tracker.PerseveranceTracker;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class PerseverancePassive {

    private final PassiveEffectSupport support;
    private final PerseveranceTracker perseveranceTracker;

    public PerseverancePassive(PassiveEffectSupport support, PerseveranceTracker perseveranceTracker) {
        this.support = support;
        this.perseveranceTracker = perseveranceTracker;
    }

    public void apply(Player holder, long now) {
        if (perseveranceTracker.hasFilledWithPerseverance(holder.getUniqueId())) {
            PotionEffectType resistance = support.resolvePotionEffect("RESISTANCE", "DAMAGE_RESISTANCE");
            PotionEffectType speed = support.resolvePotionEffect("SPEED");
            support.applyEffect(holder, resistance, 1);
            support.applyEffect(holder, speed, 0);
            support.spawnDust(holder, Color.FUCHSIA, 7, 0.25, 0.4, 0.25, 1.0F);
            return;
        }

        perseveranceTracker.refreshDormancy(holder.getUniqueId(), now);
    }
}
