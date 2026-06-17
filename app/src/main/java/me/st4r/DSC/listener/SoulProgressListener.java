package me.st4r.DSC.listener;

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
import me.st4r.DSC.world.SoulStateManager;
import me.st4r.DSC.world.SoulStateManager.SoulStateSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SoulProgressListener implements Listener {

    private static final long BRAVERY_COMBAT_WINDOW_MILLIS = 5L * 60L * 1000L;
    private static final long PATIENCE_WINDOW_MILLIS = 15L * 60L * 1000L;
    private static final long EVALUATION_PERIOD_TICKS = 20L * 30L;

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final SoulStateManager soulStateManager;
    private final BraveryTracker braveryTracker;
    private final DeterminationTracker determinationTracker;
    private final JusticeTracker justiceTracker;
    private final PerseveranceTracker perseveranceTracker;
    private final IntegrityTracker integrityTracker;
    private final PatienceTracker patienceTracker;

    private final Map<UUID, Long> onlineSince = new HashMap<>();
    private final Map<UUID, Long> lastAggressionAt = new HashMap<>();
    private final Map<UUID, Long> lastDeathAt = new HashMap<>();

    public SoulProgressListener(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
        this.soulStateManager = plugin.getSoulStateManager();
        this.braveryTracker = plugin.getBraveryTracker();
        this.determinationTracker = plugin.getDeterminationTracker();
        this.justiceTracker = plugin.getJusticeTracker();
        this.perseveranceTracker = plugin.getPerseveranceTracker();
        this.integrityTracker = plugin.getIntegrityTracker();
        this.patienceTracker = plugin.getPatienceTracker();

        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            onlineSince.put(playerUUID, now);
            lastAggressionAt.put(playerUUID, now);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                evaluatePeriodicRewards();
            }
        }.runTaskTimer(plugin, 40L, EVALUATION_PERIOD_TICKS);
    }

    public void resetRuntimeState() {
        long now = System.currentTimeMillis();
        onlineSince.clear();
        lastAggressionAt.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            onlineSince.put(playerUUID, now);
            lastAggressionAt.put(playerUUID, now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        long now = System.currentTimeMillis();
        UUID playerUUID = event.getPlayer().getUniqueId();
        onlineSince.put(playerUUID, now);
        lastAggressionAt.put(playerUUID, now);
        perseveranceTracker.recordLogin(playerUUID, now);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        long now = System.currentTimeMillis();
        UUID playerUUID = event.getPlayer().getUniqueId();
        perseveranceTracker.recordLogout(playerUUID, now);
        onlineSince.remove(playerUUID);
        lastAggressionAt.remove(playerUUID);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null) return;

        long now = System.currentTimeMillis();
        UUID attackerUUID = attacker.getUniqueId();
        lastAggressionAt.put(attackerUUID, now);
        braveryTracker.recordCombatEngagement(attackerUUID, now);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimUUID = victim.getUniqueId();
        long now = System.currentTimeMillis();
        lastDeathAt.put(victimUUID, now);

        boolean inCombatRecently = braveryTracker.wasInCombatRecently(victimUUID, now, BRAVERY_COMBAT_WINDOW_MILLIS);
        if (inCombatRecently) {
            braveryTracker.recordDeath(victimUUID);
        } else {
            braveryTracker.recordCowardlyAct(victimUUID);
        }

        if (braveryTracker.isRewardReady(victimUUID) && plugin.grantSoul(victim, SoulType.BRAVERY)) {
            braveryTracker.markRewardGranted(victimUUID);
        } else if (!inCombatRecently && braveryTracker.getCowardlyActCount(victimUUID) >= BraveryTracker.DEATHS_REQUIRED) {
            shatterHeldSoul(victim, SoulType.BRAVERY);
        }

        determinationTracker.recordDeath(victimUUID);
        if (determinationTracker.isRewardReady()) {
            UUID leaderUUID = determinationTracker.getCurrentLeader();
            Player leader = leaderUUID == null ? null : Bukkit.getPlayer(leaderUUID);
            if (leader != null && plugin.grantSoul(leader, SoulType.DETERMINATION)) {
                determinationTracker.markRewardGranted(leaderUUID);
            }
        }

        boolean victimWasWanted = justiceTracker.isMostWanted(victimUUID) || justiceTracker.isBountyHolder(victimUUID);
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victimUUID)) {
            UUID killerUUID = killer.getUniqueId();
            justiceTracker.recordPlayerKill(killerUUID, victimUUID, now);
            justiceTracker.setBountyHolder(killerUUID, justiceTracker.isMostWanted(killerUUID));

            if (victimWasWanted && plugin.grantSoul(killer, SoulType.JUSTICE)) {
                killer.sendMessage(ChatColor.AQUA + "Justice answers the fall of " + victim.getName() + ".");
            } else if (!victimWasWanted && justiceTracker.getInnocentKillCount(killerUUID) >= 3) {
                shatterHeldSoul(killer, SoulType.JUSTICE);
            }

            perseveranceTracker.recordSameSourceDeath(victimUUID, killerUUID, now);
        }

        justiceTracker.recordDeath(victimUUID);

        if (perseveranceTracker.hasFilledWithPerseverance(victimUUID)) {
            if (plugin.grantSoul(victim, SoulType.PERSEVERANCE)) {
                perseveranceTracker.markFillConsumed(victimUUID);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Animals animals)) {
            return;
        }

        Player killer = animals.getKiller();
        if (killer == null) {
            return;
        }

        UUID killerUUID = killer.getUniqueId();
        ItemStack patienceSoul = findSoulInInventory(killer, SoulType.PATIENCE);
        if (patienceSoul == null || soulManager.isShattered(patienceSoul)) {
            return;
        }

        int slaughterCount = patienceTracker.recordAnimalSlaughter(killerUUID, System.currentTimeMillis());
        if (patienceTracker.shouldShatter(killerUUID, System.currentTimeMillis()) || slaughterCount >= PatienceTracker.ANIMAL_SLAY_THRESHOLD) {
            soulManager.shatter(patienceSoul);
            killer.sendMessage(ChatColor.RED + "The Soul of Patience cracks under your cruelty.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack patienceSoul = findSoulInInventory(player, SoulType.PATIENCE);
        if (patienceSoul == null || soulManager.isShattered(patienceSoul)) {
            return;
        }

        if (!isForcedAccessTarget(event.getBlock().getType())) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        int forcedAccessCount = patienceTracker.recordForcedAccessAttempt(playerUUID, System.currentTimeMillis());
        if (patienceTracker.shouldShatter(playerUUID, System.currentTimeMillis()) || forcedAccessCount >= PatienceTracker.FORCED_ACCESS_THRESHOLD) {
            soulManager.shatter(patienceSoul);
            player.sendMessage(ChatColor.RED + "The Soul of Patience cracks under your forced reach.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        SoulStateSnapshot snapshot = soulStateManager.evaluateNow();
        String message;
        if (snapshot.allSoulsExist() && snapshot.corruptedSouls() == 0) {
            message = ChatColor.AQUA + "The Ender Dragon falls. Resonance blossoms, and the current cycle is fulfilled.";
        } else if (snapshot.corruptedSouls() >= 4) {
            message = ChatColor.DARK_RED + "The Ender Dragon falls, but the Fracture swallows the era.";
        } else {
            message = ChatColor.GOLD + "The Ender Dragon falls. The current cycle ends.";
        }

        plugin.resetCycle(message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuitAfterDeath(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        Long deathAt = lastDeathAt.get(playerUUID);
        if (deathAt == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - deathAt <= BRAVERY_COMBAT_WINDOW_MILLIS) {
            int incidents = determinationTracker.recordLogoutOnDeathIncident(playerUUID);
            if (incidents >= 3) {
                shatterHeldSoul(event.getPlayer(), SoulType.DETERMINATION);
            }
        }

        int abandonedRuns = perseveranceTracker.recordAbandonedRun(playerUUID);
        if (abandonedRuns >= PerseveranceTracker.FILL_TRIGGER_STREAK) {
            shatterHeldSoul(event.getPlayer(), SoulType.PERSEVERANCE);
        }
    }

    private void evaluatePeriodicRewards() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();

            if (!soulStateManager.isSoulPresent(SoulType.PATIENCE) && isPatient(playerUUID, now)) {
                if (plugin.grantSoul(player, SoulType.PATIENCE)) {
                    patientize(playerUUID);
                }
            }

            if (integrityTracker.isEligibleForIntegrity(playerUUID)) {
                plugin.grantSoul(player, SoulType.INTEGRITY);
            }
        }

        if (determinationTracker.isRewardReady()) {
            UUID leaderUUID = determinationTracker.getCurrentLeader();
            Player leader = leaderUUID == null ? null : Bukkit.getPlayer(leaderUUID);
            if (leader != null) {
                if (plugin.grantSoul(leader, SoulType.DETERMINATION)) {
                    determinationTracker.markRewardGranted(leaderUUID);
                }
            }
        }
    }

    private boolean isPatient(UUID playerUUID, long now) {
        Long joinedAt = onlineSince.get(playerUUID);
        if (joinedAt == null) return false;

        Long aggressionAt = lastAggressionAt.get(playerUUID);
        long referenceTime = aggressionAt == null ? joinedAt : Math.max(joinedAt, aggressionAt);
        return now - referenceTime >= PATIENCE_WINDOW_MILLIS;
    }

    private void patientize(UUID playerUUID) {
        long now = System.currentTimeMillis();
        onlineSince.put(playerUUID, now);
        lastAggressionAt.put(playerUUID, now);
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }

    private ItemStack findSoulInInventory(Player player, SoulType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == type) {
                return item;
            }
        }
        return null;
    }

    private void shatterHeldSoul(Player player, SoulType type) {
        ItemStack soul = findSoulInInventory(player, type);
        if (soul == null) {
            return;
        }

        soulManager.shatter(soul);
        player.sendMessage(ChatColor.DARK_RED + "The Soul of " + type.getDisplayName() + " shatters.");
    }

    private boolean isForcedAccessTarget(Material material) {
        String name = material.name();
        return name.contains("CHEST")
            || name.contains("BARREL")
            || name.contains("TRAPDOOR")
            || name.contains("DOOR")
            || name.contains("FENCE_GATE")
            || name.contains("SHULKER_BOX")
            || name.contains("HOPPER");
    }
}
