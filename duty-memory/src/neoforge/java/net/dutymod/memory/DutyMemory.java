package net.dutymod.memory;

import net.dutymod.framework.DutyConfig;
import net.dutymod.framework.DutyLog;
import net.dutymod.framework.screen.DutyConfigScreens;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Duty: Memory.
 *
 * <p>Two independent bodies of work live here. The mixin-driven half (from FerriteCore) attacks
 * retained heap: block states hold most of their memory in neighbour-lookup tables and property
 * maps that are overwhelmingly duplicates, so they get deduplicated into shared instances. The
 * transformer half (from Jasione) attacks allocation rate: {@code Enum.values()} clones its backing
 * array on every single call, and the vast majority of callers only read it.
 *
 * <p>The transformer does not run from here. It is installed as a
 * {@code net.neoforged.neoforgespi.transformation.ClassProcessor} service and is already active by
 * the time this class exists; see {@link net.dutymod.memory.enums.EnumValuesProcessor}.
 */
@Mod(DutyMemory.MOD_ID)
public class DutyMemory {
    public static final String MOD_ID = "duty_memory";

    public DutyMemory(ModContainer container) {
        DutyLog.info("Duty: Memory reporting for duty.");
        DutyConfigScreens.register(container);
        if (!DutyConfig.get(MemoryOptions.BLOCK_STATE_DEDUPLICATION)) {
            DutyLog.info("Block state deduplication is disabled in config.");
        }
    }
}
