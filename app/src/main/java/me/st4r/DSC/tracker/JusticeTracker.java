package me.st4r.DSC.tracker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JusticeTracker {

    public static final int MOST_WANTED_THRESHOLD = 5;
    public static final long REPEATED_KILL_WINDOW_MILLIS = 60L * 60L * 1000L;

    private final Map<UUID, Integer> killStreaks = new HashMap<>();
    private final Map<UUID, Integer> killCounts = new HashMap<>();
    private final Map<UUID, Integer> innocentKillCounts = new HashMap<>();
    private final Set<UUID> bountyHolders = new HashSet<>();
    private final Map<UUID, Map<UUID, Deque<Long>>> repeatedVictimKills = new HashMap<>();

    public int recordPlayerKill(UUID killerUUID, UUID victimUUID, long timestampMillis) {
        if (killerUUID == null) return 0;

        int updatedKills = killCounts.getOrDefault(killerUUID, 0) + 1;
        killCounts.put(killerUUID, updatedKills);
        killStreaks.put(killerUUID, killStreaks.getOrDefault(killerUUID, 0) + 1);

        if (victimUUID != null) {
            Map<UUID, Deque<Long>> killerHistory = repeatedVictimKills.computeIfAbsent(killerUUID, unused -> new HashMap<>());
            Deque<Long> killsAgainstVictim = killerHistory.computeIfAbsent(victimUUID, unused -> new ArrayDeque<>());
            killsAgainstVictim.addLast(timestampMillis);
            while (!killsAgainstVictim.isEmpty() && timestampMillis - killsAgainstVictim.peekFirst() > REPEATED_KILL_WINDOW_MILLIS) {
                killsAgainstVictim.removeFirst();
            }
        }

        return updatedKills;
    }

    public int recordPlayerKill(UUID killerUUID, UUID victimUUID) {
        return recordPlayerKill(killerUUID, victimUUID, System.currentTimeMillis());
    }

    public int recordDeath(UUID playerUUID) {
        if (playerUUID == null) return 0;
        killStreaks.put(playerUUID, 0);
        bountyHolders.remove(playerUUID);
        return 0;
    }

    public int recordInnocentKill(UUID killerUUID) {
        if (killerUUID == null) return 0;
        int updatedCount = innocentKillCounts.getOrDefault(killerUUID, 0) + 1;
        innocentKillCounts.put(killerUUID, updatedCount);
        return updatedCount;
    }

    public void setBountyHolder(UUID playerUUID, boolean hasBounty) {
        if (playerUUID == null) return;
        if (hasBounty) {
            bountyHolders.add(playerUUID);
        } else {
            bountyHolders.remove(playerUUID);
        }
    }

    public boolean isBountyHolder(UUID playerUUID) {
        return playerUUID != null && bountyHolders.contains(playerUUID);
    }

    public int getKillCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return killCounts.getOrDefault(playerUUID, 0);
    }

    public int getKillStreak(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return killStreaks.getOrDefault(playerUUID, 0);
    }

    public int getInnocentKillCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return innocentKillCounts.getOrDefault(playerUUID, 0);
    }

    public boolean isMostWanted(UUID playerUUID) {
        return getKillStreak(playerUUID) >= MOST_WANTED_THRESHOLD;
    }

    public boolean isEligibleForJusticeDrop(UUID victimUUID) {
        return isMostWanted(victimUUID);
    }

    public boolean hasRepeatedVictimKills(UUID killerUUID, UUID victimUUID, int threshold) {
        if (killerUUID == null || victimUUID == null) return false;
        Map<UUID, Deque<Long>> killerHistory = repeatedVictimKills.get(killerUUID);
        if (killerHistory == null) return false;
        Deque<Long> killsAgainstVictim = killerHistory.get(victimUUID);
        return killsAgainstVictim != null && killsAgainstVictim.size() >= threshold;
    }

    public void reset(UUID playerUUID) {
        if (playerUUID == null) return;
        killStreaks.remove(playerUUID);
        killCounts.remove(playerUUID);
        innocentKillCounts.remove(playerUUID);
        bountyHolders.remove(playerUUID);
        repeatedVictimKills.remove(playerUUID);
        for (Map<UUID, Deque<Long>> killerHistory : repeatedVictimKills.values()) {
            killerHistory.remove(playerUUID);
        }
    }

    public void clear() {
        killStreaks.clear();
        killCounts.clear();
        innocentKillCounts.clear();
        bountyHolders.clear();
        repeatedVictimKills.clear();
    }

    public void forceRewardReady(UUID playerUUID) {
        if (playerUUID == null) return;

        killStreaks.put(playerUUID, MOST_WANTED_THRESHOLD);
        killCounts.put(playerUUID, Math.max(killCounts.getOrDefault(playerUUID, 0), MOST_WANTED_THRESHOLD));
        bountyHolders.add(playerUUID);
    }
}
