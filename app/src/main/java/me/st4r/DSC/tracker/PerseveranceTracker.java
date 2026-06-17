package me.st4r.DSC.tracker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PerseveranceTracker {

    public static final int DUNGEON_COMPLETION_THRESHOLD = 100;
    public static final int FILL_TRIGGER_STREAK = 3;
    public static final long SAME_SOURCE_WINDOW_MILLIS = 10L * 60L * 1000L;
    public static final long PASSED_OUT_DORMANCY_MILLIS = 3L * 24L * 60L * 60L * 1000L;

    private static final class SourceDeathWindow {
        private int count;
        private long lastDeathAt;
    }

    private final Map<UUID, Integer> dungeonCompletionCounts = new HashMap<>();
    private final Map<UUID, UUID> latestCompletionWinner = new HashMap<>();
    private final Map<UUID, Map<UUID, SourceDeathWindow>> sourceDeathWindows = new HashMap<>();
    private final Map<UUID, Integer> abandonedRunCounts = new HashMap<>();
    private final Map<UUID, Long> lastLoginAt = new HashMap<>();
    private final Map<UUID, Long> lastLogoutAt = new HashMap<>();
    private final Set<UUID> fillReadyPlayers = new HashSet<>();
    private final Set<UUID> dormantPlayers = new HashSet<>();

    private int totalDungeonCompletions;
    private UUID rewardWinner;

    public int recordDungeonCompletion(UUID playerUUID) {
        if (playerUUID == null) return 0;

        totalDungeonCompletions++;
        int updatedCount = dungeonCompletionCounts.getOrDefault(playerUUID, 0) + 1;
        dungeonCompletionCounts.put(playerUUID, updatedCount);
        latestCompletionWinner.put(playerUUID, playerUUID);

        if (totalDungeonCompletions >= DUNGEON_COMPLETION_THRESHOLD && rewardWinner == null) {
            rewardWinner = playerUUID;
        }

        return updatedCount;
    }

    public int recordSameSourceDeath(UUID playerUUID, UUID sourceUUID, long timestampMillis) {
        if (playerUUID == null || sourceUUID == null) return 0;

        Map<UUID, SourceDeathWindow> playerWindows = sourceDeathWindows.computeIfAbsent(playerUUID, unused -> new HashMap<>());
        SourceDeathWindow sourceWindow = playerWindows.computeIfAbsent(sourceUUID, unused -> new SourceDeathWindow());

        if (timestampMillis - sourceWindow.lastDeathAt > SAME_SOURCE_WINDOW_MILLIS) {
            sourceWindow.count = 0;
        }

        sourceWindow.count++;
        sourceWindow.lastDeathAt = timestampMillis;

        if (sourceWindow.count >= FILL_TRIGGER_STREAK) {
            fillReadyPlayers.add(playerUUID);
        }

        return sourceWindow.count;
    }

    public int recordSameSourceDeath(UUID playerUUID, UUID sourceUUID) {
        return recordSameSourceDeath(playerUUID, sourceUUID, System.currentTimeMillis());
    }

    public int recordAbandonedRun(UUID playerUUID) {
        if (playerUUID == null) return 0;
        int updatedCount = abandonedRunCounts.getOrDefault(playerUUID, 0) + 1;
        abandonedRunCounts.put(playerUUID, updatedCount);
        return updatedCount;
    }

    public void recordLogin(UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return;
        lastLoginAt.put(playerUUID, timestampMillis);
        dormantPlayers.remove(playerUUID);
    }

    public void recordLogout(UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return;
        lastLogoutAt.put(playerUUID, timestampMillis);
    }

    public boolean isDormant(UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return false;
        Long logoutMillis = lastLogoutAt.get(playerUUID);
        return logoutMillis != null && timestampMillis - logoutMillis >= PASSED_OUT_DORMANCY_MILLIS;
    }

    public void refreshDormancy(UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return;
        if (isDormant(playerUUID, timestampMillis)) {
            dormantPlayers.add(playerUUID);
        } else {
            dormantPlayers.remove(playerUUID);
        }
    }

    public int getDungeonCompletionCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return dungeonCompletionCounts.getOrDefault(playerUUID, 0);
    }

    public int getTotalDungeonCompletions() {
        return totalDungeonCompletions;
    }

    public UUID getRewardWinner() {
        return rewardWinner;
    }

    public boolean isRewardReady() {
        return rewardWinner != null && totalDungeonCompletions >= DUNGEON_COMPLETION_THRESHOLD;
    }

    public boolean hasFilledWithPerseverance(UUID playerUUID) {
        return playerUUID != null && fillReadyPlayers.contains(playerUUID);
    }

    public boolean isDormantPlayer(UUID playerUUID) {
        return playerUUID != null && dormantPlayers.contains(playerUUID);
    }

    public void markFillConsumed(UUID playerUUID) {
        if (playerUUID == null) return;
        fillReadyPlayers.remove(playerUUID);
    }

    public void reset(UUID playerUUID) {
        if (playerUUID == null) return;
        dungeonCompletionCounts.remove(playerUUID);
        latestCompletionWinner.remove(playerUUID);
        sourceDeathWindows.remove(playerUUID);
        abandonedRunCounts.remove(playerUUID);
        lastLoginAt.remove(playerUUID);
        lastLogoutAt.remove(playerUUID);
        fillReadyPlayers.remove(playerUUID);
        dormantPlayers.remove(playerUUID);
        if (playerUUID.equals(rewardWinner)) {
            rewardWinner = null;
        }
    }

    public void clear() {
        dungeonCompletionCounts.clear();
        latestCompletionWinner.clear();
        sourceDeathWindows.clear();
        abandonedRunCounts.clear();
        lastLoginAt.clear();
        lastLogoutAt.clear();
        fillReadyPlayers.clear();
        dormantPlayers.clear();
        totalDungeonCompletions = 0;
        rewardWinner = null;
    }
}
