package me.st4r.DSC.tracker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PatienceTracker {

    public static final long WORLD_UNLOCK_DELAY_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    public static final long DAILY_PROGRESS_WINDOW_MILLIS = 24L * 60L * 60L * 1000L;
    public static final long SLAY_WINDOW_MILLIS = 60L * 60L * 1000L;
    public static final int ANIMAL_SLAY_THRESHOLD = 30;
    public static final int FORCED_ACCESS_THRESHOLD = 3;

    private final Map<UUID, Long> soulHeldAt = new HashMap<>();
    private final Map<UUID, Deque<Long>> animalSlaughterEvents = new HashMap<>();
    private final Map<UUID, Deque<Long>> forcedAccessEvents = new HashMap<>();
    private long worldGeneratedAtMillis;

    public void setWorldGeneratedAt(long timestampMillis) {
        worldGeneratedAtMillis = timestampMillis;
    }

    public long getWorldGeneratedAt() {
        return worldGeneratedAtMillis;
    }

    public boolean isChestUnlocked(long timestampMillis) {
        return worldGeneratedAtMillis > 0 && timestampMillis - worldGeneratedAtMillis >= WORLD_UNLOCK_DELAY_MILLIS;
    }

    public long getRemainingUnlockMillis(long timestampMillis) {
        if (worldGeneratedAtMillis <= 0) return WORLD_UNLOCK_DELAY_MILLIS;
        return Math.max(0L, WORLD_UNLOCK_DELAY_MILLIS - (timestampMillis - worldGeneratedAtMillis));
    }

    public void recordSoulHeld(UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return;
        soulHeldAt.put(playerUUID, timestampMillis);
    }

    public void markSoulHeldIfAbsent(UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return;
        soulHeldAt.putIfAbsent(playerUUID, timestampMillis);
    }

    public long getSoulHeldDuration(UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return 0L;
        Long heldAt = soulHeldAt.get(playerUUID);
        return heldAt == null ? 0L : Math.max(0L, timestampMillis - heldAt);
    }

    public long getSoulHeldDays(UUID playerUUID, long timestampMillis) {
        return getSoulHeldDuration(playerUUID, timestampMillis) / DAILY_PROGRESS_WINDOW_MILLIS;
    }

    public int recordAnimalSlaughter(UUID playerUUID, long timestampMillis) {
        return recordTimedIncident(animalSlaughterEvents, playerUUID, timestampMillis);
    }

    public int recordForcedAccessAttempt(UUID playerUUID, long timestampMillis) {
        return recordTimedIncident(forcedAccessEvents, playerUUID, timestampMillis);
    }

    public int getAnimalSlaughterCount(UUID playerUUID, long timestampMillis) {
        return getTimedIncidentCount(animalSlaughterEvents, playerUUID, timestampMillis);
    }

    public int getForcedAccessAttemptCount(UUID playerUUID, long timestampMillis) {
        return getTimedIncidentCount(forcedAccessEvents, playerUUID, timestampMillis);
    }

    public int getCorruptionPressure(UUID playerUUID, long timestampMillis) {
        return getAnimalSlaughterCount(playerUUID, timestampMillis) + getForcedAccessAttemptCount(playerUUID, timestampMillis);
    }

    public boolean shouldShatter(UUID playerUUID, long timestampMillis) {
        return getAnimalSlaughterCount(playerUUID, timestampMillis) >= ANIMAL_SLAY_THRESHOLD
            && getForcedAccessAttemptCount(playerUUID, timestampMillis) >= FORCED_ACCESS_THRESHOLD;
    }

    public boolean shouldGainDayProgress(UUID playerUUID, long timestampMillis) {
        return getSoulHeldDuration(playerUUID, timestampMillis) >= DAILY_PROGRESS_WINDOW_MILLIS;
    }

    private int recordTimedIncident(Map<UUID, Deque<Long>> incidents, UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return 0;

        Deque<Long> timeline = incidents.computeIfAbsent(playerUUID, unused -> new ArrayDeque<>());
        timeline.addLast(timestampMillis);
        purgeExpiredEntries(timeline, timestampMillis);
        return timeline.size();
    }

    private int getTimedIncidentCount(Map<UUID, Deque<Long>> incidents, UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return 0;

        Deque<Long> timeline = incidents.get(playerUUID);
        if (timeline == null) return 0;
        purgeExpiredEntries(timeline, timestampMillis);
        return timeline.size();
    }

    private void purgeExpiredEntries(Deque<Long> timeline, long timestampMillis) {
        while (!timeline.isEmpty() && timestampMillis - timeline.peekFirst() > SLAY_WINDOW_MILLIS) {
            timeline.removeFirst();
        }
    }

    public void reset(UUID playerUUID) {
        if (playerUUID == null) return;
        soulHeldAt.remove(playerUUID);
        animalSlaughterEvents.remove(playerUUID);
        forcedAccessEvents.remove(playerUUID);
    }

    public void clear() {
        soulHeldAt.clear();
        animalSlaughterEvents.clear();
        forcedAccessEvents.clear();
        worldGeneratedAtMillis = 0L;
    }
}
