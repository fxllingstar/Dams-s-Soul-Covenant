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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class SoulAltar {

    private static final Material ALTAR_BLOCK = Material.RESPAWN_ANCHOR;
    private static final int DEFAULT_SEARCH_RADIUS = 12;

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final Map<SoulType, Location> anchorLocations = new EnumMap<>(SoulType.class);
    private final Set<SoulType> attunedSouls = new HashSet<>();

    private BukkitRunnable beamTask;
    private ArmorStand centerMarker;
    private final List<Guardian> guardians = new ArrayList<>();

    public SoulAltar(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
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

        attuneSoul(type);
        if (attunedSouls.size() >= SoulType.values().length) {
            activateRitual();
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "The Soul Altar awakens as all seven souls resonate together.");
        }

        return true;
    }

    public void shutdown() {
        if (beamTask != null) {
            beamTask.cancel();
            beamTask = null;
        }

        cleanupVisuals();
        attunedSouls.clear();
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

    private void attuneSoul(SoulType type) {
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

        return ChatColor.GREEN + "Karma State: +" + karma + " [HEALTHY]";
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
