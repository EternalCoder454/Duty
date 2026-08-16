package net.dutymod.fixerupper.duck.suspend_integrated_server_during_load;

import net.minecraft.resources.Identifier;
import net.dutymod.fixerupper.ModernFix;

public interface IDeferrableIntegratedServer {
    Identifier CLIENT_LOAD_SENTINEL = Identifier.fromNamespaceAndPath(ModernFix.MODID, "mark_client_load_finished");

    void mfix$markClientLoadFinished();
}
