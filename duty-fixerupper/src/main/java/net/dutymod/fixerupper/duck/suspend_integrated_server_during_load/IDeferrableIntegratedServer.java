package net.dutymod.fixerupper.duck.suspend_integrated_server_during_load;

import net.minecraft.resources.Identifier;
import net.dutymod.fixerupper.FixerUpper;

public interface IDeferrableIntegratedServer {
    Identifier CLIENT_LOAD_SENTINEL = Identifier.fromNamespaceAndPath(FixerUpper.MODID, "mark_client_load_finished");

    void duty$markClientLoadFinished();
}
