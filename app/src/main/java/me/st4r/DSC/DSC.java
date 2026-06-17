package me.st4r.DSC;

import me.st4r.DSC.altar.SoulAltar;
import me.st4r.DSC.listener.SoulDropListener;
import me.st4r.DSC.listener.SoulInteractListener;
import me.st4r.DSC.listener.SoulProgressListener;
import me.st4r.DSC.listener.SoulPickUpListener;
import me.st4r.DSC.passive.PassiveEffectTask;
import me.st4r.DSC.pledge.PledgeCommand;
import me.st4r.DSC.pledge.PledgeManager;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulCommand;
import me.st4r.DSC.soul.SoulParticleTask;
import me.st4r.DSC.soul.SoulRegistery;
import me.st4r.DSC.soul.SoulType;
import me.st4r.DSC.tracker.KindnessTracker;
import me.st4r.DSC.tracker.BraveryTracker;
import me.st4r.DSC.tracker.DeterminationTracker;
import me.st4r.DSC.tracker.IntegrityTracker;
import me.st4r.DSC.tracker.JusticeTracker;
import me.st4r.DSC.tracker.PatienceTracker;
import me.st4r.DSC.tracker.PerseveranceTracker;
import me.st4r.DSC.world.FractureHandler;
import me.st4r.DSC.world.ResonanceHandler;
import me.st4r.DSC.world.SoulStateManager;
import me.st4r.DSC.world.SoulStateManager.SoulStateSnapshot;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class DSC extends JavaPlugin {

    private SoulItem soulItem;
    private SoulRegistery soulRegistery;
    private SoulManager soulManager;
    private BraveryTracker braveryTracker;
    private DeterminationTracker determinationTracker;
    private JusticeTracker justiceTracker;
    private PerseveranceTracker perseveranceTracker;
    private PatienceTracker patienceTracker;
    private IntegrityTracker integrityTracker;
    private PledgeManager pledgeManager;
    private SoulStateManager soulStateManager;
    private FractureHandler fractureHandler;
    private ResonanceHandler resonanceHandler;
    private SoulAltar soulAltar;
    private SoulCommand soulCommand;
    private SoulProgressListener soulProgressListener;
    private PassiveEffectTask passiveEffectTask;
    private KindnessTracker kindnessTracker;

    @Override
    public void onEnable() {
        getLogger().info("Hello! DSC is here :>");
        saveDefaultConfig();
        this.soulItem = new SoulItem(this);
        this.soulRegistery = new SoulRegistery(this);
        this.soulManager = new SoulManager(this);
        this.braveryTracker = new BraveryTracker();
        this.determinationTracker = new DeterminationTracker();
        this.justiceTracker = new JusticeTracker();
        this.perseveranceTracker = new PerseveranceTracker();
        this.patienceTracker = new PatienceTracker();
        this.integrityTracker = new IntegrityTracker();
        this.pledgeManager = new PledgeManager(this);
        this.pledgeManager.load();
        this.soulStateManager = new SoulStateManager(this);
        this.fractureHandler = new FractureHandler();
        this.resonanceHandler = new ResonanceHandler();
        this.soulStateManager.setHandlers(fractureHandler, resonanceHandler);
        this.kindnessTracker = new KindnessTracker(this);
        this.soulAltar = new SoulAltar(this);
        this.passiveEffectTask = new PassiveEffectTask(this);
        getServer().getPluginManager().registerEvents(new SoulDropListener(this), this);
        getServer().getPluginManager().registerEvents(new SoulPickUpListener(this), this);
        getServer().getPluginManager().registerEvents(new SoulInteractListener(this), this);
        this.soulProgressListener = new SoulProgressListener(this);
        getServer().getPluginManager().registerEvents(soulProgressListener, this);
        getServer().getPluginManager().registerEvents(fractureHandler, this);
        getServer().getPluginManager().registerEvents(resonanceHandler, this);
        getServer().getPluginManager().registerEvents(kindnessTracker, this);
        this.soulManager.resynchronizeOnlineHolders();
        registerPledgeCommand();
        registerSoulCommand();
        new SoulParticleTask(this).runTaskTimer(this, 20L, 15L);
        this.passiveEffectTask.start();
        this.soulStateManager.start();
    }

    public SoulItem getSoulItem() { return soulItem; }
    public SoulRegistery getSoulRegistery() { return soulRegistery; }
    public SoulManager getSoulManager() { return soulManager; }
    public BraveryTracker getBraveryTracker() { return braveryTracker; }
    public DeterminationTracker getDeterminationTracker() { return determinationTracker; }
    public JusticeTracker getJusticeTracker() { return justiceTracker; }
    public PerseveranceTracker getPerseveranceTracker() { return perseveranceTracker; }
    public PatienceTracker getPatienceTracker() { return patienceTracker; }
    public IntegrityTracker getIntegrityTracker() { return integrityTracker; }
    public PledgeManager getPledgeManager() { return pledgeManager; }
    public SoulStateManager getSoulStateManager() { return soulStateManager; }
    public FractureHandler getFractureHandler() { return fractureHandler; }
    public ResonanceHandler getResonanceHandler() { return resonanceHandler; }
    public SoulAltar getSoulAltar() { return soulAltar; }
    public SoulProgressListener getSoulProgressListener() { return soulProgressListener; }
    public KindnessTracker getKindnessTracker() { return kindnessTracker; }

    public boolean grantSoul(Player target, SoulType type) {
        if (target == null || type == null || soulStateManager.isSoulPresent(type)) {
            return false;
        }

        ItemStack soul = soulItem.create(type);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(soul);
        if (overflow.isEmpty()) {
            soulManager.setHolder(type, target.getUniqueId());
            soulManager.announceSoulAcquired(target, type);
        } else {
            target.getWorld().dropItemNaturally(target.getLocation(), soul);
        }

        return true;
    }

    public void resetCycle(String broadcastMessage) {
        if (broadcastMessage != null && !broadcastMessage.isBlank()) {
            Bukkit.broadcastMessage(broadcastMessage);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (item != null && soulItem.isSoul(item)) {
                    player.getInventory().setItem(slot, null);
                }
            }
        }

        for (var world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = item.getItemStack();
                if (stack != null && soulItem.isSoul(stack)) {
                    item.remove();
                }
            }
        }

        soulManager.clearAll();
        braveryTracker.clear();
        determinationTracker.clear();
        justiceTracker.clear();
        perseveranceTracker.clear();
        patienceTracker.clear();
        integrityTracker.clear();
        pledgeManager.clear();

        if (soulStateManager != null) {
            SoulStateSnapshot snapshot = soulStateManager.evaluateNow();
            if (fractureHandler != null) {
                fractureHandler.applySoulState(snapshot);
            }
            if (resonanceHandler != null) {
                resonanceHandler.applySoulState(snapshot);
            }
        }

        if (soulProgressListener != null) {
            soulProgressListener.resetRuntimeState();
        }
    }

    private void registerPledgeCommand() {
        PluginCommand pledgeCommand = getCommand("pledge");
        if (pledgeCommand == null) {
            getLogger().warning("Could not register /pledge because plugin.yml is missing the command.");
            return;
        }

        PledgeCommand executor = new PledgeCommand(this);
        pledgeCommand.setExecutor(executor);
        pledgeCommand.setTabCompleter(executor);
    }

    private void registerSoulCommand() {
        PluginCommand soulCommand = getCommand("soul");
        if (soulCommand == null) {
            getLogger().warning("Could not register /soul because plugin.yml is missing the command.");
            return;
        }

        this.soulCommand = new SoulCommand(this);
        soulCommand.setExecutor(this.soulCommand);
        soulCommand.setTabCompleter(this.soulCommand);
    }

    @Override
    public void onDisable(){
        if (soulAltar != null) {
            soulAltar.shutdown();
        }
        if (passiveEffectTask != null) {
            passiveEffectTask.cancel();
        }
        if (soulStateManager != null) {
            soulStateManager.stop();
        }
        if (pledgeManager != null) {
            pledgeManager.save();
        }
        getLogger().info("Bye! DSC is gone now :<");
    }
}
