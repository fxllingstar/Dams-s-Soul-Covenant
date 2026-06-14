package me.st4r.DSC.passive.effects;

import me.st4r.DSC.passive.PassiveEffectSupport;
import me.st4r.DSC.tracker.PatienceTracker;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class PatiencePassive {

    private final PassiveEffectSupport support;
    private final PatienceTracker patienceTracker;

    public PatiencePassive(PassiveEffectSupport support, PatienceTracker patienceTracker) {
        this.support = support;
        this.patienceTracker = patienceTracker;
    }

    public void apply(Player holder, long now) {
        patienceTracker.markSoulHeldIfAbsent(holder.getUniqueId(), now);

        long heldDays = patienceTracker.getSoulHeldDays(holder.getUniqueId(), now);
        if (heldDays < 4) {
            return;
        }

        PotionEffectType haste = support.resolvePotionEffect("HASTE", "FAST_DIGGING");
        int holderAmplifier = heldDays >= 7 ? 1 : 0;
        int nearbyAmplifier = 0;

        support.applyEffect(holder, haste, holderAmplifier);
        support.applyNearbyEffect(holder, 3.0D, haste, nearbyAmplifier);
        support.spawnDust(holder, Color.AQUA, 5, 0.2, 0.35, 0.2, 1.0F);
    }
}
