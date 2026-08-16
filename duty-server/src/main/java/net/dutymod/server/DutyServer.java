package net.dutymod.server;

import net.dutymod.core.DutyLog;
import net.dutymod.core.screen.DutyConfigScreens;
import net.dutymod.server.net.NetOptions;
import net.dutymod.server.wire.AlternateCurrent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Duty: Server's entry point.
 *
 * <p>This module ran without one until now. Its mixin config plugins register their own options
 * from {@code onLoad}, so nothing was broken -- but the plugins only run when their mixins are
 * being applied, which made this the one installed module with no entry in the settings screen.
 *
 * <p>Registering the options here as well is deliberate rather than redundant. It means the keys
 * exist in {@code duty.properties}, and are listed in the screen, whether or not the mixins that
 * consume them were applied. An option that vanishes from the config when its feature is switched
 * off cannot be switched back on from the screen.
 */
@Mod(DutyServer.MOD_ID)
public final class DutyServer {
    public static final String MOD_ID = "duty_server";

    public DutyServer(ModContainer container) {
        NetOptions.init();
        AlternateCurrent.init();
        DutyConfigScreens.register(container);
        DutyLog.info("Duty: Server reporting for duty.");
    }
}
