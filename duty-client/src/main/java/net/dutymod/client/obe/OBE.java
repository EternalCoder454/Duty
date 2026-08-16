package net.dutymod.client.obe;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Shared constants for the baked block entity work.
 *
 * <p>Upstream this was a {@code @Mod} entrypoint. Here it is a plain holder: Duty: Client is one
 * mod, so a second {@code @Mod} class would register a second mod id. Startup now happens from
 * {@link net.dutymod.client.DutyClient}.
 *
 * <p>The logger stays SLF4J rather than moving to {@code DutyLog} only because the registry classes
 * below call it from paths that other mods trigger, and matching the message shape upstream used
 * keeps those reports recognisable when someone searches for them.
 */
public final class OBE {
    /** Duty's own id, so anything keyed on it lands in Duty's namespace rather than "obe". */
    public static final String MOD_ID = "duty_client";

    public static final Logger LOGGER = LogUtils.getLogger();

    private OBE() {}
}
