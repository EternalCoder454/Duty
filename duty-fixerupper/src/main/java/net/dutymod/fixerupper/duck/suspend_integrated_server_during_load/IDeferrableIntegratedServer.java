package net.dutymod.fixerupper.duck.suspend_integrated_server_during_load;

import net.minecraft.resources.ResourceLocation;
import net.dutymod.fixerupper.FixerUpper;

public interface IDeferrableIntegratedServer {
    ResourceLocation CLIENT_LOAD_SENTINEL = ResourceLocation.fromNamespaceAndPath(FixerUpper.MODID, "mark_client_load_finished");

    void duty$markClientLoadFinished();
}
