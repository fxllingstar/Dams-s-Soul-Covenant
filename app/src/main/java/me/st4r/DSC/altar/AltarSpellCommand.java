package me.st4r.DSC.altar;

import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class AltarSpellCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + "Resonance" + ChatColor.DARK_PURPLE + "] ";

    private final DSC plugin;

    public AltarSpellCommand(DSC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("resonance")) {
            return handleResonance(sender, label, args);
        }

        if (args[0].equalsIgnoreCase("forceclose")) {
            return handleForceClose(sender);
        }

        if (args[0].equalsIgnoreCase("revive") && args.length >= 2 && args[1].equalsIgnoreCase("soul")) {
            sender.sendMessage(ChatColor.YELLOW + "The revive soul spell has not been implemented yet.");
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    private boolean handleResonance(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only soul carriers can vote on the Resonance.");
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender, label);
            return true;
        }

        SoulAltar altar = plugin.getSoulAltar();

        if (args[1].equalsIgnoreCase("open")) {
            return handleOpenVote(player, altar);
        }

        if (args[1].equalsIgnoreCase("close")) {
            return handleCloseVote(player, altar);
        }

        sendUsage(sender, label);
        return true;
    }

    private boolean handleForceClose(CommandSender sender) {
        if (!sender.hasPermission("dsc.admin.altarspell")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You do not have permission to force close the Resonance.");
            return true;
        }

        SoulAltar.ResonanceVoteResult result = plugin.getSoulAltar().forceCloseResonance();
        switch (result) {
            case CLOSED -> sender.sendMessage(PREFIX + ChatColor.GOLD + "The Resonance was force closed.");
            case ALREADY_CLOSED -> sender.sendMessage(PREFIX + ChatColor.YELLOW + "The Resonance is already closed.");
            case CENTER_UNAVAILABLE -> sender.sendMessage(PREFIX + ChatColor.RED + "The altar center is not available. Check altar.center in config.yml.");
            default -> sender.sendMessage(PREFIX + ChatColor.RED + "The Resonance could not be force closed.");
        }
        return true;
    }

    private boolean handleOpenVote(Player player, SoulAltar altar) {
        SoulAltar.ResonanceVoteOutcome outcome = altar.voteToOpen(player);
        switch (outcome.result()) {
            case OPENED -> plugin.getServer().broadcastMessage(PREFIX + ChatColor.AQUA + "The Resonance "
                + ChatColor.GREEN + "opens" + ChatColor.AQUA + " at the Soul Altar.");
            case VOTE_RECORDED -> plugin.getServer().broadcastMessage(PREFIX + ChatColor.AQUA + player.getName()
                + ChatColor.GRAY + " voted to " + ChatColor.GREEN + "open" + ChatColor.GRAY + " the Resonance "
                + formatVoteProgress(outcome));
            case ALREADY_VOTED -> player.sendMessage(PREFIX + ChatColor.YELLOW + "Your soul vote is already counted "
                + ChatColor.GRAY + "to " + ChatColor.GREEN + "open" + ChatColor.GRAY + " the Resonance "
                + formatVoteProgress(outcome));
            case ALREADY_OPEN -> player.sendMessage(PREFIX + ChatColor.AQUA + "The Resonance is already open.");
            case CENTER_UNAVAILABLE -> player.sendMessage(PREFIX + ChatColor.RED + "The altar center is not available. Check altar.center in config.yml.");
            case NOT_SOUL_CARRIER -> player.sendMessage(PREFIX + ChatColor.RED + "Only current soul carriers can vote on the Resonance.");
            case NOT_READY -> {
                String missing = altar.getMissingAttunedSouls().stream()
                    .map(SoulType::getDisplayName)
                    .collect(Collectors.joining(", "));
                player.sendMessage(PREFIX + ChatColor.GOLD + "The altar is missing: " + ChatColor.WHITE + missing);
            }
            default -> player.sendMessage(PREFIX + ChatColor.RED + "That vote cannot be used to open the Resonance.");
        }
        return true;
    }

    private boolean handleCloseVote(Player player, SoulAltar altar) {
        SoulAltar.ResonanceVoteOutcome outcome = altar.voteToClose(player);
        switch (outcome.result()) {
            case CLOSED -> player.sendMessage(PREFIX + ChatColor.GOLD + "Your vote closes the Resonance.");
            case VOTE_RECORDED -> plugin.getServer().broadcastMessage(PREFIX + ChatColor.AQUA + player.getName()
                + ChatColor.GRAY + " voted to " + ChatColor.GOLD + "close" + ChatColor.GRAY + " the Resonance "
                + formatVoteProgress(outcome));
            case ALREADY_VOTED -> player.sendMessage(PREFIX + ChatColor.YELLOW + "Your soul vote is already counted "
                + ChatColor.GRAY + "to " + ChatColor.GOLD + "close" + ChatColor.GRAY + " the Resonance "
                + formatVoteProgress(outcome));
            case ALREADY_CLOSED -> player.sendMessage(PREFIX + ChatColor.YELLOW + "The Resonance is already closed.");
            case TOO_EARLY -> player.sendMessage(PREFIX + ChatColor.GOLD + "The Resonance must remain open for "
                + ChatColor.WHITE + formatDuration(outcome.remainingMillis())
                + ChatColor.GRAY + " more before close voting can begin.");
            case PLAYERS_INSIDE -> player.sendMessage(PREFIX + ChatColor.RED + "The Resonance cannot close while someone is inside it.");
            case NOT_SOUL_CARRIER -> player.sendMessage(PREFIX + ChatColor.RED + "Only current soul carriers can vote on the Resonance.");
            default -> player.sendMessage(PREFIX + ChatColor.RED + "That vote cannot be used to close the Resonance.");
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "/" + label + " resonance open");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " resonance close");
        if (sender.hasPermission("dsc.admin.altarspell")) {
            sender.sendMessage(ChatColor.AQUA + "/" + label + " forceclose");
        }
        sender.sendMessage(ChatColor.AQUA + "/" + label + " revive soul");
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String formatVoteProgress(SoulAltar.ResonanceVoteOutcome outcome) {
        return ChatColor.DARK_GRAY + "[" + ChatColor.WHITE + outcome.votes()
            + ChatColor.GRAY + "/" + ChatColor.WHITE + outcome.requiredVotes()
            + ChatColor.DARK_GRAY + "]";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> rootOptions = sender.hasPermission("dsc.admin.altarspell")
                ? List.of("resonance", "forceclose", "revive")
                : List.of("resonance", "revive");
            for (String option : rootOptions) {
                if (option.startsWith(prefix)) {
                    options.add(option);
                }
            }
            return options;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("resonance")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String option : List.of("open", "close")) {
                if (option.startsWith(prefix)) {
                    options.add(option);
                }
            }
            return options;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("revive")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("soul".startsWith(prefix)) {
                options.add("soul");
            }
            return options;
        }

        return List.of();
    }
}
