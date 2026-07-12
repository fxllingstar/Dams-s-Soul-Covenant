package me.st4r.DSC.altar;

import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class ReviveSoulCommand implements CommandExecutor {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + "Soul Revival" + ChatColor.DARK_PURPLE + "] ";

    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;

    public ReviveSoulCommand(DSC plugin) {
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            sender.sendMessage(ChatColor.AQUA + "/" + label);
            return true;
        }

        List<RevivalTarget> targets = findRevivalTargets();
        if (targets.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No corrupted souls are available to revive.");
            return true;
        }

        RevivalTarget target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        ItemStack revivedSoul = soulManager.cleanse(target.item());
        target.update(revivedSoul);

        plugin.getSoulStateManager().evaluateAndApplyNow();
        Bukkit.broadcastMessage(PREFIX + target.type().getColor() + "The Soul of "
            + target.type().getDisplayName() + ChatColor.GRAY + " has been revived.");
        return true;
    }

    private List<RevivalTarget> findRevivalTargets() {
        List<RevivalTarget> targets = new ArrayList<>();
        collectPlayerInventoryTargets(targets);
        collectPatienceChestTargets(targets);
        collectDroppedItemTargets(targets);
        return targets;
    }

    private void collectPlayerInventoryTargets(List<RevivalTarget> targets) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                SoulType type = getRevivalType(item);
                if (type == null) {
                    continue;
                }

                int targetSlot = slot;
                targets.add(new RevivalTarget(type, item, updatedItem -> inventory.setItem(targetSlot, updatedItem)));
            }
        }
    }

    private void collectPatienceChestTargets(List<RevivalTarget> targets) {
        Location chestLocation = plugin.resolvePatienceChestLocation();
        if (chestLocation == null || chestLocation.getWorld() == null) {
            return;
        }

        if (!(chestLocation.getWorld().getBlockAt(chestLocation).getState() instanceof Chest chest)) {
            return;
        }

        Inventory inventory = chest.getBlockInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            SoulType type = getRevivalType(item);
            if (type == null) {
                continue;
            }

            int targetSlot = slot;
            targets.add(new RevivalTarget(type, item, updatedItem -> inventory.setItem(targetSlot, updatedItem)));
        }
    }

    private void collectDroppedItemTargets(List<RevivalTarget> targets) {
        for (var world : Bukkit.getWorlds()) {
            for (Item itemEntity : world.getEntitiesByClass(Item.class)) {
                ItemStack item = itemEntity.getItemStack();
                SoulType type = getRevivalType(item);
                if (type != null) {
                    targets.add(new RevivalTarget(type, item, itemEntity::setItemStack));
                }
            }
        }
    }

    private SoulType getRevivalType(ItemStack item) {
        if (item == null || !soulItem.isSoul(item)) {
            return null;
        }

        if (!soulManager.isCorrupted(item) && !soulManager.isShattered(item)) {
            return null;
        }

        return soulItem.getSoulType(item);
    }

    private record RevivalTarget(SoulType type, ItemStack item, Consumer<ItemStack> updater) {
        private void update(ItemStack updatedItem) {
            updater.accept(updatedItem);
        }
    }
}
