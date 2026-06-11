package me.st4r.DSC.soul;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class SoulManager {

   
    private final Map<SoulType, UUID> activeHolders = new EnumMap<>(SoulType.class);

 
    public void setHolder(SoulType type, UUID playerUUID) {
        if (playerUUID == null) {
            activeHolders.remove(type);
        } else {
            activeHolders.put(type, playerUUID);
        }
    }

  
    public UUID getHolder(SoulType type) {
        return activeHolders.get(type);
    }

  
    public SoulType getSoulOfPlayer(UUID playerUUID) {
        if (playerUUID == null) return null;
        
        for (Map.Entry<SoulType, UUID> entry : activeHolders.entrySet()) {
            if (playerUUID.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

  
    public boolean isHoldingSoul(UUID playerUUID) {
        return activeHolders.containsValue(playerUUID);
    }

  
    public void clearAll() {
        activeHolders.clear();
    }

   
    public Map<SoulType, UUID> getLiveRegistryView() {
        return Collections.unmodifiableMap(activeHolders);
    }
}