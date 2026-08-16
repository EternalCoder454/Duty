package net.dutymod.client.mixin.hash;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;

/**
 * Caches {@link VertexFormat}'s hash.
 *
 * <p>Vertex formats are used as map keys throughout the render state, so {@code hashCode} runs
 * often, and vanilla recomputes {@link Arrays#hashCode(int[])} over {@code offsetsByElement} every
 * single time. Both fields it hashes are final, so the answer cannot change once computed.
 *
 * <p>Zero is used as the "not computed yet" marker, and a genuine hash of zero is stored as one.
 * That biases exactly one hash value onto a neighbouring bucket and costs nothing, where a separate
 * boolean flag would add a field and a second read.
 *
 * <p>Safe alongside Iris, which is the only other installed mod that patches this class: its
 * {@code MixinVertexFormat} adds attribute binding and touches neither {@code hashCode} nor either
 * shadowed field. Adapted from Lomka (MIT).
 */
@Mixin(VertexFormat.class)
public abstract class VertexFormatMixin {
    @Shadow
    @Final
    private int elementsMask;

    @Shadow
    @Final
    private int[] offsetsByElement;

    @Unique
    private int duty$hashCode;

    /**
     * @author Duty (from Lomka by Starlevka, MIT)
     * @reason Cache the hash; the fields it is derived from are final.
     */
    @Overwrite
    public int hashCode() {
        int hash = this.duty$hashCode;
        if (hash == 0) {
            hash = this.elementsMask * 31 + Arrays.hashCode(this.offsetsByElement);
            if (hash == 0) {
                hash = 1;
            }
            this.duty$hashCode = hash;
        }
        return hash;
    }
}
