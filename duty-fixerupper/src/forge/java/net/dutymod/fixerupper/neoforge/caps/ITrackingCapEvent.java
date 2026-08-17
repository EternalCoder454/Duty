package net.dutymod.fixerupper.neoforge.caps;

import net.neoforged.neoforge.capabilities.BaseCapability;

import java.util.Set;

public interface ITrackingCapEvent {
    Set<BaseCapability<?, ?>> duty$getTrackedCaps();
}
