package me.st4r.DSC.soul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import me.st4r.DSC.DSC;

public class SoulRegistery {

    private final DSC plugin;
    private final SoulItem soulItem;
    private final Map<SoulType, SoulType> registeredSouls = new EnumMap<>(SoulType.class);

    public SoulRegistery(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        registerDefaults();
    }

    private void registerDefaults() {
        for (SoulType type : SoulType.values()) {
            registeredSouls.put(type, type);
        }
    }

    public boolean isRegistered(SoulType type) {
        return type != null && registeredSouls.containsKey(type);
    }

    public List<SoulType> getRegisteredTypes() {
        return Collections.unmodifiableList(new ArrayList<>(registeredSouls.keySet()));
    }

    public Optional<SoulType> getByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (SoulType type : registeredSouls.keySet()) {
            if (type.name().equals(normalized) || type.getDisplayName().toUpperCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public ItemStack createSoul(SoulType type) {
        if (!isRegistered(type)) {
            throw new IllegalArgumentException("Unknown soul type: " + type);
        }
        return soulItem.create(type);
    }

    public Map<SoulType, ItemStack> createAllSouls() {
        Map<SoulType, ItemStack> result = new EnumMap<>(SoulType.class);
        for (SoulType type : registeredSouls.keySet()) {
            result.put(type, soulItem.create(type));
        }
        return result;
    }

    public DSC getPlugin() {
        return plugin;
    }
}
