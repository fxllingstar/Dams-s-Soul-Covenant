package me.st4r.DSC.pledge;

import me.st4r.DSC.DSC;
import me.st4r.DSC.pledge.Pledge.Status;
import me.st4r.DSC.tracker.IntegrityTracker;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PledgeManager {

    private final DSC plugin;
    private final Map<Integer, Pledge> pledges = new LinkedHashMap<>();
    private final File storageFile;
    private int nextId = 1;

    public PledgeManager(DSC plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "pledges.yml");
    }

    public void load() {
        pledges.clear();
        File parent = storageFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!storageFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
        nextId = Math.max(1, config.getInt("next-id", 1));

        ConfigurationSection section = config.getConfigurationSection("pledges");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection pledgeSection = section.getConfigurationSection(key);
            if (pledgeSection == null) continue;

            try {
                int id = Integer.parseInt(key);
                Pledge pledge = new Pledge(
                    id,
                    UUID.fromString(pledgeSection.getString("creator")),
                    UUID.fromString(pledgeSection.getString("target")),
                    pledgeSection.getString("creator-offer", ""),
                    pledgeSection.getString("target-offer", ""),
                    pledgeSection.getLong("created-at")
                );
                pledge.setStatus(Status.valueOf(pledgeSection.getString("status", Status.PENDING.name())));
                pledge.setCreatorFulfilled(pledgeSection.getBoolean("creator-fulfilled"));
                pledge.setTargetFulfilled(pledgeSection.getBoolean("target-fulfilled"));
                pledge.setIntegrityDisqualified(pledgeSection.getBoolean("integrity-disqualified"));
                pledges.put(id, pledge);
                nextId = Math.max(nextId, id + 1);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        File parent = storageFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("next-id", nextId);

        for (Pledge pledge : pledges.values()) {
            String path = "pledges." + pledge.getId() + ".";
            config.set(path + "creator", pledge.getCreatorUUID().toString());
            config.set(path + "target", pledge.getTargetUUID().toString());
            config.set(path + "creator-offer", pledge.getCreatorOffer());
            config.set(path + "target-offer", pledge.getTargetOffer());
            config.set(path + "created-at", pledge.getCreatedAt());
            config.set(path + "status", pledge.getStatus().name());
            config.set(path + "creator-fulfilled", pledge.isCreatorFulfilled());
            config.set(path + "target-fulfilled", pledge.isTargetFulfilled());
            config.set(path + "integrity-disqualified", pledge.isIntegrityDisqualified());
        }

        try {
            config.save(storageFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save pledges.yml: " + exception.getMessage());
        }
    }

    public Pledge create(Player creator, OfflinePlayer target, String creatorOffer, String targetOffer) {
        Pledge pledge = new Pledge(nextId++, creator.getUniqueId(), target.getUniqueId(), creatorOffer, targetOffer, System.currentTimeMillis());
        pledges.put(pledge.getId(), pledge);
        save();
        return pledge;
    }

    public Pledge get(int id) {
        return pledges.get(id);
    }

    public Collection<Pledge> getAll() {
        return pledges.values();
    }

    public List<Pledge> getFor(UUID playerUUID) {
        return pledges.values().stream()
            .filter(pledge -> pledge.involves(playerUUID))
            .sorted(Comparator.comparingInt(Pledge::getId))
            .toList();
    }

    public boolean accept(Pledge pledge, Player player) {
        if (pledge == null || pledge.getStatus() != Status.PENDING) return false;
        if (!pledge.getTargetUUID().equals(player.getUniqueId())) return false;

        pledge.setStatus(Status.ACTIVE);
        save();
        return true;
    }

    public FulfillmentResult fulfill(Pledge pledge, Player player) {
        if (pledge == null || pledge.getStatus() != Status.ACTIVE) {
            return FulfillmentResult.notAllowed();
        }
        if (!pledge.involves(player.getUniqueId())) {
            return FulfillmentResult.notAllowed();
        }

        if (pledge.getCreatorUUID().equals(player.getUniqueId())) {
            pledge.setCreatorFulfilled(true);
        } else {
            pledge.setTargetFulfilled(true);
        }

        boolean bothOnline = Bukkit.getPlayer(pledge.getCreatorUUID()) != null && Bukkit.getPlayer(pledge.getTargetUUID()) != null;
        if (bothOnline) {
            pledge.setIntegrityDisqualified(true);
        }

        if (pledge.isComplete()) {
            pledge.setStatus(Status.HONORED);
            if (!pledge.isIntegrityDisqualified()) {
                plugin.getIntegrityTracker().recordHonoredPledge(pledge.getCreatorUUID(), pledge.getTargetUUID(), false);
                if (plugin.getIntegrityTracker().isEligibleForIntegrity(pledge.getCreatorUUID())) {
                    Player creator = Bukkit.getPlayer(pledge.getCreatorUUID());
                    if (creator != null) {
                        plugin.grantSoul(creator, SoulType.INTEGRITY);
                    }
                }
            }
        }

        save();
        return new FulfillmentResult(true, bothOnline, pledge.getStatus() == Status.HONORED, pledge.isIntegrityDisqualified());
    }

    public boolean breakPledge(Pledge pledge, Player player) {
        if (pledge == null || pledge.getStatus() == Status.BROKEN || pledge.getStatus() == Status.HONORED) return false;
        if (!pledge.involves(player.getUniqueId())) return false;

        pledge.setStatus(Status.BROKEN);
        int brokenCount = plugin.getIntegrityTracker().recordBrokenPledge(pledge.getCreatorUUID());
        if (plugin.getIntegrityTracker().isShattered(pledge.getCreatorUUID()) && brokenCount >= IntegrityTracker.BROKEN_PLEDGE_THRESHOLD) {
            Player creator = Bukkit.getPlayer(pledge.getCreatorUUID());
            if (creator != null) {
                ItemStack integritySoul = findSoulInInventory(creator, SoulType.INTEGRITY);
                if (integritySoul != null) {
                    plugin.getSoulManager().shatter(integritySoul);
                }
            }
        }
        save();
        return true;
    }

    public String describe(Pledge pledge) {
        OfflinePlayer creator = Bukkit.getOfflinePlayer(pledge.getCreatorUUID());
        OfflinePlayer target = Bukkit.getOfflinePlayer(pledge.getTargetUUID());
        String creatorName = creator.getName() == null ? pledge.getCreatorUUID().toString() : creator.getName();
        String targetName = target.getName() == null ? pledge.getTargetUUID().toString() : target.getName();

        List<String> parts = new ArrayList<>();
        parts.add("#" + pledge.getId());
        parts.add(pledge.getStatus().name());
        parts.add(creatorName + " -> " + targetName);
        parts.add("creator: " + (pledge.isCreatorFulfilled() ? "done" : "open"));
        parts.add("target: " + (pledge.isTargetFulfilled() ? "done" : "open"));
        if (pledge.isIntegrityDisqualified()) {
            parts.add("Integrity-disqualified");
        }
        return String.join(" | ", parts);
    }

    public void clear() {
        pledges.clear();
        nextId = 1;
        save();
    }

    private ItemStack findSoulInInventory(Player player, SoulType type) {
        if (player == null || type == null) return null;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && plugin.getSoulItem().isSoul(item) && plugin.getSoulItem().getSoulType(item) == type) {
                return item;
            }
        }
        return null;
    }

    public record FulfillmentResult(boolean allowed, boolean disqualifiedNow, boolean completed, boolean disqualified) {
        public static FulfillmentResult notAllowed() {
            return new FulfillmentResult(false, false, false, false);
        }
    }
}
