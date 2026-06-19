package me.st4r.DSC.world;

import me.st4r.DSC.DSC;
import me.st4r.DSC.world.SoulStateManager.SoulStateSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")

public class ResonanceHandler implements Listener {

    private static final long PORTAL_COOLDOWN_MILLIS = 2_500L;

    private final DSC plugin;
    private final Map<UUID, Long> lastPortalUse = new HashMap<>();

    private SoulStateSnapshot currentSnapshot;

    public ResonanceHandler(DSC plugin) {
        this.plugin = plugin;
    }

    public void applySoulState(SoulStateSnapshot snapshot) {
        currentSnapshot = snapshot;
    }

    public boolean canEnterResonance() {
        return currentSnapshot != null
            && currentSnapshot.allSoulsExist()
            && currentSnapshot.corruptedSouls() < 3;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (plugin.getSoulAltar().isResonancePortalBlock(event.getFrom().getBlock())) {
            if (!plugin.getSoulAltar().isResonanceOpen() || !canEnterResonance()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "The Resonance refuses to open. The souls are not aligned.");
                return;
            }

            Location destination = resolveResonanceDestination();
            if (destination == null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "The Resonance world could not be found or created.");
                return;
            }

            event.setTo(destination);
            return;
        }

        if (!isResonanceWorld(event.getTo() == null ? null : event.getTo().getWorld())) {
            return;
        }

        if (plugin.getSoulAltar().isResonanceOpen() && canEnterResonance()) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "The Resonance refuses to open. The souls are not aligned.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAltarPortalStep(PlayerMoveEvent event) {
        if (event.getTo() == null || isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        if (!plugin.getSoulAltar().isResonancePortalBlock(event.getTo().getBlock())) {
            return;
        }

        if (!plugin.getSoulAltar().isResonanceOpen() || !canEnterResonance()) {
            event.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "The Resonance refuses to open. The souls are not aligned.");
            return;
        }

        long now = System.currentTimeMillis();
        UUID playerUUID = event.getPlayer().getUniqueId();
        long lastUse = lastPortalUse.getOrDefault(playerUUID, 0L);
        if (now - lastUse < PORTAL_COOLDOWN_MILLIS) {
            return;
        }

        Location destination = resolveResonanceDestination();
        if (destination == null) {
            event.getPlayer().sendMessage(ChatColor.RED + "The Resonance world could not be found or created.");
            return;
        }

        lastPortalUse.put(playerUUID, now);
        event.getPlayer().teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private boolean isResonanceWorld(World world) {
        return world != null && world.getName().toLowerCase().contains("resonance");
    }

    private Location resolveResonanceDestination() {
        String worldName = plugin.getConfig().getString("resonance.world", "Resonance");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName)
                .environment(World.Environment.THE_END)
                .createWorld();
        }

        if (world == null) {
            return null;
        }

        String spawnPath = "resonance.spawn";
        if (plugin.getConfig().isConfigurationSection(spawnPath)
            && plugin.getConfig().contains(spawnPath + ".x")
            && plugin.getConfig().contains(spawnPath + ".y")
            && plugin.getConfig().contains(spawnPath + ".z")) {
            double x = plugin.getConfig().getDouble(spawnPath + ".x");
            double y = plugin.getConfig().getDouble(spawnPath + ".y");
            double z = plugin.getConfig().getDouble(spawnPath + ".z");
            float yaw = (float) plugin.getConfig().getDouble(spawnPath + ".yaw", 0.0D);
            float pitch = (float) plugin.getConfig().getDouble(spawnPath + ".pitch", 0.0D);
            return new Location(world, x, y, z, yaw, pitch);
        }

        return world.getSpawnLocation().add(0.5D, 0.0D, 0.5D);
    }

    private boolean isSameBlock(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }

        return first.getWorld().equals(second.getWorld())
            && first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }
}
