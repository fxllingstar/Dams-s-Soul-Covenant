package me.st4r.DSC.passive;

import me.st4r.DSC.DSC;
import me.st4r.DSC.passive.effects.BraveryPassive;
import me.st4r.DSC.passive.effects.DeterminationPassive;
import me.st4r.DSC.passive.effects.IntegrityPassive;
import me.st4r.DSC.passive.effects.JusticePassive;
import me.st4r.DSC.passive.effects.PatiencePassive;
import me.st4r.DSC.passive.effects.PerseverancePassive;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

public class PassiveEffectTask {

    private static final long PASSIVE_TICK_MS = 4_000L;

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final BukkitRunnable runnable = new BukkitRunnable() {
        @Override
        public void run() {
            tick();
        }
    };

    private final DeterminationPassive determinationPassive;
    private final JusticePassive justicePassive;
    private final BraveryPassive braveryPassive;
    private final PerseverancePassive perseverancePassive;
    private final PatiencePassive patiencePassive;
    private final IntegrityPassive integrityPassive;

    public PassiveEffectTask(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();

        PassiveEffectSupport support = new PassiveEffectSupport(plugin);
        this.determinationPassive = new DeterminationPassive(support);
        this.justicePassive = new JusticePassive(support, plugin.getJusticeTracker());
        this.braveryPassive = new BraveryPassive(support);
        this.perseverancePassive = new PerseverancePassive(support, plugin.getPerseveranceTracker());
        this.patiencePassive = new PatiencePassive(support, plugin.getPatienceTracker());
        this.integrityPassive = new IntegrityPassive(support, plugin.getIntegrityTracker());
    }

    public void start() {
        runnable.runTaskTimer(plugin, 20L, PASSIVE_TICK_MS / 50L);
    }

    public void cancel() {
        runnable.cancel();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (Map.Entry<SoulType, UUID> entry : soulManager.getLiveRegistryView().entrySet()) {
            SoulType type = entry.getKey();
            UUID holderUUID = entry.getValue();
            Player holder = plugin.getServer().getPlayer(holderUUID);
            if (holder == null || !holder.isOnline()) continue;

            ItemStack heldSoul = findSoulInInventory(holder, type);
            if (heldSoul == null) continue;
            if (soulManager.isShattered(heldSoul) || soulManager.isCorrupted(heldSoul)) continue;

            switch (type) {
                case DETERMINATION -> determinationPassive.apply(holder);
                case JUSTICE -> justicePassive.apply(holder);
                case BRAVERY -> braveryPassive.apply(holder);
                case PERSEVERANCE -> perseverancePassive.apply(holder, now);
                case PATIENCE -> patiencePassive.apply(holder, now);
                case INTEGRITY -> integrityPassive.apply(holder);
                case KINDNESS -> { }
            }
        }
    }

    private ItemStack findSoulInInventory(Player player, SoulType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == type) {
                return item;
            }
        }
        return null;
    }
}
