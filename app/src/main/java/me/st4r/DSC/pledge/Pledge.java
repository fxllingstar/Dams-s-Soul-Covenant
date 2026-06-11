package me.st4r.DSC.pledge;

import java.util.UUID;

public class Pledge {

    public enum Status {
        PENDING,
        ACTIVE,
        HONORED,
        BROKEN
    }

    private final int id;
    private final UUID creatorUUID;
    private final UUID targetUUID;
    private final String creatorOffer;
    private final String targetOffer;
    private final long createdAt;

    private Status status;
    private boolean creatorFulfilled;
    private boolean targetFulfilled;
    private boolean integrityDisqualified;

    public Pledge(int id, UUID creatorUUID, UUID targetUUID, String creatorOffer, String targetOffer, long createdAt) {
        this.id = id;
        this.creatorUUID = creatorUUID;
        this.targetUUID = targetUUID;
        this.creatorOffer = creatorOffer;
        this.targetOffer = targetOffer;
        this.createdAt = createdAt;
        this.status = Status.PENDING;
    }

    public int getId() { return id; }
    public UUID getCreatorUUID() { return creatorUUID; }
    public UUID getTargetUUID() { return targetUUID; }
    public String getCreatorOffer() { return creatorOffer; }
    public String getTargetOffer() { return targetOffer; }
    public long getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public boolean isCreatorFulfilled() { return creatorFulfilled; }
    public boolean isTargetFulfilled() { return targetFulfilled; }
    public boolean isIntegrityDisqualified() { return integrityDisqualified; }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setCreatorFulfilled(boolean creatorFulfilled) {
        this.creatorFulfilled = creatorFulfilled;
    }

    public void setTargetFulfilled(boolean targetFulfilled) {
        this.targetFulfilled = targetFulfilled;
    }

    public void setIntegrityDisqualified(boolean integrityDisqualified) {
        this.integrityDisqualified = integrityDisqualified;
    }

    public boolean involves(UUID playerUUID) {
        return creatorUUID.equals(playerUUID) || targetUUID.equals(playerUUID);
    }

    public UUID getOther(UUID playerUUID) {
        if (creatorUUID.equals(playerUUID)) return targetUUID;
        if (targetUUID.equals(playerUUID)) return creatorUUID;
        return null;
    }

    public boolean isComplete() {
        return creatorFulfilled && targetFulfilled;
    }
}
