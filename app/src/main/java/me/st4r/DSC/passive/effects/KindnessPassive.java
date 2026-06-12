package me.st4r.DSC.passive.effects;

import me.st4r.DSC.passive.PassiveEffectSupport;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

public class KindnessPassive {

    private final PassiveEffectSupport support;
    private final SoulItem soulItem;
    private final SoulManager soulManager;

    public KindnessPassive(PassiveEffectSupport support, SoulItem soulItem, SoulManager soulManager) {
        this.support = support;
        this.soulItem = soulItem;
        this.soulManager = soulManager;
    }

    public void apply(Player holder) {
        ItemStack soul = findHeldKindnessSoul(holder);
        if (soul == null || soulManager.isShattered(soul)) {
            return;
        }

        support.spawnDust(holder, Color.fromRGB(60, 255, 90), 8, 0.28, 0.45, 0.28, 1.0F);
        support.applyNearbyEffect(holder, 3.0D, resolveRegeneration(), 0);
    }

    public ItemStack findHeldKindnessSoul(Player player) {
        if (player == null) return null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == SoulType.KINDNESS) {
                return item;
            }
        }
        return null;
    }

    private PotionEffectType resolveRegeneration() {
        return support.resolvePotionEffect("REGENERATION", "SLOW_REGENERATION");
    }
}
