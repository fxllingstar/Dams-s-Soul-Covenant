package me.st4r.DSC.altar;

import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class SoulAltar {

    private static final Material ALTAR_BLOCK = Material.RESPAWN_ANCHOR;
    private static final int DEFAULT_SEARCH_RADIUS = 12;
    private static final int RESONANCE_PORTAL_SIZE = 4;
    private static final int RESONANCE_PORTAL_Y = 129;
    private static final double RESONANCE_HEART_COST = 10.0D;
    private static final int RESONANCE_OPEN_VOTES_REQUIRED = 2;
    private static final int RESONANCE_CLOSE_VOTES_REQUIRED = 6;
    private static final long RESONANCE_CLOSE_LOCK_MILLIS = 24L * 60L * 60L * 1000L;

    public enum ResonanceResult {
        OPENED,
        ALREADY_OPEN,
        NOT_READY,
        CENTER_UNAVAILABLE
    }

    public enum ResonanceVoteResult {
        OPENED,
        CLOSED,
        VOTE_RECORDED,
        ALREADY_VOTED,
        ALREADY_OPEN,
        ALREADY_CLOSED,
        NOT_READY,
        NOT_SOUL_CARRIER,
        CENTER_UNAVAILABLE,
        TOO_EARLY,
        PLAYERS_INSIDE
    }

    public record ResonanceVoteOutcome(
        ResonanceVoteResult result,
        int votes,
        int requiredVotes,
        Set<SoulType> voterSouls,
        long remainingMillis
    ) {
    }

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final Map<SoulType, Location> anchorLocations = new EnumMap<>(SoulType.class);
    private final Set<SoulType> attunedSouls = new HashSet<>();
    private final Map<SoulType, UUID> attunedHolders = new EnumMap<>(SoulType.class);
    private final Set<SoulType> openVotes = EnumSet.noneOf(SoulType.class);
    private final Set<SoulType> closeVotes = EnumSet.noneOf(SoulType.class);

    private BukkitRunnable beamTask;
    private ArmorStand centerMarker;
    private final List<Guardian> guardians = new ArrayList<>();
    private boolean resonanceOpened;
    private long resonanceOpenedAtMillis;

    public SoulAltar(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
        this.resonanceOpenedAtMillis = plugin.getConfig().getLong("resonance.opened-at", 0L);
        loadAnchorLocations();
    }

    public boolean isAltar(Block block) {
        return block != null && anchorLocations.values().stream().anyMatch(location -> matchesBlock(block, location));
    }

    public boolean handleInteract(Player player, Block block, ItemStack heldItem) {
        if (player == null || block == null || !soulItem.isSoul(heldItem)) {
            return false;
        }

        SoulType type = soulItem.getSoulType(heldItem);
        if (type == null) {
            return false;
        }

        Location expectedAnchor = anchorLocations.get(type);
        if (expectedAnchor == null) {
            player.sendMessage(ChatColor.RED + "This soul has not been bound to an anchor yet.");
            return true;
        }

        if (!matchesBlock(block, expectedAnchor)) {
            player.sendMessage(ChatColor.RED + "This anchor rejects your soul. Find your own.");
            glowAnchorForPlayer(player, expectedAnchor);
            return true;
        }

        UUID holderUUID = soulManager.getHolder(heldItem);
        if (holderUUID == null) {
            holderUUID = soulManager.getHolder(type);
        }

        String holderName = holderUUID == null ? "None" : safeName(holderUUID);
        String karmaState = describeKarmaState(heldItem);

        player.sendMessage(ChatColor.AQUA + "Bound anchor for " + type.getDisplayName() + ": " + ChatColor.WHITE + holderName + ChatColor.GRAY + " | " + karmaState);

        boolean wasComplete = areAllSoulsAttuned();
        attuneSoul(type, holderUUID == null ? player.getUniqueId() : holderUUID);
        if (!wasComplete && areAllSoulsAttuned()) {
            activateRitual();
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "The Soul Altar is aligned. Soul carriers may vote with /altarspell resonance open.");
        }

        return true;
    }

    public ResonanceResult openResonance() {
        if (!areAllSoulsAttuned()) {
            return ResonanceResult.NOT_READY;
        }

        if (isResonanceOpen()) {
            return ResonanceResult.ALREADY_OPEN;
        }

        Location center = resolveCenter();
        if (center == null || center.getWorld() == null) {
            return ResonanceResult.CENTER_UNAVAILABLE;
        }

        plugin.getSoulStateManager().evaluateAndApplyNow();
        consumeSoulCarrierHearts();
        buildResonancePortal(center);
        resonanceOpened = true;
        resonanceOpenedAtMillis = System.currentTimeMillis();
        saveResonanceOpenedAt();
        openVotes.clear();
        closeVotes.clear();
        return ResonanceResult.OPENED;
    }

    public ResonanceVoteOutcome voteToOpen(Player voter) {
        if (!areAllSoulsAttuned()) {
            return voteOutcome(ResonanceVoteResult.NOT_READY, openVotes.size(), RESONANCE_OPEN_VOTES_REQUIRED, Set.of(), 0L);
        }

        if (isResonanceOpen()) {
            return voteOutcome(ResonanceVoteResult.ALREADY_OPEN, openVotes.size(), RESONANCE_OPEN_VOTES_REQUIRED, Set.of(), 0L);
        }

        Set<SoulType> voterSouls = getVoteEligibleSouls(voter);
        if (voterSouls.isEmpty()) {
            return voteOutcome(ResonanceVoteResult.NOT_SOUL_CARRIER, openVotes.size(), RESONANCE_OPEN_VOTES_REQUIRED, Set.of(), 0L);
        }

        boolean changed = openVotes.addAll(voterSouls);
        closeVotes.removeAll(voterSouls);
        if (openVotes.size() >= RESONANCE_OPEN_VOTES_REQUIRED) {
            ResonanceResult openResult = openResonance();
            if (openResult == ResonanceResult.CENTER_UNAVAILABLE) {
                return voteOutcome(ResonanceVoteResult.CENTER_UNAVAILABLE, openVotes.size(), RESONANCE_OPEN_VOTES_REQUIRED, voterSouls, 0L);
            }
            if (openResult == ResonanceResult.OPENED) {
                return voteOutcome(ResonanceVoteResult.OPENED, RESONANCE_OPEN_VOTES_REQUIRED, RESONANCE_OPEN_VOTES_REQUIRED, voterSouls, 0L);
            }
            if (openResult == ResonanceResult.ALREADY_OPEN) {
                return voteOutcome(ResonanceVoteResult.ALREADY_OPEN, openVotes.size(), RESONANCE_OPEN_VOTES_REQUIRED, voterSouls, 0L);
            }
            return voteOutcome(ResonanceVoteResult.NOT_READY, openVotes.size(), RESONANCE_OPEN_VOTES_REQUIRED, voterSouls, 0L);
        }

        return voteOutcome(changed ? ResonanceVoteResult.VOTE_RECORDED : ResonanceVoteResult.ALREADY_VOTED,
            openVotes.size(), RESONANCE_OPEN_VOTES_REQUIRED, voterSouls, 0L);
    }

    public ResonanceVoteOutcome voteToClose(Player voter) {
        if (!isResonanceOpen()) {
            return voteOutcome(ResonanceVoteResult.ALREADY_CLOSED, closeVotes.size(), RESONANCE_CLOSE_VOTES_REQUIRED, Set.of(), 0L);
        }

        long remainingMillis = getCloseLockRemainingMillis();
        if (remainingMillis > 0L) {
            return voteOutcome(ResonanceVoteResult.TOO_EARLY, closeVotes.size(), RESONANCE_CLOSE_VOTES_REQUIRED, Set.of(), remainingMillis);
        }

        if (hasPlayersInResonance()) {
            return voteOutcome(ResonanceVoteResult.PLAYERS_INSIDE, closeVotes.size(), RESONANCE_CLOSE_VOTES_REQUIRED, Set.of(), 0L);
        }

        Set<SoulType> voterSouls = getVoteEligibleSouls(voter);
        if (voterSouls.isEmpty()) {
            return voteOutcome(ResonanceVoteResult.NOT_SOUL_CARRIER, closeVotes.size(), RESONANCE_CLOSE_VOTES_REQUIRED, Set.of(), 0L);
        }

        boolean changed = closeVotes.addAll(voterSouls);
        openVotes.removeAll(voterSouls);
        if (closeVotes.size() >= RESONANCE_CLOSE_VOTES_REQUIRED) {
            closeResonance();
            return voteOutcome(ResonanceVoteResult.CLOSED, RESONANCE_CLOSE_VOTES_REQUIRED, RESONANCE_CLOSE_VOTES_REQUIRED, voterSouls, 0L);
        }

        return voteOutcome(changed ? ResonanceVoteResult.VOTE_RECORDED : ResonanceVoteResult.ALREADY_VOTED,
            closeVotes.size(), RESONANCE_CLOSE_VOTES_REQUIRED, voterSouls, 0L);
    }

    public long getCloseLockRemainingMillis() {
        if (!isResonanceOpen()) {
            return 0L;
        }

        if (resonanceOpenedAtMillis <= 0L) {
            resonanceOpenedAtMillis = System.currentTimeMillis();
            saveResonanceOpenedAt();
        }

        long closeAt = resonanceOpenedAtMillis + RESONANCE_CLOSE_LOCK_MILLIS;
        return Math.max(0L, closeAt - System.currentTimeMillis());
    }

    public boolean areAllSoulsAttuned() {
        return attunedSouls.containsAll(EnumSet.allOf(SoulType.class));
    }

    public Set<SoulType> getMissingAttunedSouls() {
        Set<SoulType> missing = EnumSet.allOf(SoulType.class);
        missing.removeAll(attunedSouls);
        return missing;
    }

    public boolean isResonanceOpen() {
        return resonanceOpened || isPortalBuilt();
    }

    public boolean isResonancePortalBlock(Block block) {
        if (block == null || block.getType() != Material.END_PORTAL) {
            return false;
        }

        Location center = resolveCenter();
        if (center == null || center.getWorld() == null || !block.getWorld().equals(center.getWorld())) {
            return false;
        }

        int startX = getPortalStart(center.getBlockX());
        int startZ = getPortalStart(center.getBlockZ());
        return block.getY() == RESONANCE_PORTAL_Y
            && block.getX() >= startX
            && block.getX() < startX + RESONANCE_PORTAL_SIZE
            && block.getZ() >= startZ
            && block.getZ() < startZ + RESONANCE_PORTAL_SIZE;
    }

    public void shutdown() {
        if (beamTask != null) {
            beamTask.cancel();
            beamTask = null;
        }

        cleanupVisuals();
        attunedSouls.clear();
        attunedHolders.clear();
        openVotes.clear();
        closeVotes.clear();
    }

    private void loadAnchorLocations() {
        anchorLocations.clear();

        for (SoulType type : SoulType.values()) {
            String basePath = "altar.anchors." + type.name().toLowerCase();
            String worldName = plugin.getConfig().getString(basePath + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            double x = plugin.getConfig().getDouble(basePath + ".x");
            double y = plugin.getConfig().getDouble(basePath + ".y");
            double z = plugin.getConfig().getDouble(basePath + ".z");
            anchorLocations.put(type, new Location(world, x, y, z));
        }
    }

    private void attuneSoul(SoulType type, UUID holderUUID) {
        if (holderUUID != null) {
            attunedHolders.put(type, holderUUID);
        }

        if (attunedSouls.add(type)) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "The Soul of " + type.getDisplayName() + " resonates with its anchor.");
        }
    }

    private void activateRitual() {
        if (beamTask != null) {
            return;
        }

        spawnVisuals();
        beamTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshVisuals();
            }
        };
        beamTask.runTaskTimer(plugin, 0L, 10L);
    }

    private void spawnVisuals() {
        cleanupVisuals();

        Location center = resolveCenter();
        if (center == null || center.getWorld() == null) {
            return;
        }

        centerMarker = center.getWorld().spawn(center.clone().add(0.0D, 0.2D, 0.0D), ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(true);
            stand.setCustomNameVisible(false);
        });

        for (Location anchorLocation : anchorLocations.values()) {
            Guardian guardian = anchorLocation.getWorld().spawn(anchorLocation.clone().add(0.5D, 1.0D, 0.5D), Guardian.class, spawned -> {
                spawned.setInvisible(true);
                spawned.setSilent(true);
                spawned.setInvulnerable(true);
                spawned.setPersistent(true);
                spawned.setAI(true);
            });
            guardian.setTarget(centerMarker);
            guardians.add(guardian);
        }
    }

    private void refreshVisuals() {
        if (centerMarker == null || centerMarker.isDead()) {
            spawnVisuals();
            return;
        }

        for (Guardian guardian : guardians) {
            if (guardian != null && !guardian.isDead()) {
                guardian.setTarget(centerMarker);
            }
        }
    }

    private void cleanupVisuals() {
        if (centerMarker != null) {
            centerMarker.remove();
            centerMarker = null;
        }

        for (Guardian guardian : guardians) {
            if (guardian != null) {
                guardian.remove();
            }
        }
        guardians.clear();
    }

    private void consumeSoulCarrierHearts() {
        Set<UUID> chargedPlayers = new HashSet<>();
        for (SoulType type : SoulType.values()) {
            UUID holderUUID = attunedHolders.get(type);
            if (holderUUID == null) {
                holderUUID = soulManager.getHolder(type);
            }
            if (holderUUID == null || !chargedPlayers.add(holderUUID)) {
                continue;
            }

            Player holder = Bukkit.getPlayer(holderUUID);
            if (holder == null || !holder.isOnline()) {
                continue;
            }

            double newHealth = Math.max(1.0D, holder.getHealth() - RESONANCE_HEART_COST);
            holder.setHealth(newHealth);
            holder.sendMessage(ChatColor.DARK_PURPLE + "The Resonance consumes five hearts from you.");
        }
    }

    private void buildResonancePortal(Location center) {
        World world = center.getWorld();
        int startX = getPortalStart(center.getBlockX());
        int startZ = getPortalStart(center.getBlockZ());
        int clearMaxY = world.getMaxHeight() - 1;

        for (int x = startX; x < startX + RESONANCE_PORTAL_SIZE; x++) {
            for (int z = startZ; z < startZ + RESONANCE_PORTAL_SIZE; z++) {
                for (int y = RESONANCE_PORTAL_Y; y <= clearMaxY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                world.getBlockAt(x, RESONANCE_PORTAL_Y, z).setType(Material.END_PORTAL, false);
            }
        }
    }

    private void closeResonance() {
        Location center = resolveCenter();
        if (center != null && center.getWorld() != null) {
            clearResonancePortal(center);
        }

        resonanceOpened = false;
        resonanceOpenedAtMillis = 0L;
        openVotes.clear();
        closeVotes.clear();
        saveResonanceOpenedAt();
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "The Resonance closes as the soul carriers reach accord.");
    }

    private void clearResonancePortal(Location center) {
        World world = center.getWorld();
        int startX = getPortalStart(center.getBlockX());
        int startZ = getPortalStart(center.getBlockZ());

        for (int x = startX; x < startX + RESONANCE_PORTAL_SIZE; x++) {
            for (int z = startZ; z < startZ + RESONANCE_PORTAL_SIZE; z++) {
                Block block = world.getBlockAt(x, RESONANCE_PORTAL_Y, z);
                if (block.getType() == Material.END_PORTAL) {
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private boolean isPortalBuilt() {
        Location center = resolveCenter();
        if (center == null || center.getWorld() == null) {
            return false;
        }

        World world = center.getWorld();
        int startX = getPortalStart(center.getBlockX());
        int startZ = getPortalStart(center.getBlockZ());

        for (int x = startX; x < startX + RESONANCE_PORTAL_SIZE; x++) {
            for (int z = startZ; z < startZ + RESONANCE_PORTAL_SIZE; z++) {
                if (world.getBlockAt(x, RESONANCE_PORTAL_Y, z).getType() != Material.END_PORTAL) {
                    return false;
                }
            }
        }
        return true;
    }

    private int getPortalStart(int centerBlockCoordinate) {
        return centerBlockCoordinate - 1;
    }

    private ResonanceVoteOutcome voteOutcome(ResonanceVoteResult result, int votes, int requiredVotes, Set<SoulType> voterSouls, long remainingMillis) {
        return new ResonanceVoteOutcome(result, votes, requiredVotes, Set.copyOf(voterSouls), remainingMillis);
    }

    private Set<SoulType> getVoteEligibleSouls(Player player) {
        if (player == null) {
            return Set.of();
        }

        UUID playerUUID = player.getUniqueId();
        Set<SoulType> eligibleSouls = EnumSet.noneOf(SoulType.class);
        for (SoulType type : SoulType.values()) {
            UUID activeHolder = soulManager.getHolder(type);
            UUID attunedHolder = attunedHolders.get(type);
            if (playerUUID.equals(activeHolder) || playerUUID.equals(attunedHolder)) {
                eligibleSouls.add(type);
            }
        }

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !soulItem.isSoul(item)) {
                continue;
            }

            SoulType type = soulItem.getSoulType(item);
            if (type != null) {
                eligibleSouls.add(type);
            }
        }

        return eligibleSouls;
    }

    private boolean hasPlayersInResonance() {
        String configuredName = plugin.getConfig().getString("resonance.world", "Resonance");
        for (World world : Bukkit.getWorlds()) {
            if (!isResonanceWorld(world, configuredName)) {
                continue;
            }
            if (!world.getPlayers().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isResonanceWorld(World world, String configuredName) {
        if (world == null) {
            return false;
        }

        return world.getName().equalsIgnoreCase(configuredName)
            || world.getName().toLowerCase().contains("resonance");
    }

    private void saveResonanceOpenedAt() {
        if (resonanceOpenedAtMillis > 0L) {
            plugin.getConfig().set("resonance.opened-at", resonanceOpenedAtMillis);
        } else {
            plugin.getConfig().set("resonance.opened-at", null);
        }
        plugin.saveConfig();
    }

    private Location resolveCenter() {
        if (plugin.getConfig().getBoolean("altar.center.enabled", false)) {
            String worldName = plugin.getConfig().getString("altar.center.world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world != null) {
                double x = plugin.getConfig().getDouble("altar.center.x");
                double y = plugin.getConfig().getDouble("altar.center.y");
                double z = plugin.getConfig().getDouble("altar.center.z");
                return new Location(world, x, y, z);
            }
        }

        double totalX = 0.0D;
        double totalY = 0.0D;
        double totalZ = 0.0D;
        Location first = null;
        int count = 0;

        for (Location anchor : anchorLocations.values()) {
            if (anchor == null) {
                continue;
            }
            if (first == null) {
                first = anchor;
            }
            totalX += anchor.getX() + 0.5D;
            totalY += anchor.getY() + 0.5D;
            totalZ += anchor.getZ() + 0.5D;
            count++;
        }

        if (first == null || count == 0) {
            return null;
        }

        return new Location(first.getWorld(), totalX / count, totalY / count, totalZ / count);
    }

    private void glowAnchorForPlayer(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }

        Location pulse = location.clone().add(0.5D, 1.05D, 0.5D);
        player.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, pulse, 24, 0.25D, 0.35D, 0.25D, 0.02D);
        player.spawnParticle(org.bukkit.Particle.END_ROD, pulse, 12, 0.25D, 0.35D, 0.25D, 0.01D);
    }

    private boolean matchesBlock(Block block, Location location) {
        if (block == null || location == null || block.getWorld() == null || location.getWorld() == null) {
            return false;
        }

        return block.getWorld().equals(location.getWorld())
                && block.getX() == location.getBlockX()
                && block.getY() == location.getBlockY()
                && block.getZ() == location.getBlockZ();
    }

    private String describeKarmaState(ItemStack item) {
        if (soulManager.isShattered(item)) {
            return ChatColor.DARK_RED + "Karma State: SHATTERED";
        }

        int karma = soulManager.getKarma(item);
        if (soulManager.isCorrupted(item)) {
            return ChatColor.RED + "Karma State: " + karma + " [CORRUPTED]";
        }

        ChatColor karmaColor = karma >= 0 ? ChatColor.GREEN : ChatColor.RED;
        String karmaPrefix = karma >= 0 ? "+" : "";
        return karmaColor + "Karma State: " + karmaPrefix + karma + ChatColor.GREEN + " [HEALTHY]";
    }

    private String safeName(UUID holderUUID) {
        if (holderUUID == null) {
            return "None";
        }

        Player online = Bukkit.getPlayer(holderUUID);
        if (online != null) {
            return online.getName();
        }

        var offline = Bukkit.getOfflinePlayer(holderUUID);
        return offline.getName() != null ? offline.getName() : holderUUID.toString();
    }
}
