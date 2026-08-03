package me.st4r.ASC.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import me.st4r.ASC.world.SoulStateManager.SoulStateSnapshot;

public class SoulThresholdLostEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final SoulStateSnapshot snapshot;

    public SoulThresholdLostEvent(SoulStateSnapshot snapshot) {
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
