package me.st4r.DSC.passive;

import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulType;
import me.st4r.DSC.tracker.BraveryTracker;
import me.st4r.DSC.tracker.DeterminationTracker;
import me.st4r.DSC.tracker.IntegrityTracker;
import me.st4r.DSC.tracker.JusticeTracker;
import me.st4r.DSC.tracker.PatienceTracker;
import me.st4r.DSC.tracker.PerseveranceTracker;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

public class PassiveEffectTask {

    private static final long PASSIVE_TICK_MS = 4_000L;
    private static final int PASSIVE_DURATION_TICKS = 100;
    private static final double PASSIVE_RADIUS = 3.0D;

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final DeterminationTracker determinationTracker;
    private final JusticeTracker justiceTracker;
    private final BraveryTracker braveryTracker;
    private final PerseveranceTracker perseveranceTracker;
    private final PatienceTracker patienceTracker;
    private final IntegrityTracker integrityTracker;
    private final BukkitRunnable runnable = new BukkitRunnable() {
        @Override
        public void run() {
            tick();
        }
    };

    public PassiveEffectTask(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
        this.determinationTracker = plugin.getDeterminationTracker();
        this.justiceTracker = plugin.getJusticeTracker();
        this.braveryTracker = plugin.getBraveryTracker();
        this.perseveranceTracker = plugin.getPerseveranceTracker();
        this.patienceTracker = plugin.getPatienceTracker();
        this.integrityTracker = plugin.getIntegrityTracker();
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
                case DETERMINATION -> applyDetermination(holder);
                case JUSTICE -> applyJustice(holder);
                case BRAVERY -> applyBravery(holder);
                case PERSEVERANCE -> applyPerseverance(holder, now);
                case PATIENCE -> applyPatience(holder, now);
                case INTEGRITY -> applyIntegrity(holder);
                case KINDNESS -> { }
            }
        }
    }

    private void applyDetermination(Player holder) {
        PotionEffectType resistance = resolvePotionEffect("DAMAGE_RESISTANCE");
        if (resistance != null) {
            applyEffect(holder, resistance, 1);
            applyNearbyEffect(holder, resistance, 0);
        }
        holder.getWorld().spawnParticle(Particle.DUST, holder.getLocation().add(0, 1.0, 0), 6, 0.25, 0.45, 0.25, new Particle.DustOptions(Color.RED, 1.0F));
    }

    private void applyJustice(Player holder) {
        holder.getWorld().spawnParticle(Particle.DUST, holder.getLocation().add(0, 1.0, 0), 6, 0.25, 0.45, 0.25, new Particle.DustOptions(Color.YELLOW, 1.0F));
    }

    private void applyBravery(Player holder) {
        PotionEffectType strength = resolvePotionEffect("INCREASE_DAMAGE");
        if (strength != null) {
            applyEffect(holder, strength, 1);
        }
        applyNearbyEffect(holder, PotionEffectType.SPEED, 0);
        holder.getWorld().spawnParticle(Particle.DUST, holder.getLocation().add(0, 1.0, 0), 8, 0.3, 0.5, 0.3, new Particle.DustOptions(Color.ORANGE, 1.15F));
    }

    private void applyPerseverance(Player holder, long now) {
        if (perseveranceTracker.hasFilledWithPerseverance(holder.getUniqueId())) {
            PotionEffectType resistance = resolvePotionEffect("DAMAGE_RESISTANCE");
            if (resistance != null) {
                applyEffect(holder, resistance, 1);
            }
            applyEffect(holder, PotionEffectType.SPEED, 0);
            holder.getWorld().spawnParticle(Particle.DUST, holder.getLocation().add(0, 1.0, 0), 7, 0.25, 0.4, 0.25, new Particle.DustOptions(Color.FUCHSIA, 1.0F));
        } else {
            perseveranceTracker.refreshDormancy(holder.getUniqueId(), now);
        }
    }

    private void applyPatience(Player holder, long now) {
        UUID holderUUID = holder.getUniqueId();
        patienceTracker.markSoulHeldIfAbsent(holderUUID, now);

        long heldDays = patienceTracker.getSoulHeldDays(holderUUID, now);
        if (heldDays <= 0) {
            return;
        }

        PotionEffectType haste = resolvePotionEffect("FAST_DIGGING");
        if (haste == null) {
            return;
        }

        if (heldDays >= 7) {
            applyEffect(holder, haste, 1);
            applyNearbyEffect(holder, haste, 0);
        } else {
            applyEffect(holder, haste, 0);
            if (heldDays >= 4) {
                applyNearbyEffect(holder, haste, 0);
            }
        }

        holder.getWorld().spawnParticle(Particle.DUST, holder.getLocation().add(0, 1.0, 0), 5, 0.2, 0.35, 0.2, new Particle.DustOptions(Color.AQUA, 1.0F));
    }

    private void applyIntegrity(Player holder) {
        if (!integrityTracker.isEligibleForIntegrity(holder.getUniqueId())) {
            return;
        }

        PotionEffectType healthBoost = resolvePotionEffect("HEALTH_BOOST");
        if (healthBoost != null) {
            applyEffect(holder, healthBoost, 0);
        }
        holder.getWorld().spawnParticle(Particle.DUST, holder.getLocation().add(0, 1.0, 0), 6, 0.25, 0.45, 0.25, new Particle.DustOptions(Color.PURPLE, 1.0F));
    }

    private void applyEffect(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, PASSIVE_DURATION_TICKS, amplifier, true, false, false), true);
    }

    private void applyNearbyEffect(Player source, PotionEffectType type, int amplifier) {
        for (Entity entity : source.getNearbyEntities(PASSIVE_RADIUS, PASSIVE_RADIUS, PASSIVE_RADIUS)) {
            if (!(entity instanceof Player nearby) || nearby.getUniqueId().equals(source.getUniqueId())) continue;
            nearby.addPotionEffect(new PotionEffect(type, PASSIVE_DURATION_TICKS, amplifier, true, false, false), true);
        }
    }

    private PotionEffectType resolvePotionEffect(String name) {
        return PotionEffectType.getByName(name);
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
