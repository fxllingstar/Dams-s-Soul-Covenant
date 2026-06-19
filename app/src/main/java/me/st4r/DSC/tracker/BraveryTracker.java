package me.st4r.DSC.tracker;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BraveryTracker {

    public static final int DEATHS_REQUIRED = 10;

    private final Map<UUID, Integer> deathCounts = new HashMap<>();
    private final Map<UUID, Long> lastCombatAt = new HashMap<>();
    private final Map<UUID, Integer> cowardlyActCounts = new HashMap<>();
    private final Set<UUID> rewardReady = new HashSet<>();
    private final Set<UUID> rewardConsumed = new HashSet<>();

    public int recordDeath(UUID playerUUID) {
        if (playerUUID == null) return 0;

        int updatedCount = deathCounts.getOrDefault(playerUUID, 0) + 1;
        deathCounts.put(playerUUID, updatedCount);

        if (updatedCount >= DEATHS_REQUIRED) {
            rewardReady.add(playerUUID);
        }

        return updatedCount;
    }

    public void recordCombatEngagement(UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return;
        lastCombatAt.put(playerUUID, timestampMillis);
    }

    public boolean wasInCombatRecently(UUID playerUUID, long timestampMillis, long windowMillis) {
        if (playerUUID == null) return false;
        Long lastCombatMillis = lastCombatAt.get(playerUUID);
        return lastCombatMillis != null && timestampMillis - lastCombatMillis <= windowMillis;
    }

    public int recordCowardlyAct(UUID playerUUID) {
        if (playerUUID == null) return 0;
        int updatedCount = cowardlyActCounts.getOrDefault(playerUUID, 0) + 1;
        cowardlyActCounts.put(playerUUID, updatedCount);
        deathCounts.remove(playerUUID);
        rewardReady.remove(playerUUID);
        return updatedCount;
    }

    public int recordDeath(Player player) {
        if (player == null) return 0;
        return recordDeath(player.getUniqueId());
    }

    public int getDeathCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return deathCounts.getOrDefault(playerUUID, 0);
    }

    public int getDeathsRemaining(UUID playerUUID) {
        return Math.max(0, DEATHS_REQUIRED - getDeathCount(playerUUID));
    }

    public boolean hasReachedThreshold(UUID playerUUID) {
        return getDeathCount(playerUUID) >= DEATHS_REQUIRED;
    }

    public boolean isRewardReady(UUID playerUUID) {
        if (playerUUID == null) return false;
        return rewardReady.contains(playerUUID) && !rewardConsumed.contains(playerUUID);
    }

    public int getCowardlyActCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return cowardlyActCounts.getOrDefault(playerUUID, 0);
    }

    public boolean canDropBravery(UUID playerUUID) {
        return isRewardReady(playerUUID);
    }

    public void markRewardGranted(UUID playerUUID) {
        if (playerUUID == null) return;
        rewardReady.remove(playerUUID);
        rewardConsumed.add(playerUUID);
    }

    public void forceRewardReady(UUID playerUUID) {
        if (playerUUID == null) return;
        deathCounts.put(playerUUID, DEATHS_REQUIRED);
        cowardlyActCounts.put(playerUUID, DEATHS_REQUIRED);
        rewardReady.add(playerUUID);
        rewardConsumed.remove(playerUUID);
    }

    public void reset(UUID playerUUID) {
        if (playerUUID == null) return;
        deathCounts.remove(playerUUID);
        lastCombatAt.remove(playerUUID);
        cowardlyActCounts.remove(playerUUID);
        rewardReady.remove(playerUUID);
        rewardConsumed.remove(playerUUID);
    }

    public void clear() {
        deathCounts.clear();
        lastCombatAt.clear();
        cowardlyActCounts.clear();
        rewardReady.clear();
        rewardConsumed.clear();
    }
}
