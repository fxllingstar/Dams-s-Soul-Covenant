package me.st4r.DSC.listener;


import me.st4r.DSC.DSC;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;


public class SoulDropListener implements Listener {
    
    private final DSC plugin;
    private final SoulItem soulItem;
    private final SoulManager soulManager;

    public SoulDropListener(DSC plugin){
        this.plugin = plugin;
        this.soulItem = plugin.getSoulItem();
        this.soulManager = plugin.getSoulManager();
    }
   

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoulSpawn(ItemSpawnEvent event){
        Item itemEntity = event.getEntity();
        if (!soulItem.isSoul(itemEntity.getItemStack())) return;

        itemEntity.setInvulnerable(true);
        itemEntity.setTicksLived(1);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoulDespawn(ItemDespawnEvent event){
        if (soulItem.isSoul(event.getEntity().getItemStack())){
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoulDamage(EntityDamageEvent event){
        if (!(event.getEntity() instanceof Item itemEntity)) return;


        ItemStack stack = itemEntity.getItemStack();
        if (!soulItem.isSoul(stack)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause == EntityDamageEvent.DamageCause.FIRE 
            || cause == EntityDamageEvent.DamageCause.FIRE_TICK
            || cause == EntityDamageEvent.DamageCause.LAVA
            || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
            || cause == EntityDamageEvent.DamageCause.VOID){
             
                event.setCancelled(true);


                SoulType type = soulItem.getSoulType(stack);
                if (type != null){
                    UUID holderUUID = soulManager.getHolder(type);
                    safelyReturnToHolder(itemEntity, stack, holderUUID);

         }
       }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event){
        Player player = event.getEntity();

        event.getDrops().removeIf(drop ->{
            if (soulItem.isSoul(drop)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if(player.isOnline()){
                        safelyForceInsert(player, drop);
                    }
                });
                 return true;
            }
           return false;
        });
    }

    private void safelyReturnToHolder(Item itemEntity, ItemStack stack, UUID holderUUID){
        if (holderUUID == null){
            routeToWorldSpawn(itemEntity);
            return;
        }

        Player player = Bukkit.getPlayer(holderUUID);

        if (player == null || !player.isOnline()){
            routeToWorldSpawn(itemEntity);
            return;
        }
        itemEntity.remove();
        safelyForceInsert(player,stack);
    }

    private void safelyForceInsert(Player player, ItemStack stack){
        Map<Integer, ItemStack> leftOver = player.getInventory().addItem(stack);

        if(!leftOver.isEmpty()){
            for (ItemStack remainingStack : leftOver.values()){
                Location safeLoc = player.getLocation();
                player.getWorld().dropItemNaturally(safeLoc, remainingStack);
            }
        }
    }
//ABSOLUTE FALLBACK!!
      private void routeToWorldSpawn(Item itemEntity) {
        Location spawn = itemEntity.getWorld().getSpawnLocation();
        itemEntity.teleport(spawn);
        itemEntity.setVelocity(itemEntity.getVelocity().zero()); // Nullify kinetic speed/momentum
    }

}
