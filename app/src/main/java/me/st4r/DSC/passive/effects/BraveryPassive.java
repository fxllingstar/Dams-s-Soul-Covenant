package me.st4r.DSC.passive.effects;

import me.st4r.DSC.passive.PassiveEffectSupport;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class BraveryPassive {

    private final PassiveEffectSupport support;

    public BraveryPassive(PassiveEffectSupport support) {
        this.support = support;
    }

    public void apply(Player holder) {
        PotionEffectType strength = support.resolvePotionEffect("STRENGTH", "INCREASE_DAMAGE");
        PotionEffectType speed = support.resolvePotionEffect("SPEED");
        support.applyEffect(holder, strength, 1);
        support.applyNearbyEffect(holder, 3.0D, speed, 0);
        support.spawnDust(holder, Color.ORANGE, 8, 0.3, 0.5, 0.3, 1.15F);
    }
}
