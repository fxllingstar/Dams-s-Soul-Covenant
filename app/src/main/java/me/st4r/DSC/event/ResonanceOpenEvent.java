package me.st4r.DSC.event;

import me.st4r.DSC.world.SoulStateManager.SoulStateSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ResonanceOpenEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final SoulStateSnapshot snapshot;

    public ResonanceOpenEvent(SoulStateSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public SoulStateSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
