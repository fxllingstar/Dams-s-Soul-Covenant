package me.st4r.ASC.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import me.st4r.ASC.ASC;
import me.st4r.ASC.event.SoulThresholdLostEvent;
import me.st4r.ASC.event.SoulThresholdMetEvent;
import me.st4r.ASC.soul.SoulItem;
import me.st4r.ASC.soul.SoulManager;
import me.st4r.ASC.soul.SoulType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class SoulStateManager {

    public enum SoulState {
        HEALTHY,
        DEGRADED,
        FRACTURE
    }

    public record SoulStateSnapshot(
        SoulState state,
        int corruptedSouls,
        int existingSouls,
        int validResonanceSouls,
        int totalKarma,
        Map<SoulType, Integer> karmaBySoul,
        Map<SoulType, Boolean> corruptedBySoul
    ) {
        public boolean allSoulsExist() {
            return existingSouls >= SoulType.values().length;
        }

        public boolean meetsResonanceThreshold() {
            return validResonanceSouls >= 5;
        }
    }

    private static final long EVALUATION_PERIOD_TICKS = 80L;

    private final ASC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final Map<SoulType, ItemStack> lastKnownSouls = new EnumMap<>(SoulType.class);

    private SoulStateSnapshot currentSnapshot;
    private FractureHandler fractureHandler;
    private ResonanceHandler resonanceHandler;
    private BukkitTask task;
    private boolean resonanceThresholdCurrentlyMet;

    public SoulStateManager(ASC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
        this.currentSnapshot = new SoulStateSnapshot(
            SoulState.HEALTHY,
            0,
            0,
            0,
            0,
            Collections.emptyMap(),
            Collections.emptyMap()
        );
    }

    public void setHandlers(FractureHandler fractureHandler, ResonanceHandler resonanceHandler) {
        this.fractureHandler = fractureHandler;
        this.resonanceHandler = resonanceHandler;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::evaluateAndApply, 20L, EVALUATION_PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public SoulStateSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    public SoulState getCurrentState() {
        return currentSnapshot.state();
    }

    public boolean isDegradedOrWorse() {
        return currentSnapshot.state() == SoulState.DEGRADED || currentSnapshot.state() == SoulState.FRACTURE;
    }

    public boolean isFractured() {
        return currentSnapshot.state() == SoulState.FRACTURE;
    }

    public boolean isSoulPresent(SoulType type) {
        if (findTrackedSoul(type) != null) {
            return true;
        }

        UUID holderUUID = soulManager.getHolder(type);
        if (holderUUID == null) {
            return false;
        }

        Player holder = Bukkit.getPlayer(holderUUID);
        return holder == null || !holder.isOnline();
    }

    public SoulStateSnapshot evaluateNow() {
        Map<SoulType, Integer> karmaBySoul = new EnumMap<>(SoulType.class);
        Map<SoulType, Boolean> corruptedBySoul = new EnumMap<>(SoulType.class);
        int existingSouls = 0;
        int validResonanceSouls = 0;
        int corruptedSouls = 0;
        int totalKarma = 0;

        for (SoulType type : SoulType.values()) {
            ItemStack soulStack = findTrackedSoul(type);
            if (soulStack == null) {
                lastKnownSouls.remove(type);
                corruptedBySoul.put(type, false);
                continue;
            }

            lastKnownSouls.put(type, soulStack.clone());
            existingSouls++;
            int karma = soulManager.getKarma(soulStack);
            boolean resonanceInvalid = soulManager.isCorrupted(soulStack) || soulManager.isShattered(soulStack);
            karmaBySoul.put(type, karma);
            corruptedBySoul.put(type, resonanceInvalid);
            totalKarma += karma;

            if (resonanceInvalid) {
                corruptedSouls++;
            } else {
                validResonanceSouls++;
            }
        }

        SoulState state = determineState(corruptedSouls);
        currentSnapshot = new SoulStateSnapshot(
            state,
            corruptedSouls,
            existingSouls,
            validResonanceSouls,
            totalKarma,
            Collections.unmodifiableMap(karmaBySoul),
            Collections.unmodifiableMap(corruptedBySoul)
        );
        return currentSnapshot;
    }

    public SoulStateSnapshot evaluateAndApplyNow() {
        SoulStateSnapshot snapshot = evaluateNow();

        if (fractureHandler != null) {
            fractureHandler.applySoulState(snapshot);
        }

        if (resonanceHandler != null) {
            resonanceHandler.applySoulState(snapshot);
        }

        handleSoulThresholdTrigger(snapshot);

        return snapshot;
    }

    private void evaluateAndApply() {
        evaluateAndApplyNow();
    }

    private SoulState determineState(int corruptedSouls) {
        if (corruptedSouls >= 4) return SoulState.FRACTURE;
        if (corruptedSouls >= 3) return SoulState.DEGRADED;
        return SoulState.HEALTHY;
    }

    private void handleSoulThresholdTrigger(SoulStateSnapshot snapshot) {
        boolean thresholdMet = snapshot.meetsResonanceThreshold();
        if (thresholdMet == resonanceThresholdCurrentlyMet) {
            return;
        }

        resonanceThresholdCurrentlyMet = thresholdMet;
        if (thresholdMet) {
            Bukkit.getPluginManager().callEvent(new SoulThresholdMetEvent(snapshot));
        } else {
            Bukkit.getPluginManager().callEvent(new SoulThresholdLostEvent(snapshot));
        }
    }

    private ItemStack findTrackedSoul(SoulType type) {
        if (type == SoulType.PATIENCE) {
            ItemStack patienceChestSoul = findPatienceChestSoul();
            if (patienceChestSoul != null) {
                return patienceChestSoul;
            }
        }

        UUID holderUUID = soulManager.getHolder(type);
        boolean holderOnline = false;
        if (holderUUID != null) {
            Player holder = Bukkit.getPlayer(holderUUID);
            if (holder != null && holder.isOnline()) {
                holderOnline = true;
                for (ItemStack item : holder.getInventory().getContents()) {
                    if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == type) {
                        return item;
                    }
                }
            }
        }

        for (var world : Bukkit.getWorlds()) {
            for (Item itemEntity : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = itemEntity.getItemStack();
                if (stack != null && soulItem.isSoul(stack) && soulItem.getSoulType(stack) == type) {
                    return stack;
                }
            }
        }

        if (holderUUID != null) {
            if (holderOnline) {
                soulManager.setHolder(type, null);
                lastKnownSouls.remove(type);
                return null;
            }

            ItemStack cachedSoul = lastKnownSouls.get(type);
            if (cachedSoul != null) {
                return cachedSoul.clone();
            }
        }

        return null;
    }

    private ItemStack findPatienceChestSoul() {
        Location chestLocation = plugin.resolvePatienceChestLocation();
        if (chestLocation == null || chestLocation.getWorld() == null) {
            return null;
        }

        var block = chestLocation.getWorld().getBlockAt(chestLocation);
        if (!(block.getState() instanceof Chest chest)) {
            return null;
        }

        for (ItemStack item : chest.getBlockInventory().getContents()) {
            if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == SoulType.PATIENCE) {
                return item;
            }
        }

        return null;
    }
}
