package me.st4r.DSC;

import me.st4r.DSC.listener.SoulDropListener;
import me.st4r.DSC.soul.SoulItem;
import me.st4r.DSC.soul.SoulManager;
import me.st4r.DSC.soul.SoulParticleTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class DSC extends JavaPlugin {

    private SoulItem soulItem;
    private SoulManager soulManager;

    @Override
    public void onEnable() {
        getLogger().info("Hello! DSC is here :>");
        this.soulItem = new SoulItem(this);
        this.soulManager = new SoulManager(this);
        getServer().getPluginManager().registerEvents(new SoulDropListener(this), this);
        new SoulParticleTask(this).runTaskTimer(this, 20L, 15L);
    }

    public SoulItem getSoulItem() { return soulItem; }
    public SoulManager getSoulManager() { return soulManager; }
}