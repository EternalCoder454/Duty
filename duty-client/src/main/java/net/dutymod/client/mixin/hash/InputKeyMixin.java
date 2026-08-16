package net.dutymod.client.mixin.hash;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Caches {@link InputConstants.Key}'s hash.
 *
 * <p>Keys are map keys in the keybinding lookup, which is consulted on every key and mouse event
 * and, with a large modpack's keybind list, for every registered binding during conflict checks.
 * The class is final and both hashed fields are final, so the value is fixed at construction.
 *
 * <p>Same zero-as-sentinel scheme as {@link VertexFormatMixin}. Adapted from Lomka (MIT).
 */
@Mixin(InputConstants.Key.class)
public abstract class InputKeyMixin {
    @Shadow
    @Final
    private InputConstants.Type type;

    @Shadow
    @Final
    private int value;

    @Unique
    private int duty$hashCode;

    /**
     * @author Duty (from Lomka by Starlevka, MIT)
     * @reason Cache the hash; the class and the fields it is derived from are final.
     */
    @Overwrite
    public int hashCode() {
        int hash = this.duty$hashCode;
        if (hash == 0) {
            hash = 31 * (31 + this.type.hashCode()) + this.value;
            if (hash == 0) {
                hash = 1;
            }
            this.duty$hashCode = hash;
        }
        return hash;
    }
}
