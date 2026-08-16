package net.dutymod.fixerupper.chunk;

import net.minecraft.world.level.chunk.Palette;

public interface ExtendedPalettedContainer<T> {
    Palette<T> mfix$getPalette();
}
