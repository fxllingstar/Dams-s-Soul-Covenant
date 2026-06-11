package me.st4r.DSC.passive.effects;

import me.st4r.DSC.passive.PassiveEffectSupport;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class DeterminationPassive {

    private final PassiveEffectSupport support;

    public DeterminationPassive(PassiveEffectSupport support) {
        this.support = support;
    }

    public void apply(Player holder) {
        PotionEffectType resistance = support.resolvePotionEffect("RESISTANCE", "DAMAGE_RESISTANCE");
        support.applyEffect(holder, resistance, 1);
        support.applyNearbyEffect(holder, 3.0D, resistance, 0);
        support.spawnDust(holder, Color.RED, 6, 0.25, 0.45, 0.25, 1.0F);
    }
}
