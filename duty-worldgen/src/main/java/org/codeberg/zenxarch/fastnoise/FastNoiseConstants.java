package org.codeberg.zenxarch.fastnoise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FastNoiseConstants {
  private FastNoiseConstants() {
    throw new IllegalStateException("Utility class");
  }

  // This logger is used to write text to the console and the log file.
  // It is considered best practice to use your mod id as the logger's name.
  // That way, it's clear which mod wrote info, warnings, and errors.
  public static final Logger LOGGER = LoggerFactory.getLogger(FastNoiseConstants.MOD_ID);

  // duty_worldgen, not "zfastnoise": NeoForge refuses to load a jar whose @Mod entrypoint names
  // an id its neoforge.mods.toml does not declare. Duty: Innovative shipped that exact mismatch
  // and failed at load, so the constant and the metadata are kept in step here.
  public static final String MOD_ID = "duty_worldgen";
}
