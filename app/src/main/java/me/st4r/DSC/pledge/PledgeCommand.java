package me.st4r.DSC.pledge;

import me.st4r.DSC.DSC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SuppressWarnings("deprecation")
public class PledgeCommand implements CommandExecutor, TabCompleter {

    private final DSC plugin;
    private final PledgeManager pledgeManager;

    public PledgeCommand(DSC plugin) {
        this.plugin = plugin;
        this.pledgeManager = plugin.getPledgeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use pledges.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> create(player, args);
            case "accept" -> accept(player, args);
            case "fulfill" -> fulfill(player, args);
            case "break" -> breakPledge(player, args);
            case "list" -> list(player);
            case "info" -> info(player, args);
            default -> sendUsage(player);
        }

        return true;
    }

    private void create(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /pledge create <player> <your offer> | <their offer>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "That player must be online to receive a new pledge.");
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot pledge with yourself.");
            return;
        }

        String body = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String[] offers = body.split("\\|", 2);
        if (offers.length != 2 || offers[0].isBlank() || offers[1].isBlank()) {
            player.sendMessage(ChatColor.YELLOW + "Separate the offers with | like: /pledge create Alex diamonds | elytra");
            return;
        }

        Pledge pledge = pledgeManager.create(player, target, offers[0].trim(), offers[1].trim());
        player.sendMessage(ChatColor.GREEN + "Created pledge #" + pledge.getId() + ". " + target.getName() + " can accept it with /pledge accept " + pledge.getId() + ".");
        target.sendMessage(ChatColor.AQUA + player.getName() + " created pledge #" + pledge.getId() + " with you. Use /pledge info " + pledge.getId() + " and /pledge accept " + pledge.getId() + ".");
    }

    private void accept(Player player, String[] args) {
        Pledge pledge = requirePledge(player, args);
        if (pledge == null) return;

        if (!pledgeManager.accept(pledge, player)) {
            player.sendMessage(ChatColor.RED + "Only the target can accept a pending pledge.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Accepted pledge #" + pledge.getId() + ".");
    }

    private void fulfill(Player player, String[] args) {
        Pledge pledge = requirePledge(player, args);
        if (pledge == null) return;

        PledgeManager.FulfillmentResult result = pledgeManager.fulfill(pledge, player);
        if (!result.allowed()) {
            player.sendMessage(ChatColor.RED + "You can only fulfill an active pledge you are part of.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Fulfillment logged for pledge #" + pledge.getId() + ".");
        if (result.disqualifiedNow()) {
            player.sendMessage(ChatColor.YELLOW + "Both pledge members were online, so this pledge cannot count toward Integrity.");
        }
        if (result.completed()) {
            player.sendMessage(result.disqualified() ? ChatColor.YELLOW + "Pledge completed without Integrity credit." : ChatColor.AQUA + "Pledge honored. Integrity remembers.");
        }
    }

    private void breakPledge(Player player, String[] args) {
        Pledge pledge = requirePledge(player, args);
        if (pledge == null) return;

        if (!pledgeManager.breakPledge(pledge, player)) {
            player.sendMessage(ChatColor.RED + "You can only break a pending or active pledge you are part of.");
            return;
        }

        player.sendMessage(ChatColor.RED + "Pledge #" + pledge.getId() + " has been broken.");
    }

    private void list(Player player) {
        List<Pledge> pledges = pledgeManager.getFor(player.getUniqueId());
        if (pledges.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You have no pledges.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "Your pledges:");
        for (Pledge pledge : pledges) {
            player.sendMessage(ChatColor.GRAY + pledgeManager.describe(pledge));
        }
    }

    private void info(Player player, String[] args) {
        Pledge pledge = requirePledge(player, args);
        if (pledge == null) return;

        OfflinePlayer creator = Bukkit.getOfflinePlayer(pledge.getCreatorUUID());
        OfflinePlayer target = Bukkit.getOfflinePlayer(pledge.getTargetUUID());
        player.sendMessage(ChatColor.AQUA + pledgeManager.describe(pledge));
        player.sendMessage(ChatColor.GRAY + (creator.getName() == null ? "Creator" : creator.getName()) + " offers: " + pledge.getCreatorOffer());
        player.sendMessage(ChatColor.GRAY + (target.getName() == null ? "Target" : target.getName()) + " offers: " + pledge.getTargetOffer());
    }

    private Pledge requirePledge(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /pledge " + args[0].toLowerCase() + " <id>");
            return null;
        }

        try {
            Pledge pledge = pledgeManager.get(Integer.parseInt(args[1]));
            if (pledge == null) {
                player.sendMessage(ChatColor.RED + "No pledge exists with that id.");
            }
            return pledge;
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "Pledge id must be a number.");
            return null;
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.AQUA + "/pledge create <player> <your offer> | <their offer>");
        player.sendMessage(ChatColor.AQUA + "/pledge accept <id>, /pledge fulfill <id>, /pledge break <id>, /pledge list, /pledge info <id>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "accept", "fulfill", "break", "list", "info").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase()))
                .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }

        return List.of();
    }
}
