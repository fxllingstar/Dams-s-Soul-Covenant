package me.st4r.DSC;

import me.st4r.DSC.listener.SoulDropListener;
import me.st4r.DSC.passive.PassiveEffectTask;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulParticleTask;
import me.st4r.DSC.tracker.BraveryTracker;
import me.st4r.DSC.tracker.DeterminationTracker;
import me.st4r.DSC.tracker.IntegrityTracker;
import me.st4r.DSC.tracker.JusticeTracker;
import me.st4r.DSC.tracker.PatienceTracker;
import me.st4r.DSC.tracker.PerseveranceTracker;
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
        this.passiveEffectTask = new PassiveEffectTask(this);
        getServer().getPluginManager().registerEvents(new SoulDropListener(this), this);
        new SoulParticleTask(this).runTaskTimer(this, 20L, 15L);
        this.passiveEffectTask.start();
    }

    public SoulItem getSoulItem() { return soulItem; }
    public SoulManager getSoulManager() { return soulManager; }
    public BraveryTracker getBraveryTracker() { return braveryTracker; }
    public DeterminationTracker getDeterminationTracker() { return determinationTracker; }
    public JusticeTracker getJusticeTracker() { return justiceTracker; }
    public PerseveranceTracker getPerseveranceTracker() { return perseveranceTracker; }
    public PatienceTracker getPatienceTracker() { return patienceTracker; }
    public IntegrityTracker getIntegrityTracker() { return integrityTracker; }



    @Override
    public void onDisable(){
        if (passiveEffectTask != null) {
            passiveEffectTask.cancel();
        }
        getLogger().info("Bye! DSC is gone now :<");
    }
}
