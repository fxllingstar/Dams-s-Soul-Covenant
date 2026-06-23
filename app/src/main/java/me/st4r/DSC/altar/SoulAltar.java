package me.st4r.DSC.altar;

import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulType;
import me.st4r.DSC.world.SoulStateManager.SoulStateSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

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
    private static final double BEAM_PARTICLE_STEP = 0.8D;
    private static final int MAX_BEAM_PARTICLES = 80;
    private static final double BEAM_CENTER_OFFSET_X = -1.0D;
    private static final double BEAM_CENTER_OFFSET_Z = -1.0D;
    private static final double LEGACY_GUARDIAN_CLEANUP_RADIUS = 4.0D;
    private static final int LEGACY_PORTAL_START_OFFSET = -1;
    private static final String PORTAL_RESTORE_PATH = "resonance.portal-restore";

    public enum ResonanceResult {
        OPENED,
        ALREADY_OPEN,
        NOT_READY,
        CENTER_UNAVAILABLE
    }

    public enum ResonanceCloseResult {
        CLOSED,
        ALREADY_CLOSED,
        CENTER_UNAVAILABLE
    }

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final Map<SoulType, Location> anchorLocations = new EnumMap<>(SoulType.class);
    private final Set<SoulType> attunedSouls = new HashSet<>();
    private final Map<SoulType, UUID> attunedHolders = new EnumMap<>(SoulType.class);

    private BukkitRunnable beamTask;
    private boolean resonanceOpened;
    private long resonanceOpenedAtMillis;

    public SoulAltar(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
        this.resonanceOpenedAtMillis = plugin.getConfig().getLong("resonance.opened-at", 0L);
        loadAnchorLocations();
        cleanupLegacyGuardianVisuals();
        cleanupLegacyPortalIfCurrentMissing();
    }

    public boolean isAltar(Block block) {
        return block != null && anchorLocations.values().stream().anyMatch(location -> matchesBlock(block, location));
    }

    public boolean handleInteract(Player player, Block block, ItemStack heldItem) {
        if (player == null || block == null) {
            return false;
        }

        if (!soulItem.isSoul(heldItem)) {
            inspectAnchor(player, block);
            return true;
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

        sendAnchorStatus(player, type, holderName, karmaState);

        boolean wasComplete = areAllSoulsAttuned();
        boolean newlyAttuned = attuneSoul(type, holderUUID == null ? player.getUniqueId() : holderUUID);
        if (newlyAttuned) {
            plugin.sendOverseerWhisper(player);
        }
        if (!wasComplete && areAllSoulsAttuned()) {
            activateRitual();
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + "Resonance" + ChatColor.DARK_PURPLE + "] "
                + ChatColor.AQUA + "The Soul Altar is aligned. "
                + ChatColor.GRAY + "The Resonance will remain open while all seven souls exist and no more than two are corrupted.");
        }

        return true;
    }

    public ResonanceResult openResonance() {
        return openResonance(true, true);
    }

    public void syncResonancePortal(SoulStateSnapshot snapshot) {
        if (canMaintainResonance(snapshot)) {
            openResonance(false, false);
            return;
        }

        if (isResonanceOpen()) {
            closeResonance(ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + "Resonance" + ChatColor.DARK_PURPLE + "] "
                + ChatColor.DARK_RED + "The Resonance closes "
                + ChatColor.GRAY + "as the souls fall out of alignment.");
        }
    }

    public boolean canMaintainResonance(SoulStateSnapshot snapshot) {
        return snapshot != null
            && snapshot.allSoulsExist()
            && snapshot.corruptedSouls() <= 2;
    }

    private ResonanceResult openResonance(boolean requireAttunement, boolean chargeSoulCarriers) {
        if (requireAttunement && !areAllSoulsAttuned()) {
            return ResonanceResult.NOT_READY;
        }

        if (isResonanceOpen()) {
            return ResonanceResult.ALREADY_OPEN;
        }

        Location center = resolveCenter();
        if (center == null || center.getWorld() == null) {
            return ResonanceResult.CENTER_UNAVAILABLE;
        }

        if (chargeSoulCarriers) {
            consumeSoulCarrierHearts();
        }
        buildResonancePortal(center);
        resonanceOpened = true;
        resonanceOpenedAtMillis = System.currentTimeMillis();
        saveResonanceOpenedAt();
        return ResonanceResult.OPENED;
    }

    public ResonanceCloseResult forceCloseResonance() {
        Location center = resolveCenter();
        if (center == null || center.getWorld() == null) {
            return ResonanceCloseResult.CENTER_UNAVAILABLE;
        }

        if (!isResonanceOpen() && !hasPortalRestoreSnapshot() && !isLegacyPortalBuilt(center)) {
            return ResonanceCloseResult.ALREADY_CLOSED;
        }

        closeResonance(ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + "Resonance" + ChatColor.DARK_PURPLE + "] "
            + ChatColor.GOLD + "The Resonance is forcibly closed "
            + ChatColor.GRAY + "by an operator.");
        return ResonanceCloseResult.CLOSED;
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

        cleanupLegacyGuardianVisuals();
        attunedSouls.clear();
        attunedHolders.clear();
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

    private boolean attuneSoul(SoulType type, UUID holderUUID) {
        if (holderUUID != null) {
            attunedHolders.put(type, holderUUID);
        }

        if (attunedSouls.add(type)) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "The Soul of " + type.getDisplayName() + " resonates with its anchor.");
            return true;
        }
        return false;
    }

    private void activateRitual() {
        if (beamTask != null) {
            return;
        }

        beamTask = new BukkitRunnable() {
            @Override
            public void run() {
                renderParticleBeams();
            }
        };
        beamTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void renderParticleBeams() {
        Location center = resolveCenter();
        if (center == null || center.getWorld() == null) {
            return;
        }

        Location beamCenter = resolveBeamCenter(center);
        for (Location anchorLocation : anchorLocations.values()) {
            renderParticleBeam(anchorLocation.clone().add(0.5D, 1.05D, 0.5D), beamCenter);
        }

        World world = center.getWorld();
        world.spawnParticle(Particle.END_ROD, beamCenter, 10, 0.35D, 0.2D, 0.35D, 0.01D);
        world.spawnParticle(Particle.ELECTRIC_SPARK, beamCenter, 12, 0.3D, 0.18D, 0.3D, 0.02D);
    }

    private Location resolveBeamCenter(Location center) {
        return center.clone().add(BEAM_CENTER_OFFSET_X, 0.35D, BEAM_CENTER_OFFSET_Z);
    }

    private void renderParticleBeam(Location start, Location end) {
        if (start.getWorld() == null || end.getWorld() == null || !start.getWorld().equals(end.getWorld())) {
            return;
        }

        World world = start.getWorld();
        Vector delta = end.toVector().subtract(start.toVector());
        double distance = delta.length();
        if (distance <= 0.0D) {
            return;
        }

        int particles = Math.max(1, Math.min(MAX_BEAM_PARTICLES, (int) Math.ceil(distance / BEAM_PARTICLE_STEP)));
        Vector step = delta.normalize().multiply(distance / particles);
        Location point = start.clone();
        for (int i = 0; i <= particles; i++) {
            world.spawnParticle(Particle.END_ROD, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.03D, 0.03D, 0.03D, 0.0D);
            }
            point.add(step);
        }
    }

    private void cleanupLegacyGuardianVisuals() {
        Set<Location> cleanupCenters = new HashSet<>(anchorLocations.values());
        Location center = resolveCenter();
        if (center != null) {
            cleanupCenters.add(center);
        }

        for (Location location : cleanupCenters) {
            if (location == null || location.getWorld() == null) {
                continue;
            }

            location.getWorld().getNearbyEntitiesByType(Guardian.class, location, LEGACY_GUARDIAN_CLEANUP_RADIUS).forEach(guardian -> {
                if (guardian.isInvisible() && guardian.isSilent()) {
                    guardian.remove();
                }
            });
        }
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
            holder.sendMessage(ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + "Resonance" + ChatColor.DARK_PURPLE + "] "
                + ChatColor.RED + "The Resonance consumes "
                + ChatColor.WHITE + "five hearts" + ChatColor.RED + " from you.");
        }
    }

    private void buildResonancePortal(Location center) {
        World world = center.getWorld();
        int startX = getPortalStart(center.getBlockX());
        int startZ = getPortalStart(center.getBlockZ());
        int clearMaxY = world.getMaxHeight() - 1;

        clearLegacyResonancePortal(center);
        snapshotPortalArea(world, startX, startZ, RESONANCE_PORTAL_Y, clearMaxY);
        buildPortalArea(world, startX, startZ, clearMaxY);
    }

    private void closeResonance(String broadcastMessage) {
        Location center = resolveCenter();
        if (center != null && center.getWorld() != null) {
            clearResonancePortal(center);
        }

        resonanceOpened = false;
        resonanceOpenedAtMillis = 0L;
        saveResonanceOpenedAt();
        Bukkit.broadcastMessage(broadcastMessage);
    }

    private void clearResonancePortal(Location center) {
        World world = center.getWorld();
        clearLegacyResonancePortal(center);
        if (!restorePortalArea(center)) {
            clearPortalArea(world, getPortalStart(center.getBlockX()), getPortalStart(center.getBlockZ()));
        }
    }

    private void clearLegacyResonancePortal(Location center) {
        clearPortalArea(center.getWorld(), getLegacyPortalStart(center.getBlockX()), getLegacyPortalStart(center.getBlockZ()));
    }

    private void cleanupLegacyPortalIfCurrentMissing() {
        Location center = resolveCenter();
        if (center != null && center.getWorld() != null && !isPortalBuilt()) {
            clearLegacyResonancePortal(center);
        }
    }

    private void snapshotPortalArea(World world, int startX, int startZ, int minY, int maxY) {
        List<String> blocks = new ArrayList<>();
        for (int x = startX; x < startX + RESONANCE_PORTAL_SIZE; x++) {
            for (int z = startZ; z < startZ + RESONANCE_PORTAL_SIZE; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        blocks.add((x - startX) + "," + (y - minY) + "," + (z - startZ) + "|" + block.getBlockData().getAsString());
                    }
                }
            }
        }

        plugin.getConfig().set(PORTAL_RESTORE_PATH + ".world", world.getName());
        plugin.getConfig().set(PORTAL_RESTORE_PATH + ".start-x", startX);
        plugin.getConfig().set(PORTAL_RESTORE_PATH + ".start-z", startZ);
        plugin.getConfig().set(PORTAL_RESTORE_PATH + ".min-y", minY);
        plugin.getConfig().set(PORTAL_RESTORE_PATH + ".max-y", maxY);
        plugin.getConfig().set(PORTAL_RESTORE_PATH + ".blocks", blocks);
        plugin.saveConfig();
    }

    private boolean restorePortalArea(Location fallbackCenter) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(PORTAL_RESTORE_PATH);
        if (section == null) {
            return false;
        }

        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            world = fallbackCenter.getWorld();
        }
        if (world == null) {
            return false;
        }

        int startX = section.getInt("start-x");
        int startZ = section.getInt("start-z");
        int minY = section.getInt("min-y", RESONANCE_PORTAL_Y);
        int maxY = section.getInt("max-y", world.getMaxHeight() - 1);
        List<String> blocks = section.getStringList("blocks");
        int failedBlocks = 0;

        clearPortalColumn(world, startX, startZ, minY, maxY);
        for (String entry : blocks) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length != 2) {
                failedBlocks++;
                continue;
            }

            String[] relativeCoordinates = parts[0].split(",", 3);
            if (relativeCoordinates.length != 3) {
                failedBlocks++;
                continue;
            }

            try {
                int x = startX + Integer.parseInt(relativeCoordinates[0]);
                int y = minY + Integer.parseInt(relativeCoordinates[1]);
                int z = startZ + Integer.parseInt(relativeCoordinates[2]);
                BlockData blockData = Bukkit.createBlockData(parts[1]);
                world.getBlockAt(x, y, z).setBlockData(blockData, false);
            } catch (IllegalArgumentException exception) {
                failedBlocks++;
            }
        }

        plugin.getConfig().set(PORTAL_RESTORE_PATH, null);
        plugin.saveConfig();
        if (failedBlocks > 0) {
            plugin.getLogger().warning("Skipped " + failedBlocks + " resonance altar restore blocks because their saved block data was invalid.");
        }
        return true;
    }

    private boolean hasPortalRestoreSnapshot() {
        return plugin.getConfig().isConfigurationSection(PORTAL_RESTORE_PATH);
    }

    private void buildPortalArea(World world, int startX, int startZ, int clearMaxY) {
        clearPortalColumn(world, startX, startZ, RESONANCE_PORTAL_Y, clearMaxY);
        for (int x = startX; x < startX + RESONANCE_PORTAL_SIZE; x++) {
            for (int z = startZ; z < startZ + RESONANCE_PORTAL_SIZE; z++) {
                world.getBlockAt(x, RESONANCE_PORTAL_Y, z).setType(Material.END_PORTAL, false);
            }
        }
    }

    private void clearPortalColumn(World world, int startX, int startZ, int minY, int maxY) {
        for (int x = startX; x < startX + RESONANCE_PORTAL_SIZE; x++) {
            for (int z = startZ; z < startZ + RESONANCE_PORTAL_SIZE; z++) {
                for (int y = minY; y <= maxY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private void clearPortalArea(World world, int startX, int startZ) {
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
        return isPortalAreaBuilt(world, getPortalStart(center.getBlockX()), getPortalStart(center.getBlockZ()));
    }

    private boolean isLegacyPortalBuilt(Location center) {
        return isPortalAreaBuilt(center.getWorld(), getLegacyPortalStart(center.getBlockX()), getLegacyPortalStart(center.getBlockZ()));
    }

    private boolean isPortalAreaBuilt(World world, int startX, int startZ) {
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
        return centerBlockCoordinate - (RESONANCE_PORTAL_SIZE / 2);
    }

    private int getLegacyPortalStart(int centerBlockCoordinate) {
        return centerBlockCoordinate + LEGACY_PORTAL_START_OFFSET;
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

    private void inspectAnchor(Player player, Block block) {
        SoulType type = getAnchorType(block);
        if (type == null) {
            player.sendMessage(ChatColor.RED + "This anchor is not bound to a soul.");
            return;
        }

        ItemStack soulStack = findKnownSoulStack(type);
        UUID holderUUID = soulStack == null ? soulManager.getHolder(type) : soulManager.getHolder(soulStack);
        if (holderUUID == null) {
            holderUUID = attunedHolders.get(type);
        }

        sendAnchorStatus(
            player,
            type,
            holderUUID == null ? "None" : safeName(holderUUID),
            describeKarmaState(type, soulStack)
        );
    }

    private SoulType getAnchorType(Block block) {
        for (Map.Entry<SoulType, Location> entry : anchorLocations.entrySet()) {
            if (matchesBlock(block, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void sendAnchorStatus(Player player, SoulType type, String holderName, String karmaState) {
        player.sendMessage(type.getColor() + "Soul of " + type.getDisplayName()
            + ChatColor.GRAY + " | Holder: " + ChatColor.WHITE + holderName
            + ChatColor.GRAY + " | " + karmaState);
    }

    private ItemStack findKnownSoulStack(SoulType type) {
        if (type == SoulType.PATIENCE) {
            ItemStack patienceSoul = findPatienceChestSoul();
            if (patienceSoul != null) {
                return patienceSoul;
            }
        }

        UUID holderUUID = soulManager.getHolder(type);
        if (holderUUID != null) {
            Player holder = Bukkit.getPlayer(holderUUID);
            if (holder != null && holder.isOnline()) {
                ItemStack soul = findSoulInInventory(holder, type);
                if (soul != null) {
                    return soul;
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Item itemEntity : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = itemEntity.getItemStack();
                if (stack != null && soulItem.isSoul(stack) && soulItem.getSoulType(stack) == type) {
                    return stack;
                }
            }
        }

        return null;
    }

    private ItemStack findSoulInInventory(Player player, SoulType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == type) {
                return item;
            }
        }
        return null;
    }

    private ItemStack findPatienceChestSoul() {
        Location chestLocation = plugin.resolvePatienceChestLocation();
        if (chestLocation == null || chestLocation.getWorld() == null) {
            return null;
        }

        Block block = chestLocation.getWorld().getBlockAt(chestLocation);
        if (!(block.getState() instanceof Chest chest)) {
            return null;
        }

        for (ItemStack item : chest.getBlockInventory().getContents()) {
            if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == SoulType.PATIENCE) {
                return item;
            }
        }

        return null;
    }

    private String describeKarmaState(SoulType type, ItemStack item) {
        if (item != null && soulItem.isSoul(item)) {
            return describeKarmaState(item);
        }

        var snapshot = plugin.getSoulStateManager() == null ? null : plugin.getSoulStateManager().getCurrentSnapshot();
        if (snapshot == null || !snapshot.karmaBySoul().containsKey(type)) {
            return ChatColor.GRAY + "Karma State: " + ChatColor.DARK_GRAY + "UNKNOWN";
        }

        int karma = snapshot.karmaBySoul().get(type);
        boolean corrupted = snapshot.corruptedBySoul().getOrDefault(type, false);
        ChatColor karmaColor = karma >= 0 ? ChatColor.GREEN : ChatColor.RED;
        String karmaPrefix = karma >= 0 ? "+" : "";
        return ChatColor.GRAY + "Karma State: " + karmaColor + karmaPrefix + karma
            + (corrupted ? ChatColor.RED + " [CORRUPTED]" : ChatColor.GREEN + " [HEALTHY]");
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
