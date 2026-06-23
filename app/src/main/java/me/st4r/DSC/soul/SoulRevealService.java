package me.st4r.DSC.soul;

import me.st4r.DSC.DSC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class SoulRevealService implements CommandExecutor, Listener {

    private static final String PERMISSION = "dsc.admin.sreveal";
    private static final long REVEAL_INTERVAL_TICKS = 20L * 60L * 60L;
    private static final String LAST_KNOWN_PATH = "soul-reveal.last-known";

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final Map<SoulType, Location> lastKnownHolderLocations = new EnumMap<>(SoulType.class);

    private BukkitTask revealTask;

    public SoulRevealService(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
    }

    public void start() {
        stop();
        loadLastKnownHolderLocations();
        revealTask = Bukkit.getScheduler().runTaskTimer(plugin, this::revealSoulLocations, REVEAL_INTERVAL_TICKS, REVEAL_INTERVAL_TICKS);
    }

    public void stop() {
        if (revealTask != null) {
            revealTask.cancel();
            revealTask = null;
        }
    }

    public void clear() {
        lastKnownHolderLocations.clear();
        plugin.getConfig().set(LAST_KNOWN_PATH, null);
        plugin.saveConfig();
    }

    public void revealSoulLocations() {
        updateOnlineHolderLocations();

        Bukkit.broadcastMessage(ChatColor.DARK_RED + "The Hollow.. reveals the location of the current soul holders.");
        for (SoulType type : SoulType.values()) {
            Location location = resolveSoulLocation(type);
            if (location != null) {
                Bukkit.broadcastMessage(type.getColor() + "Soul of " + type.getDisplayName()
                    + ChatColor.GRAY + " is at " + ChatColor.WHITE + formatLocation(location));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use /" + label + ".");
            return true;
        }

        revealSoulLocations();
        sender.sendMessage(ChatColor.GREEN + "Forced the soul location reveal.");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        updateHolderLocations(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        updateHolderLocations(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> updateHolderLocations(player, false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> updateHolderLocations(player, false));
        }
    }

    private void updateOnlineHolderLocations() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateHolderLocations(player, false);
        }
    }

    private void updateHolderLocations(Player player, boolean persist) {
        if (player == null) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        Location location = player.getLocation().clone();
        boolean changed = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (!soulItem.isSoul(item)) {
                continue;
            }

            SoulType type = soulItem.getSoulType(item);
            if (type != null) {
                lastKnownHolderLocations.put(type, location);
                changed = true;
                if (player.isOnline()) {
                    soulManager.setHolder(type, playerUUID);
                }
            }
        }

        if (persist && changed) {
            saveLastKnownHolderLocations();
        }
    }

    private Location resolveSoulLocation(SoulType type) {
        UUID holderUUID = soulManager.getHolder(type);
        if (holderUUID != null) {
            Player holder = Bukkit.getPlayer(holderUUID);
            if (holder != null && holder.isOnline()) {
                Location location = holder.getLocation().clone();
                lastKnownHolderLocations.put(type, location);
                return location;
            }

            Location lastKnownLocation = lastKnownHolderLocations.get(type);
            if (lastKnownLocation != null) {
                return lastKnownLocation.clone();
            }
        }

        Location onlineInventoryLocation = findOnlineInventoryLocation(type);
        if (onlineInventoryLocation != null) {
            return onlineInventoryLocation;
        }

        Location patienceChestLocation = findPatienceChestLocation(type);
        if (patienceChestLocation != null) {
            return patienceChestLocation;
        }

        Location droppedSoulLocation = findDroppedSoulLocation(type);
        if (droppedSoulLocation != null) {
            return droppedSoulLocation;
        }

        Location lastKnownLocation = lastKnownHolderLocations.get(type);
        return lastKnownLocation == null ? null : lastKnownLocation.clone();
    }

    private Location findOnlineInventoryLocation(SoulType type) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == type) {
                    Location location = player.getLocation().clone();
                    lastKnownHolderLocations.put(type, location);
                    soulManager.setHolder(type, player.getUniqueId());
                    return location;
                }
            }
        }
        return null;
    }

    private Location findPatienceChestLocation(SoulType type) {
        if (type != SoulType.PATIENCE) {
            return null;
        }

        Location chestLocation = plugin.resolvePatienceChestLocation();
        if (chestLocation == null || chestLocation.getWorld() == null) {
            return null;
        }

        if (!(chestLocation.getBlock().getState() instanceof Chest chest)) {
            return null;
        }

        for (ItemStack item : chest.getBlockInventory().getContents()) {
            if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == SoulType.PATIENCE) {
                return chestLocation.clone();
            }
        }
        return null;
    }

    private Location findDroppedSoulLocation(SoulType type) {
        for (var world : Bukkit.getWorlds()) {
            for (Item itemEntity : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = itemEntity.getItemStack();
                if (stack != null && soulItem.isSoul(stack) && soulItem.getSoulType(stack) == type) {
                    return itemEntity.getLocation().clone();
                }
            }
        }
        return null;
    }

    private String formatLocation(Location location) {
        return "\"" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "\"";
    }

    private void loadLastKnownHolderLocations() {
        lastKnownHolderLocations.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(LAST_KNOWN_PATH);
        if (section == null) {
            return;
        }

        for (SoulType type : SoulType.values()) {
            String path = type.name().toLowerCase();
            String worldName = section.getString(path + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            lastKnownHolderLocations.put(type, new Location(
                world,
                section.getDouble(path + ".x"),
                section.getDouble(path + ".y"),
                section.getDouble(path + ".z")
            ));
        }
    }

    private void saveLastKnownHolderLocations() {
        plugin.getConfig().set(LAST_KNOWN_PATH, null);
        for (Map.Entry<SoulType, Location> entry : lastKnownHolderLocations.entrySet()) {
            Location location = entry.getValue();
            if (location == null || location.getWorld() == null) {
                continue;
            }

            String path = LAST_KNOWN_PATH + "." + entry.getKey().name().toLowerCase();
            plugin.getConfig().set(path + ".world", location.getWorld().getName());
            plugin.getConfig().set(path + ".x", location.getX());
            plugin.getConfig().set(path + ".y", location.getY());
            plugin.getConfig().set(path + ".z", location.getZ());
        }
        plugin.saveConfig();
    }
}
