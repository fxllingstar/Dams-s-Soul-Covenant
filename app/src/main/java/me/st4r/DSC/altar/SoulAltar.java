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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SoulAltar {

    private static final Material ALTAR_BLOCK = Material.RESPAWN_ANCHOR;
    private static final int DEFAULT_SEARCH_RADIUS = 12;

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final Map<String, RitualState> ritualStates = new HashMap<>();

    private BukkitRunnable beamTask;

    public SoulAltar(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
    }

    public boolean isAltar(Block block) {
        return block != null && block.getType() == ALTAR_BLOCK;
    }

    public boolean handleInteract(Player player, Block block, ItemStack heldItem) {
        if (player == null || block == null || !isAltar(block) || !soulItem.isSoul(heldItem)) {
            return false;
        }

        SoulType type = soulItem.getSoulType(heldItem);
        if (type == null) {
            return false;
        }

        UUID holder = soulManager.getHolder(heldItem);
        if (holder == null || !holder.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the soul's bearer can attune this anchor.");
            return true;
        }

        Set<Location> cluster = collectCluster(block);
        if (cluster.size() != 7) {
            player.sendMessage(ChatColor.RED + "Seven respawn anchors must be arranged before the Soul Altar can awaken.");
            return true;
        }

        String ritualKey = buildRitualKey(cluster);
        RitualState state = ritualStates.computeIfAbsent(ritualKey, key -> new RitualState(cluster, resolveCenter(cluster)));

        if (state.active) {
            player.sendMessage(ChatColor.GRAY + "The Soul Altar is already resonating.");
            return true;
        }

        if (!state.activatedSouls.add(type)) {
            player.sendMessage(ChatColor.GRAY + "That soul has already attuned this altar.");
            return true;
        }

        player.sendMessage(ChatColor.AQUA + "The Soul of " + type.getDisplayName() + " answers the altar: " + state.activatedSouls.size() + "/7.");

        if (state.activatedSouls.size() >= 7) {
            activateRitual(state);
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "The Soul Altar awakens as all seven souls resonate together.");
        }

        return true;
    }

    public void shutdown() {
        if (beamTask != null) {
            beamTask.cancel();
            beamTask = null;
        }

        for (RitualState state : ritualStates.values()) {
            state.cleanup();
        }
        ritualStates.clear();
    }

    private Set<Location> collectCluster(Block origin) {
        int searchRadius = plugin.getConfig().getInt("altar.search-radius", DEFAULT_SEARCH_RADIUS);
        World world = origin.getWorld();
        Location base = origin.getLocation();
        Set<Location> anchors = new HashSet<>();

        for (int x = base.getBlockX() - searchRadius; x <= base.getBlockX() + searchRadius; x++) {
            for (int y = base.getBlockY() - searchRadius; y <= base.getBlockY() + searchRadius; y++) {
                for (int z = base.getBlockZ() - searchRadius; z <= base.getBlockZ() + searchRadius; z++) {
                    Block candidate = world.getBlockAt(x, y, z);
                    if (candidate.getType() == ALTAR_BLOCK) {
                        anchors.add(candidate.getLocation().getBlock().getLocation());
                    }
                }
            }
        }

        return anchors;
    }

    private String buildRitualKey(Set<Location> anchors) {
        return anchors.stream()
                .map(this::locationKey)
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private Location resolveCenter(Set<Location> anchors) {
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

        for (Location anchor : anchors) {
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

    private void activateRitual(RitualState state) {
        if (state.active) {
            return;
        }

        state.active = true;
        state.spawnVisuals();

        if (beamTask == null) {
            beamTask = new BukkitRunnable() {
                @Override
                public void run() {
                    tickVisuals();
                }
            };
            beamTask.runTaskTimer(plugin, 0L, 10L);
        }
    }

    private void tickVisuals() {
        for (RitualState state : ritualStates.values()) {
            if (state.active) {
                state.refreshTargets();
            }
        }
    }

    private final class RitualState {
        private final Set<Location> anchors;
        private final Set<SoulType> activatedSouls = new HashSet<>();
        private final List<Guardian> guardians = new ArrayList<>();
        private Location center;
        private ArmorStand centerMarker;
        private boolean active;

        private RitualState(Set<Location> anchors, Location center) {
            this.anchors = new HashSet<>(anchors);
            this.center = center;
        }

        private void spawnVisuals() {
            if (center == null || center.getWorld() == null) {
                return;
            }

            ensureCenterMarker();
            respawnGuardians();
        }

        private void refreshTargets() {
            if (center == null && !anchors.isEmpty()) {
                center = resolveCenter(anchors);
            }

            if (center == null || center.getWorld() == null) {
                cleanup();
                return;
            }

            if (centerMarker == null || centerMarker.isDead()) {
                ensureCenterMarker();
            }

            boolean needsRespawn = guardians.size() != anchors.size();
            if (!needsRespawn) {
                for (Guardian guardian : guardians) {
                    if (guardian == null || guardian.isDead()) {
                        needsRespawn = true;
                        break;
                    }
                }
            }

            if (needsRespawn) {
                respawnGuardians();
            }

            for (Guardian guardian : guardians) {
                if (guardian != null && !guardian.isDead() && centerMarker != null && !centerMarker.isDead()) {
                    guardian.setTarget(centerMarker);
                }
            }
        }

        private void ensureCenterMarker() {
            if (center == null || center.getWorld() == null) {
                return;
            }

            if (centerMarker != null && !centerMarker.isDead()) {
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
        }

        private void respawnGuardians() {
            for (Guardian guardian : guardians) {
                if (guardian != null && !guardian.isDead()) {
                    guardian.remove();
                }
            }
            guardians.clear();

            if (centerMarker == null || centerMarker.isDead()) {
                ensureCenterMarker();
            }
            if (centerMarker == null || centerMarker.isDead()) {
                return;
            }

            for (Location anchorLocation : anchors) {
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

        private void cleanup() {
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
    }
}
