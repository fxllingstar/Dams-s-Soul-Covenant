package me.st4r.DSC.tracker;

import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulType;
import me.st4r.DSC.world.SoulStateManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class KindnessTracker implements Listener {

    private static final int OFFENSIVE_USE_THRESHOLD = 10;
    private static final int OFFENSIVE_KARMA_PENALTY = 1;

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final SoulStateManager soulStateManager;

    private final NamespacedKey offensiveUsesKey;
    private final NamespacedKey healthModifierKey;

    private int totalServerSaves = 0;
    private boolean soulSpawned = false;
    private final Map<UUID, Integer> playerContributions = new HashMap<>();
    private final Map<String, Integer> dailyPairsSaveRegistry = new HashMap<>();

    public KindnessTracker(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
        this.soulStateManager = plugin.getSoulStateManager();
        this.offensiveUsesKey = new NamespacedKey(plugin, "kindness_offensive_uses");
        this.healthModifierKey = new NamespacedKey(plugin, "kindness_max_health");

        startPassiveHeartEngine();
    }

    /* ==========================================
       1. SUMMONING & SPAWN LOGIC ENGINE
       ========================================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player rescuer)) return;

        boolean isHealingPotion = potion.getEffects().stream().anyMatch(effect ->
                effect.getType().equals(PotionEffectType.REGENERATION)
                        || effect.getType().equals(PotionEffectType.INSTANT_HEALTH));
        if (!isHealingPotion) return;

        for (Entity entity : event.getAffectedEntities()) {
            if (!(entity instanceof Player target) || target.equals(rescuer)) continue;

            if (target.getHealth() <= 8.0) {
                String dailyKey = LocalDate.now() + "_" + rescuer.getUniqueId() + "_" + target.getUniqueId();
                int currentDailySaves = dailyPairsSaveRegistry.getOrDefault(dailyKey, 0);

                if (currentDailySaves < 3) {
                    dailyPairsSaveRegistry.put(dailyKey, currentDailySaves + 1);
                    playerContributions.put(rescuer.getUniqueId(), playerContributions.getOrDefault(rescuer.getUniqueId(), 0) + 1);
                    totalServerSaves++;

                    plugin.sendSoulProgress(rescuer, SoulType.KINDNESS, totalServerSaves, 30);

                    if (totalServerSaves >= 30 && !soulSpawned) {
                        manifestKindnessSoul();
                    }
                    break;
                }
            }
        }
    }

    private void manifestKindnessSoul() {
        if (soulStateManager.isSoulPresent(SoulType.KINDNESS)) {
            soulSpawned = true;
            return;
        }

        UUID topContributor = playerContributions.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (topContributor == null) return;
        soulSpawned = true;

        Player winner = Bukkit.getPlayer(topContributor);

        Bukkit.broadcastMessage("§a§l§m---------------------------------------");
        Bukkit.broadcastMessage("§a§l[ATARAXIA] §eThe collective weight of genuine protection has summoned the physical §a§lSoul of Kindness§e into the world!");
        Bukkit.broadcastMessage("§a§l§m---------------------------------------");

        if (winner != null && winner.isOnline()) {
            grantKindnessSoul(winner, true);
        } else {
            ItemStack kindnessSoul = soulItem.create(SoulType.KINDNESS);
            Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            Bukkit.getWorlds().get(0).dropItemNaturally(spawn, kindnessSoul);
        }
    }

    public boolean forceReward(Player target) {
        if (target == null || soulStateManager.isSoulPresent(SoulType.KINDNESS)) {
            return false;
        }

        totalServerSaves = Math.max(totalServerSaves, 30);
        soulSpawned = true;
        return grantKindnessSoul(target, false);
    }

    public void clear() {
        totalServerSaves = 0;
        soulSpawned = false;
        playerContributions.clear();
        dailyPairsSaveRegistry.clear();
    }

    private boolean grantKindnessSoul(Player winner, boolean sendPersonalMessage) {
        if (winner == null || !winner.isOnline()) {
            return false;
        }

        ItemStack kindnessSoul = soulItem.create(SoulType.KINDNESS, winner.getUniqueId());
        Map<Integer, ItemStack> overflow = winner.getInventory().addItem(kindnessSoul);
        if (overflow.isEmpty()) {
            soulManager.setHolder(SoulType.KINDNESS, winner.getUniqueId());
            soulManager.announceSoulAcquired(winner, SoulType.KINDNESS);
        }
        if (!overflow.isEmpty()) {
            winner.getWorld().dropItemNaturally(winner.getLocation(), kindnessSoul);
        }
        if (sendPersonalMessage) {
            winner.sendMessage("§a§k!§r §aYour profound aura of protection has materialized the Soul of Kindness directly to you. §a§k!");
        }
        return true;
    }

    /* ==========================================
       2. PASSIVE EFFECT TICK SCHEDULER
       ========================================== */

    private void startPassiveHeartEngine() {
        new BukkitRunnable() {

            private final AttributeModifier healthModifier = new AttributeModifier(
                    healthModifierKey, 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY
            );

            @Override
            public void run() {
                UUID activeHolderUUID = soulManager.getHolder(SoulType.KINDNESS);
                Player systemHolder = activeHolderUUID != null ? Bukkit.getPlayer(activeHolderUUID) : null;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
                    if (maxHealthAttribute == null) continue;

                    boolean hasModifierActive = maxHealthAttribute.getModifiers().stream()
                            .anyMatch(mod -> mod.getKey().equals(healthModifierKey));

                    if (player.equals(systemHolder) && holdsSoulAsset(player) && !isSoulAssetShattered(player)) {
                        if (!hasModifierActive) {
                            maxHealthAttribute.addModifier(healthModifier);
                        }

                        for (Entity localizedEntity : player.getNearbyEntities(3.0, 3.0, 3.0)) {
                            if (localizedEntity instanceof Player nearbyPlayer) {
                                nearbyPlayer.addPotionEffect(new PotionEffect(
                                        PotionEffectType.REGENERATION, 35, 0, true, true, true
                                ));
                            }
                        }
                        continue;
                    }

                    if (hasModifierActive) {
                        maxHealthAttribute.removeModifier(healthModifier);
                    }
                }
            }

            private boolean holdsSoulAsset(Player p) {
                for (ItemStack i : p.getInventory().getContents()) {
                    if (i != null && soulItem.isSoul(i) && soulItem.getSoulType(i) == SoulType.KINDNESS) return true;
                }
                return false;
            }

            private boolean isSoulAssetShattered(Player p) {
                for (ItemStack i : p.getInventory().getContents()) {
                    if (i != null && soulItem.isSoul(i) && soulItem.getSoulType(i) == SoulType.KINDNESS) {
                        return soulManager.isShattered(i);
                    }
                }
                return false;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /* ==========================================
       3. RIGHT-CLICK SACRIFICIAL ACTIVE ABILITY
       ========================================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClickPlayer(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player holder = event.getPlayer();
        ItemStack heldItem = holder.getInventory().getItemInMainHand();

        if (!soulItem.isSoul(heldItem) || soulItem.getSoulType(heldItem) != SoulType.KINDNESS) return;

        if (soulManager.isShattered(heldItem)) {
            holder.sendMessage("§cThe Soul of Kindness is currently shattered. Its active properties are dormant.");
            return;
        }

        if (holder.getHealth() <= 2.0 || holder.getFoodLevel() < 10) {
            holder.sendMessage("§cYou are too weak to sacrifice your life force right now.");
            return;
        }

        double maxTargetHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (target.getHealth() >= maxTargetHealth) {
            holder.sendMessage("§c" + target.getName() + " is already at maximum health.");
            return;
        }

        holder.setHealth(holder.getHealth() - 2.0);
        holder.setFoodLevel(holder.getFoodLevel() - 10);
        target.setHealth(Math.min(target.getHealth() + 6.0, maxTargetHealth));

        soulManager.addKarma(heldItem, 5);

        holder.sendMessage("§a❤ You sacrificed your physical state to restore " + target.getName() + ".");
        target.sendMessage("§a❤ " + holder.getName() + " has sacrificed their own vitality to mend your wounds.");
        target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1.2, 0), 6, 0.2, 0.2, 0.2, 0.1);
    }

    /* ==========================================
       4. LEFT-CLICK OFFENSIVE EXTRACTION MECHANIC
       ========================================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClickWeaponStrike(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!soulItem.isSoul(weapon) || soulItem.getSoulType(weapon) != SoulType.KINDNESS) return;

        if (soulManager.isShattered(weapon)) return;

        event.setDamage(14.0);

        soulManager.removeKarma(weapon, OFFENSIVE_KARMA_PENALTY);

        ItemMeta meta = weapon.getItemMeta();
        if (meta != null) {
            var pdc = meta.getPersistentDataContainer();
            int violations = pdc.getOrDefault(offensiveUsesKey, PersistentDataType.INTEGER, 0) + 1;

            if (violations >= OFFENSIVE_USE_THRESHOLD) {
                soulManager.shatter(weapon);
                attacker.sendMessage("§4✕ Your repeated malice has overextended and shattered the Soul of Kindness. ✕");
            } else {
                pdc.set(offensiveUsesKey, PersistentDataType.INTEGER, violations);
                weapon.setItemMeta(meta);
                soulManager.refreshVisuals(weapon);
            }
        }
    }
}
