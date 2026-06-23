package me.st4r.DSC.altar;

import me.st4r.DSC.DSC;
import me.st4r.DSC.world.SoulStateManager.SoulStateSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            return handleResonanceStatus(sender);
        }

        if (args[1].equalsIgnoreCase("open")) {
            return handleResonanceStatus(sender);
        }

        if (args[1].equalsIgnoreCase("close")) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "The Resonance cannot be closed by vote. "
                + ChatColor.GRAY + "It closes automatically if fewer than seven souls exist or three or more are corrupted.");
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    private boolean handleResonanceStatus(CommandSender sender) {
        SoulStateSnapshot snapshot = plugin.getSoulStateManager().evaluateAndApplyNow();
        if (plugin.getSoulAltar().canMaintainResonance(snapshot)) {
            sender.sendMessage(PREFIX + ChatColor.AQUA + "No vote is needed. "
                + ChatColor.GRAY + "The Resonance is open while all seven souls exist and no more than two are corrupted.");
            return true;
        }

        if (!snapshot.allSoulsExist()) {
            sender.sendMessage(PREFIX + ChatColor.GOLD + "The Resonance will open automatically once all seven souls exist. "
                + ChatColor.GRAY + "(" + snapshot.existingSouls() + "/7 found)");
            return true;
        }

        sender.sendMessage(PREFIX + ChatColor.DARK_RED + "The Resonance is closed because "
            + ChatColor.WHITE + snapshot.corruptedSouls()
            + ChatColor.DARK_RED + " souls are corrupted.");
        return true;
    }

    private boolean handleForceClose(CommandSender sender) {
        if (!sender.hasPermission("dsc.admin.altarspell")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You do not have permission to force close the Resonance.");
            return true;
        }

        SoulAltar.ResonanceCloseResult result = plugin.getSoulAltar().forceCloseResonance();
        switch (result) {
            case CLOSED -> sender.sendMessage(PREFIX + ChatColor.GOLD + "The Resonance was force closed.");
            case ALREADY_CLOSED -> sender.sendMessage(PREFIX + ChatColor.YELLOW + "The Resonance is already closed.");
            case CENTER_UNAVAILABLE -> sender.sendMessage(PREFIX + ChatColor.RED + "The altar center is not available. Check altar.center in config.yml.");
            default -> sender.sendMessage(PREFIX + ChatColor.RED + "The Resonance could not be force closed.");
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "/" + label + " resonance");
        if (sender.hasPermission("dsc.admin.altarspell")) {
            sender.sendMessage(ChatColor.AQUA + "/" + label + " forceclose");
        }
        sender.sendMessage(ChatColor.AQUA + "/" + label + " revive soul");
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
            for (String option : List.of("status")) {
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
