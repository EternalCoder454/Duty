package com.axalotl.async.common;

import com.axalotl.async.common.platform.PlatformUtils;

public abstract class AsyncCommon {
    // duty_innovative, not "async": this ships as a Duty module and the id has to match
    // the one in neoforge.mods.toml. NeoForge refuses to load a jar whose @Mod entrypoint
    // names an id the file does not declare, which is exactly how this was found.
    public static final String MODID = "duty_innovative";

    public final void initialize() {
        PlatformUtils.initialize();
    }
}