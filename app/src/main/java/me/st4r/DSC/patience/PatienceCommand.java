package me.st4r.DSC.patience;

import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class PatienceCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "dsc.admin.patience";
    private static final long HIGHLIGHT_DURATION_TICKS = 20L * 20L;

    private final DSC plugin;
    private BlockDisplay highlightMarker;
    private BukkitTask highlightCleanupTask;

    public PatienceCommand(DSC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use /" + label + ".");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            return handleCreate(sender, label);
        }

        sendUsage(sender, label);
        return true;
    }

    private boolean handleCreate(CommandSender sender, String label) {
        if (plugin.getSoulStateManager().isSoulPresent(SoulType.PATIENCE)) {
            sender.sendMessage(ChatColor.YELLOW + "The Soul of Patience already exists somewhere in the world.");
            return true;
        }

        DSC.PatienceChestSeedResult seedResult = plugin.seedPatienceChestDetailed(sender instanceof Player player ? player.getUniqueId() : null);
        if (!seedResult.success()) {
            sender.sendMessage(getCreateFailureMessage(seedResult.status()));
            return true;
        }

        Location chestLocation = seedResult.location();
        highlightChest(chestLocation);
        sender.sendMessage(ChatColor.GREEN + "Created the Patience chest and highlighted it for nearby players.");
        return true;
    }

    private String getCreateFailureMessage(DSC.PatienceChestSeedStatus status) {
        return switch (status) {
            case ALREADY_PRESENT -> ChatColor.YELLOW + "The Soul of Patience already exists somewhere in the world.";
            case LOCATION_UNAVAILABLE -> ChatColor.RED + "Could not create the Patience chest. Check that the chest location is enabled and the world is loaded in config.yml.";
            case BLOCK_NOT_CHEST -> ChatColor.RED + "Created the Patience chest block, but the server did not expose it as a chest inventory.";
            case INVENTORY_WRITE_FAILED -> ChatColor.RED + "Created the Patience chest, but could not place the soul item inside it. Check for protections or plugins affecting that block.";
            case UNAVAILABLE -> ChatColor.RED + "Could not create the Patience chest because the soul systems are not ready.";
            case SUCCESS -> ChatColor.GREEN + "Created the Patience chest.";
        };
    }

    public void highlightChest(Location chestLocation) {
        clearHighlight();

        if (chestLocation.getWorld() == null) {
            return;
        }

        Location markerLocation = chestLocation.toBlockLocation();
        highlightMarker = chestLocation.getWorld().spawn(markerLocation, BlockDisplay.class, display -> {
            display.setBlock(chestLocation.getBlock().getType() == Material.CHEST
                ? chestLocation.getBlock().getBlockData()
                : Material.CHEST.createBlockData());
            display.setGlowing(true);
            display.setGlowColorOverride(Color.AQUA);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setPersistent(false);
            display.setShadowRadius(0.0F);
            display.setViewRange(128.0F);
            display.setCustomName(ChatColor.AQUA + "Soul of Patience");
            display.setCustomNameVisible(false);
        });

        var world = chestLocation.getWorld();
        Location particleLocation = chestLocation.clone().add(0.5D, 1.15D, 0.5D);
        world.spawnParticle(Particle.ELECTRIC_SPARK, particleLocation, 40, 0.35D, 0.45D, 0.35D, 0.02D);
        world.spawnParticle(Particle.END_ROD, particleLocation, 24, 0.3D, 0.4D, 0.3D, 0.01D);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)) {
                player.spawnParticle(Particle.ELECTRIC_SPARK, particleLocation, 16, 0.25D, 0.35D, 0.25D, 0.01D);
                player.spawnParticle(Particle.END_ROD, particleLocation, 12, 0.2D, 0.3D, 0.2D, 0.01D);
            } else {
                player.sendMessage(ChatColor.AQUA + "The Patience chest has been revealed in " + world.getName() + ".");
            }
        }

        highlightCleanupTask = Bukkit.getScheduler().runTaskLater(plugin, this::clearHighlight, HIGHLIGHT_DURATION_TICKS);
    }

    public void shutdown() {
        clearHighlight();
    }

    private void clearHighlight() {
        if (highlightCleanupTask != null) {
            highlightCleanupTask.cancel();
            highlightCleanupTask = null;
        }

        if (highlightMarker != null) {
            highlightMarker.remove();
            highlightMarker = null;
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "/" + label + " create");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }

        return List.of();
    }
}
