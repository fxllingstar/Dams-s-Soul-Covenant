package me.st4r.DSC.pledge;

import me.st4r.DSC.DSC;
import me.st4r.DSC.pledge.Pledge.Status;
import me.st4r.DSC.tracker.IntegrityTracker;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PledgeManager {

    private final DSC plugin;
    private final Map<Integer, Pledge> pledges = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> claimItems = new LinkedHashMap<>();
    private final File storageFile;
    private int nextId = 1;
    public static final int CLAIM_INVENTORY_SIZE = 27;

    public PledgeManager(DSC plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "pledges.yml");
    }

    public void load() {
        pledges.clear();
        claimItems.clear();
        File parent = storageFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!storageFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
        nextId = Math.max(1, config.getInt("next-id", 1));

        ConfigurationSection section = config.getConfigurationSection("pledges");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection pledgeSection = section.getConfigurationSection(key);
                if (pledgeSection == null) continue;

                try {
                    int id = Integer.parseInt(key);
                    UUID creatorUUID = parseUuid(pledgeSection.getString("creator"));
                    UUID targetUUID = parseUuid(pledgeSection.getString("target"));
                    if (creatorUUID == null || targetUUID == null) {
                        continue;
                    }

                    Pledge pledge = new Pledge(
                        id,
                        creatorUUID,
                        targetUUID,
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

        ConfigurationSection claimSection = config.getConfigurationSection("claim-items");
        if (claimSection == null) return;

        for (String key : claimSection.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(key);
                List<ItemStack> storedItems = new ArrayList<>();
                List<?> rawItems = claimSection.getList(key, List.of());
                for (Object rawItem : rawItems) {
                    if (rawItem instanceof ItemStack item && !item.getType().isAir()) {
                        storedItems.add(item);
                    }
                }
                if (!storedItems.isEmpty()) {
                    claimItems.put(playerUUID, storedItems);
                }
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

        for (Map.Entry<UUID, List<ItemStack>> entry : claimItems.entrySet()) {
            List<ItemStack> storedItems = cleanItems(entry.getValue());
            if (!storedItems.isEmpty()) {
                config.set("claim-items." + entry.getKey(), storedItems);
            }
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

        boolean creatorSide = pledge.getCreatorUUID().equals(player.getUniqueId());
        if (creatorSide) {
            if (pledge.isCreatorFulfilled()) {
                return FulfillmentResult.duplicate();
            }
        } else {
            if (pledge.isTargetFulfilled()) {
                return FulfillmentResult.duplicate();
            }
        }

        ItemStack offeredItem = player.getInventory().getItemInMainHand();
        if (offeredItem == null || offeredItem.getType() == Material.AIR || offeredItem.getAmount() <= 0) {
            return FulfillmentResult.withEmptyHand();
        }

        ItemStack depositedItem = offeredItem.clone();
        String expectedOffer = creatorSide ? pledge.getCreatorOffer() : pledge.getTargetOffer();
        ItemRequirement requirement = parseRequirement(expectedOffer);
        if (requirement != null) {
            if (offeredItem.getType() != requirement.material() || offeredItem.getAmount() < requirement.amount()) {
                return FulfillmentResult.withWrongItem(expectedOffer);
            }
            depositedItem.setAmount(requirement.amount());
        }

        UUID recipientUUID = pledge.getOther(player.getUniqueId());
        List<ItemStack> updatedClaimItems = withStoredItem(recipientUUID, depositedItem);
        if (updatedClaimItems == null) {
            return FulfillmentResult.withClaimStorageFull();
        }

        if (creatorSide) {
            pledge.setCreatorFulfilled(true);
        } else {
            pledge.setTargetFulfilled(true);
        }

        claimItems.put(recipientUUID, updatedClaimItems);
        removeDepositedItemFromHand(player, offeredItem, depositedItem.getAmount());

        boolean bothOnline = Bukkit.getPlayer(pledge.getCreatorUUID()) != null && Bukkit.getPlayer(pledge.getTargetUUID()) != null;
        if (bothOnline) {
            pledge.setIntegrityDisqualified(true);
        }

        if (pledge.isComplete()) {
            pledge.setStatus(Status.HONORED);
            if (!pledge.isIntegrityDisqualified()) {
                Player creator = Bukkit.getPlayer(pledge.getCreatorUUID());
                Player target = Bukkit.getPlayer(pledge.getTargetUUID());
                int creatorHonoredCount = plugin.getIntegrityTracker().recordHonoredPledge(pledge.getCreatorUUID(), pledge.getTargetUUID(), false);
                int targetHonoredCount = plugin.getIntegrityTracker().recordHonoredPledge(pledge.getTargetUUID(), pledge.getCreatorUUID(), false);

                if (creator != null) {
                    plugin.sendSoulProgress(creator, SoulType.INTEGRITY, creatorHonoredCount, IntegrityTracker.HONORED_PLEDGE_THRESHOLD);
                    if (plugin.getIntegrityTracker().isEligibleForIntegrity(pledge.getCreatorUUID())) {
                        plugin.grantSoul(creator, SoulType.INTEGRITY);
                    }
                }

                if (target != null) {
                    plugin.sendSoulProgress(target, SoulType.INTEGRITY, targetHonoredCount, IntegrityTracker.HONORED_PLEDGE_THRESHOLD);
                    if (plugin.getIntegrityTracker().isEligibleForIntegrity(pledge.getTargetUUID())) {
                        plugin.grantSoul(target, SoulType.INTEGRITY);
                    }
                }
            }
        }

        save();
        return new FulfillmentResult(true, false, false, false, false, expectedOffer, bothOnline, pledge.getStatus() == Status.HONORED, pledge.isIntegrityDisqualified(), depositedItem);
    }

    public boolean breakPledge(Pledge pledge, Player player) {
        if (pledge == null || pledge.getStatus() == Status.BROKEN || pledge.getStatus() == Status.HONORED) return false;
        if (!pledge.involves(player.getUniqueId())) return false;

        pledge.setStatus(Status.BROKEN);
        UUID breakerUUID = player.getUniqueId();
        int brokenCount = plugin.getIntegrityTracker().recordBrokenPledge(breakerUUID);
        if (plugin.getIntegrityTracker().isShattered(breakerUUID) && brokenCount >= IntegrityTracker.BROKEN_PLEDGE_THRESHOLD) {
            Player breaker = Bukkit.getPlayer(breakerUUID);
            if (breaker != null) {
                ItemStack integritySoul = findSoulInInventory(breaker, SoulType.INTEGRITY);
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

    public Inventory createClaimInventory(Player player) {
        PledgeClaimHolder holder = new PledgeClaimHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, CLAIM_INVENTORY_SIZE, ChatColor.DARK_PURPLE + "Pledge Items");
        holder.setInventory(inventory);

        List<ItemStack> storedItems = cleanItems(claimItems.get(player.getUniqueId()));
        for (int slot = 0; slot < Math.min(storedItems.size(), CLAIM_INVENTORY_SIZE); slot++) {
            inventory.setItem(slot, storedItems.get(slot).clone());
        }
        return inventory;
    }

    public int getClaimItemCount(UUID playerUUID) {
        return cleanItems(claimItems.get(playerUUID)).size();
    }

    public void saveClaimInventory(PledgeClaimHolder holder, Inventory inventory) {
        if (holder == null || inventory == null) return;

        List<ItemStack> storedItems = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                storedItems.add(item.clone());
            }
        }

        if (storedItems.isEmpty()) {
            claimItems.remove(holder.getOwnerUUID());
        } else {
            claimItems.put(holder.getOwnerUUID(), storedItems);
        }
        save();
    }

    public void clear() {
        pledges.clear();
        claimItems.clear();
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

    private List<ItemStack> withStoredItem(UUID recipientUUID, ItemStack item) {
        if (recipientUUID == null || item == null || item.getType().isAir()) return null;

        List<ItemStack> updatedItems = cleanItems(claimItems.get(recipientUUID));
        ItemStack remaining = item.clone();

        for (ItemStack storedItem : updatedItems) {
            if (!storedItem.isSimilar(remaining)) continue;

            int availableSpace = storedItem.getMaxStackSize() - storedItem.getAmount();
            if (availableSpace <= 0) continue;

            int movedAmount = Math.min(availableSpace, remaining.getAmount());
            storedItem.setAmount(storedItem.getAmount() + movedAmount);
            remaining.setAmount(remaining.getAmount() - movedAmount);
            if (remaining.getAmount() <= 0) {
                return updatedItems;
            }
        }

        while (remaining.getAmount() > 0 && updatedItems.size() < CLAIM_INVENTORY_SIZE) {
            int movedAmount = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack splitItem = remaining.clone();
            splitItem.setAmount(movedAmount);
            updatedItems.add(splitItem);
            remaining.setAmount(remaining.getAmount() - movedAmount);
        }

        return remaining.getAmount() <= 0 ? updatedItems : null;
    }

    private List<ItemStack> cleanItems(List<ItemStack> items) {
        List<ItemStack> cleanedItems = new ArrayList<>();
        if (items == null) return cleanedItems;

        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                cleanedItems.add(item.clone());
            }
        }
        return cleanedItems;
    }

    private void removeDepositedItemFromHand(Player player, ItemStack offeredItem, int depositedAmount) {
        if (offeredItem.getAmount() <= depositedAmount) {
            player.getInventory().setItemInMainHand(null);
            return;
        }

        offeredItem.setAmount(offeredItem.getAmount() - depositedAmount);
        player.getInventory().setItemInMainHand(offeredItem);
    }

    private ItemRequirement parseRequirement(String offer) {
        if (offer == null || offer.isBlank()) return null;

        String normalized = offer.toLowerCase(Locale.ROOT)
            .replace('-', '_')
            .replaceAll("[^a-z0-9_ ]", " ")
            .trim();
        if (normalized.isBlank()) return null;

        String[] parts = normalized.split("\\s+");
        int amount = 1;
        int itemStartIndex = 0;
        String firstPart = parts[0].endsWith("x") ? parts[0].substring(0, parts[0].length() - 1) : parts[0];
        try {
            amount = Integer.parseInt(firstPart);
            itemStartIndex = 1;
        } catch (NumberFormatException ignored) {
        }

        if (amount <= 0 || itemStartIndex >= parts.length) return null;

        String itemName = String.join("_", List.of(parts).subList(itemStartIndex, parts.length));
        Material material = matchMaterialName(itemName);
        return material == null || material.isAir() ? null : new ItemRequirement(material, amount);
    }

    private Material matchMaterialName(String itemName) {
        Material material = Material.matchMaterial(itemName);
        if (material != null) return material;

        if (itemName.endsWith("s")) {
            material = Material.matchMaterial(itemName.substring(0, itemName.length() - 1));
        }
        return material;
    }

    private UUID parseUuid(String rawUuid) {
        if (rawUuid == null || rawUuid.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record ItemRequirement(Material material, int amount) {
    }

    public record FulfillmentResult(
        boolean allowed,
        boolean alreadyFulfilled,
        boolean emptyHand,
        boolean claimStorageFull,
        boolean wrongItem,
        String expectedOffer,
        boolean disqualifiedNow,
        boolean completed,
        boolean disqualified,
        ItemStack depositedItem
    ) {
        public static FulfillmentResult notAllowed() {
            return new FulfillmentResult(false, false, false, false, false, null, false, false, false, null);
        }

        public static FulfillmentResult duplicate() {
            return new FulfillmentResult(true, true, false, false, false, null, false, false, false, null);
        }

        public static FulfillmentResult withEmptyHand() {
            return new FulfillmentResult(true, false, true, false, false, null, false, false, false, null);
        }

        public static FulfillmentResult withClaimStorageFull() {
            return new FulfillmentResult(true, false, false, true, false, null, false, false, false, null);
        }

        public static FulfillmentResult withWrongItem(String expectedOffer) {
            return new FulfillmentResult(true, false, false, false, true, expectedOffer, false, false, false, null);
        }
    }
}
