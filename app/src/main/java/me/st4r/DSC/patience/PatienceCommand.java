package me.st4r.DSC.patience;

import me.st4r.DSC.DSC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class PatienceCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "dsc.admin.patience";
    private static final long HIGHLIGHT_DURATION_TICKS = 20L * 20L;

    private final DSC plugin;
    private ArmorStand highlightMarker;
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
        if (plugin.getSoulStateManager().isSoulPresent(me.st4r.DSC.soul.SoulType.PATIENCE)) {
            sender.sendMessage(ChatColor.YELLOW + "The Soul of Patience already exists somewhere in the world.");
            return true;
        }

        Location chestLocation = plugin.seedPatienceChest(sender instanceof Player player ? player.getUniqueId() : null);
        if (chestLocation == null || chestLocation.getWorld() == null) {
            sender.sendMessage(ChatColor.RED + "Could not create the Patience chest. Check that the chest location is enabled in config.yml.");
            return true;
        }

        highlightChest(chestLocation);
        sender.sendMessage(ChatColor.GREEN + "Created the Patience chest and highlighted it for nearby players.");
        return true;
    }

    private void highlightChest(Location chestLocation) {
        clearHighlight();

        if (chestLocation.getWorld() == null) {
            return;
        }

        Location markerLocation = chestLocation.clone().add(0.5D, 1.15D, 0.5D);
        highlightMarker = chestLocation.getWorld().spawn(markerLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setGlowing(true);
            stand.setCustomName(ChatColor.AQUA + "Soul of Patience");
            stand.setCustomNameVisible(false);
        });

        var world = chestLocation.getWorld();
        world.spawnParticle(Particle.ELECTRIC_SPARK, markerLocation, 40, 0.35D, 0.45D, 0.35D, 0.02D);
        world.spawnParticle(Particle.END_ROD, markerLocation, 24, 0.3D, 0.4D, 0.3D, 0.01D);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)) {
                player.spawnParticle(Particle.ELECTRIC_SPARK, markerLocation, 16, 0.25D, 0.35D, 0.25D, 0.01D);
                player.spawnParticle(Particle.END_ROD, markerLocation, 12, 0.2D, 0.3D, 0.2D, 0.01D);
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
