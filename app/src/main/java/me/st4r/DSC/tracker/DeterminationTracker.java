package me.st4r.DSC.tracker;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeterminationTracker {

    public static final int SERVER_DEATH_THRESHOLD = 500;

    private final Map<UUID, Integer> playerDeathCounts = new HashMap<>();
    private final Map<UUID, Integer> logoutOnDeathIncidents = new HashMap<>();
    private final Map<UUID, Integer> abandonedRunIncidents = new HashMap<>();
    private final Set<UUID> rewardConsumed = new HashSet<>();

    private int totalDeathCount;
    private UUID currentLeader;
    private int currentLeaderDeaths;

    public int recordDeath(UUID playerUUID) {
        if (playerUUID == null) return 0;

        totalDeathCount++;
        int updatedDeaths = playerDeathCounts.getOrDefault(playerUUID, 0) + 1;
        playerDeathCounts.put(playerUUID, updatedDeaths);

        if (updatedDeaths > currentLeaderDeaths) {
            currentLeader = playerUUID;
            currentLeaderDeaths = updatedDeaths;
        }

        return updatedDeaths;
    }

    public int recordDeath(Player player) {
        if (player == null) return 0;
        return recordDeath(player.getUniqueId());
    }

    public int recordLogoutOnDeathIncident(UUID playerUUID) {
        if (playerUUID == null) return 0;
        int updatedCount = logoutOnDeathIncidents.getOrDefault(playerUUID, 0) + 1;
        logoutOnDeathIncidents.put(playerUUID, updatedCount);
        return updatedCount;
    }

    public int recordAbandonedRun(UUID playerUUID) {
        if (playerUUID == null) return 0;
        int updatedCount = abandonedRunIncidents.getOrDefault(playerUUID, 0) + 1;
        abandonedRunIncidents.put(playerUUID, updatedCount);
        return updatedCount;
    }

    public int getDeathCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return playerDeathCounts.getOrDefault(playerUUID, 0);
    }

    public int getServerDeathCount() {
        return totalDeathCount;
    }

    public UUID getCurrentLeader() {
        return currentLeader;
    }

    public int getCurrentLeaderDeaths() {
        return currentLeaderDeaths;
    }

    public boolean hasReachedSpawnThreshold() {
        return totalDeathCount >= SERVER_DEATH_THRESHOLD;
    }

    public boolean isRewardReady() {
        return hasReachedSpawnThreshold() && currentLeader != null && !rewardConsumed.contains(currentLeader);
    }

    public boolean canDropSoulFor(UUID playerUUID) {
        return isRewardReady() && playerUUID != null && playerUUID.equals(currentLeader);
    }

    public void markRewardGranted(UUID playerUUID) {
        if (playerUUID == null) return;
        rewardConsumed.add(playerUUID);
    }

    public int getLogoutOnDeathIncidents(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return logoutOnDeathIncidents.getOrDefault(playerUUID, 0);
    }

    public int getAbandonedRunIncidents(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return abandonedRunIncidents.getOrDefault(playerUUID, 0);
    }

    public void reset(UUID playerUUID) {
        if (playerUUID == null) return;
        playerDeathCounts.remove(playerUUID);
        logoutOnDeathIncidents.remove(playerUUID);
        abandonedRunIncidents.remove(playerUUID);
        rewardConsumed.remove(playerUUID);

        if (playerUUID.equals(currentLeader)) {
            currentLeader = null;
            currentLeaderDeaths = 0;
            for (Map.Entry<UUID, Integer> entry : playerDeathCounts.entrySet()) {
                if (entry.getValue() > currentLeaderDeaths) {
                    currentLeader = entry.getKey();
                    currentLeaderDeaths = entry.getValue();
                }
            }
        }
    }

    public void clear() {
        playerDeathCounts.clear();
        logoutOnDeathIncidents.clear();
        abandonedRunIncidents.clear();
        rewardConsumed.clear();
        totalDeathCount = 0;
        currentLeader = null;
        currentLeaderDeaths = 0;
    }
}
