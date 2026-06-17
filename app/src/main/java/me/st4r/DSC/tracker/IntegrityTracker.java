package me.st4r.DSC.tracker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IntegrityTracker {

    public static final int HONORED_PLEDGE_THRESHOLD = 5;
    public static final int UNIQUE_PLAYER_THRESHOLD = 4;
    public static final int BROKEN_PLEDGE_THRESHOLD = 3;
    public static final long THIRD_PARTY_TRUST_WINDOW_MILLIS = 24L * 60L * 60L * 1000L;

    private final Map<UUID, Integer> honoredPledgeCounts = new HashMap<>();
    private final Map<UUID, Set<UUID>> honoredPartnersByCreator = new HashMap<>();
    private final Map<UUID, Integer> brokenPledgeCounts = new HashMap<>();
    private final Map<UUID, Integer> thirdPartyTradeCounts = new HashMap<>();
    private final Set<UUID> shatteredPlayers = new HashSet<>();

    public int recordHonoredPledge(UUID creatorUUID, UUID counterpartUUID, boolean bothPlayersOnline) {
        if (creatorUUID == null || counterpartUUID == null || bothPlayersOnline) return 0;

        int updatedCount = honoredPledgeCounts.getOrDefault(creatorUUID, 0) + 1;
        honoredPledgeCounts.put(creatorUUID, updatedCount);
        honoredPartnersByCreator.computeIfAbsent(creatorUUID, unused -> new HashSet<>()).add(counterpartUUID);
        return updatedCount;
    }

    public int recordBrokenPledge(UUID creatorUUID) {
        if (creatorUUID == null) return 0;
        int updatedCount = brokenPledgeCounts.getOrDefault(creatorUUID, 0) + 1;
        brokenPledgeCounts.put(creatorUUID, updatedCount);
        if (updatedCount >= BROKEN_PLEDGE_THRESHOLD) {
            shatteredPlayers.add(creatorUUID);
        }
        return updatedCount;
    }

    public int recordThirdPartyTrade(UUID playerUUID, long timestampMillis) {
        if (playerUUID == null) return 0;
        int updatedCount = thirdPartyTradeCounts.getOrDefault(playerUUID, 0) + 1;
        thirdPartyTradeCounts.put(playerUUID, updatedCount);
        return updatedCount;
    }

    public int getHonoredPledgeCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return honoredPledgeCounts.getOrDefault(playerUUID, 0);
    }

    public int getUniqueHonoredCounterpartCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        Set<UUID> partners = honoredPartnersByCreator.get(playerUUID);
        return partners == null ? 0 : partners.size();
    }

    public int getBrokenPledgeCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return brokenPledgeCounts.getOrDefault(playerUUID, 0);
    }

    public int getThirdPartyTradeCount(UUID playerUUID) {
        if (playerUUID == null) return 0;
        return thirdPartyTradeCounts.getOrDefault(playerUUID, 0);
    }

    public boolean isEligibleForIntegrity(UUID playerUUID) {
        return playerUUID != null
            && !shatteredPlayers.contains(playerUUID)
            && getHonoredPledgeCount(playerUUID) >= HONORED_PLEDGE_THRESHOLD
            && getUniqueHonoredCounterpartCount(playerUUID) >= UNIQUE_PLAYER_THRESHOLD;
    }

    public boolean isShattered(UUID playerUUID) {
        return playerUUID != null && shatteredPlayers.contains(playerUUID);
    }

    public void markShattered(UUID playerUUID) {
        if (playerUUID == null) return;
        shatteredPlayers.add(playerUUID);
    }

    public void reset(UUID playerUUID) {
        if (playerUUID == null) return;
        honoredPledgeCounts.remove(playerUUID);
        honoredPartnersByCreator.remove(playerUUID);
        brokenPledgeCounts.remove(playerUUID);
        thirdPartyTradeCounts.remove(playerUUID);
        shatteredPlayers.remove(playerUUID);
        for (Set<UUID> partners : honoredPartnersByCreator.values()) {
            partners.remove(playerUUID);
        }
    }

    public void clear() {
        honoredPledgeCounts.clear();
        honoredPartnersByCreator.clear();
        brokenPledgeCounts.clear();
        thirdPartyTradeCounts.clear();
        shatteredPlayers.clear();
    }

    public void forceRewardReady(UUID playerUUID) {
        if (playerUUID == null) return;

        honoredPledgeCounts.put(playerUUID, HONORED_PLEDGE_THRESHOLD);
        Set<UUID> partners = honoredPartnersByCreator.computeIfAbsent(playerUUID, unused -> new HashSet<>());
        while (partners.size() < UNIQUE_PLAYER_THRESHOLD) {
            partners.add(UUID.randomUUID());
        }
        shatteredPlayers.remove(playerUUID);
    }
}
