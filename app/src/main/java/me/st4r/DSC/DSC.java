package me.st4r.DSC;

import me.st4r.DSC.listener.SoulDropListener;
import me.st4r.DSC.listener.SoulPickUpListener;
import me.st4r.DSC.passive.PassiveEffectTask;
import me.st4r.DSC.pledge.PledgeCommand;
import me.st4r.DSC.pledge.PledgeManager;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulParticleTask;
import me.st4r.DSC.tracker.BraveryTracker;
import me.st4r.DSC.tracker.DeterminationTracker;
import me.st4r.DSC.tracker.IntegrityTracker;
import me.st4r.DSC.tracker.JusticeTracker;
import me.st4r.DSC.tracker.PatienceTracker;
import me.st4r.DSC.tracker.PerseveranceTracker;
import me.st4r.DSC.world.FractureHandler;
import me.st4r.DSC.world.ResonanceHandler;
import me.st4r.DSC.world.SoulStateManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DSC extends JavaPlugin {

    private SoulItem soulItem;
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
    private PassiveEffectTask passiveEffectTask;

    @Override
    public void onEnable() {
        getLogger().info("Hello! DSC is here :>");
        this.soulItem = new SoulItem(this);
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
        this.passiveEffectTask = new PassiveEffectTask(this);
        getServer().getPluginManager().registerEvents(new SoulDropListener(this), this);
        getServer().getPluginManager().registerEvents(new SoulPickUpListener(this), this);
        getServer().getPluginManager().registerEvents(fractureHandler, this);
        getServer().getPluginManager().registerEvents(resonanceHandler, this);
        registerPledgeCommand();
        new SoulParticleTask(this).runTaskTimer(this, 20L, 15L);
        this.passiveEffectTask.start();
        this.soulStateManager.start();
    }

    public SoulItem getSoulItem() { return soulItem; }
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

    @Override
    public void onDisable(){
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
