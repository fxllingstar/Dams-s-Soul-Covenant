package me.st4r.DSC.soul;

import me.st4r.DSC.DSC;
import me.st4r.DSC.world.SoulStateManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

 @SuppressWarnings("deprecation")

public class SoulCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "dsc.admin.soul";

    private final DSC plugin;
    private final SoulRegistery soulRegistery;
    private final SoulItem soulItem;
    private final SoulManager soulManager;
    private final SoulStateManager soulStateManager;

    public SoulCommand(DSC plugin) {
        this.plugin = plugin;
        this.soulRegistery = plugin.getSoulRegistery();
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
        this.soulStateManager = plugin.getSoulStateManager();
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

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> handleGive(sender, label, args);
            case "giveall" -> handleGiveAll(sender, label, args);
            case "drop" -> handleDrop(sender, label, args);
            case "list" -> handleList(sender);
            case "state" -> handleState(sender);
            case "trigger" -> handleTrigger(sender, label, args);
            case "simulate" -> handleSimulate(sender, label, args);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean handleGive(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " give <player> <soul>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player must be online.");
            return true;
        }

        SoulType type = parseType(args[2]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Unknown soul type.");
            return true;
        }

        if (soulStateManager.isSoulPresent(type)) {
            sender.sendMessage(ChatColor.YELLOW + "The Soul of " + type.getDisplayName() + " already exists somewhere in the world.");
            return true;
        }

        if (findSoulInInventory(target, type) != null) {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " already has the Soul of " + type.getDisplayName() + ".");
            return true;
        }

        ItemStack soul = soulItem.create(type, target.getUniqueId());
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(soul);
        if (overflow.isEmpty()) {
            soulManager.setHolder(type, target.getUniqueId());
            soulManager.announceSoulAcquired(target, type);
            sender.sendMessage(ChatColor.GREEN + "Granted the Soul of " + type.getDisplayName() + " to " + target.getName() + ".");
        } else {
            Location dropLocation = target.getLocation();
            target.getWorld().dropItemNaturally(dropLocation, soul);
            sender.sendMessage(ChatColor.YELLOW + target.getName() + "'s inventory was full, so the soul was dropped at their feet.");
        }

        return true;
    }

    private boolean handleGiveAll(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " giveall <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player must be online.");
            return true;
        }

        int granted = 0;
        for (SoulType type : soulRegistery.getRegisteredTypes()) {
            if (soulStateManager.isSoulPresent(type)) {
                continue;
            }

            if (findSoulInInventory(target, type) != null) {
                continue;
            }

            ItemStack soul = soulItem.create(type, target.getUniqueId());
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(soul);
            if (overflow.isEmpty()) {
                soulManager.setHolder(type, target.getUniqueId());
                soulManager.announceSoulAcquired(target, type);
            } else {
                target.getWorld().dropItemNaturally(target.getLocation(), soul);
            }
            granted++;
        }

        sender.sendMessage(ChatColor.GREEN + "Granted " + granted + " soul(s) to " + target.getName() + ".");
        return true;
    }

    private boolean handleDrop(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " drop <soul>");
            return true;
        }

        SoulType type = parseType(args[1]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Unknown soul type.");
            return true;
        }

        if (soulStateManager.isSoulPresent(type)) {
            sender.sendMessage(ChatColor.YELLOW + "The Soul of " + type.getDisplayName() + " already exists somewhere in the world.");
            return true;
        }

        ItemStack soul = soulItem.create(type, sender instanceof Player player ? player.getUniqueId() : null);
        soulManager.setHolder(type, null);

        if (sender instanceof Player player) {
            player.getWorld().dropItemNaturally(player.getLocation(), soul);
            sender.sendMessage(ChatColor.GREEN + "Dropped the Soul of " + type.getDisplayName() + " at your location.");
        } else if (!Bukkit.getWorlds().isEmpty()) {
            Location spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
            Bukkit.getWorlds().getFirst().dropItemNaturally(spawn, soul);
            sender.sendMessage(ChatColor.GREEN + "Dropped the Soul of " + type.getDisplayName() + " at world spawn.");
        } else {
            sender.sendMessage(ChatColor.RED + "No loaded world is available.");
        }

        return true;
    }

    private boolean handleList(CommandSender sender) {
        soulStateManager.evaluateNow();
        sender.sendMessage(ChatColor.AQUA + "Soul registry:");
        for (SoulType type : soulRegistery.getRegisteredTypes()) {
            UUID holderUUID = soulManager.getHolder(type);
            String holderName = holderUUID == null ? "none" : safeName(holderUUID);
            sender.sendMessage(ChatColor.GRAY + "- " + type.getDisplayName() + ChatColor.WHITE + " holder=" + holderName);
        }
        return true;
    }

    private boolean handleState(CommandSender sender) {
        SoulStateManager.SoulStateSnapshot snapshot = soulStateManager.evaluateNow();
        sender.sendMessage(ChatColor.AQUA + "Soul state: " + ChatColor.WHITE + snapshot.state());
        sender.sendMessage(ChatColor.GRAY + "Existing souls: " + snapshot.existingSouls() + "/" + SoulType.values().length);
        sender.sendMessage(ChatColor.GRAY + "Corrupted souls: " + snapshot.corruptedSouls());
        sender.sendMessage(ChatColor.GRAY + "Total karma: " + snapshot.totalKarma());
        return true;
    }

    private boolean handleTrigger(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " trigger <state|cycle>");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "state", "refresh", "evaluate" -> {
                SoulStateManager.SoulStateSnapshot snapshot = soulStateManager.evaluateAndApplyNow();
                sender.sendMessage(ChatColor.GREEN + "Triggered the soul state event. Current state: " + snapshot.state() + ".");
                yield true;
            }
            case "cycle", "reset" -> {
                String message = args.length > 2
                    ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                    : ChatColor.GOLD + "The soul cycle was forced to end.";
                plugin.resetCycle(message);
                sender.sendMessage(ChatColor.GREEN + "Triggered the cycle reset event.");
                yield true;
            }
            default -> {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " trigger <state|cycle>");
                yield true;
            }
        };
    }

    private boolean handleSimulate(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " simulate <player> <soul|all>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player must be online.");
            return true;
        }

        if (args[2].equalsIgnoreCase("all")) {
            int granted = plugin.simulateAllSoulAwards(target);
            sender.sendMessage(ChatColor.GREEN + "Simulated " + granted + " soul award(s) for " + target.getName() + ".");
            return true;
        }

        SoulType type = parseType(args[2]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Unknown soul type.");
            return true;
        }

        if (plugin.simulateSoulAward(target, type)) {
            sender.sendMessage(ChatColor.GREEN + "Simulated the Soul of " + type.getDisplayName() + " for " + target.getName() + ".");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Could not simulate that soul right now. It may already exist, or the debug path could not complete.");
        }
        return true;
    }

    private SoulType parseType(String input) {
        return soulRegistery.getByName(input).orElse(null);
    }

    private ItemStack findSoulInInventory(Player player, SoulType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && soulItem.isSoul(item) && soulItem.getSoulType(item) == type) {
                return item;
            }
        }
        return null;
    }

    private String safeName(UUID holderUUID) {
        Player online = Bukkit.getPlayer(holderUUID);
        if (online != null) {
            return online.getName();
        }
        var offline = Bukkit.getOfflinePlayer(holderUUID);
        return offline.getName() != null ? offline.getName() : holderUUID.toString();
    }

    private void sendUsage(CommandSender sender, String label) {
            sender.sendMessage(ChatColor.AQUA + "/" + label + " give <player> <soul>");
            sender.sendMessage(ChatColor.AQUA + "/" + label + " giveall <player>");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " drop <soul>");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " list");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " state");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " trigger <state|cycle>");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " simulate <player> <soul|all>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("give", "giveall", "drop", "list", "state", "trigger", "simulate")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    options.add(option);
                }
            }
            return options;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("trigger")) {
            for (String option : List.of("state", "cycle")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    options.add(option);
                }
            }
            return options;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("simulate")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    options.add(player.getName());
                }
            }
            return options;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("simulate")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            if ("all".startsWith(prefix)) {
                options.add("all");
            }
            for (SoulType type : soulRegistery.getRegisteredTypes()) {
                if (type.name().toLowerCase(Locale.ROOT).startsWith(prefix)
                    || type.getDisplayName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    options.add(type.name().toLowerCase(Locale.ROOT));
                }
            }
            return options;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveall"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    options.add(player.getName());
                }
            }
            return options;
        }

        if ((args.length == 2 && args[0].equalsIgnoreCase("drop"))
            || (args.length == 3 && args[0].equalsIgnoreCase("give"))) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (SoulType type : soulRegistery.getRegisteredTypes()) {
                if (type.name().toLowerCase(Locale.ROOT).startsWith(prefix)
                    || type.getDisplayName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    options.add(type.name().toLowerCase(Locale.ROOT));
                }
            }
            return options;
        }

        return List.of();
    }
}
