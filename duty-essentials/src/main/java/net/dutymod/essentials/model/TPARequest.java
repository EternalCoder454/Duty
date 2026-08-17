package net.dutymod.essentials.model;

import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.level.DutyServerPlayer;

public class TPARequest {

    public final DutyServerPlayer sender;
    public final DutyServerPlayer receiver;
    public final long timestamp;
    public final boolean isHere;

    public TPARequest(DutyServerPlayer sender, DutyServerPlayer receiver, long timestamp, boolean isHere) {
        this.sender = sender;
        this.receiver = receiver;
        this.timestamp = timestamp;
        this.isHere = isHere;
    }

    public boolean isPending() {
        return System.currentTimeMillis() - timestamp < (EssentialsOptions.tpaTimeout.get() * 1000);
    }
}
