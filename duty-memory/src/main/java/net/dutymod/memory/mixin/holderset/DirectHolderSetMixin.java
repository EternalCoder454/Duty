package net.dutymod.memory.mixin.holderset;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Set;

/**
 * Stops small direct holder sets from retaining a {@link Set}.
 *
 * <p>A {@code HolderSet.Direct} is the backing store for a tag or any other fixed list of registry
 * entries. Vanilla's {@code contains} lazily materialises {@code contentsSet} via {@code
 * Set.copyOf} on first call and then keeps it forever -- confirmed against the 26.1.2 bytecode,
 * which contains exactly one {@code Set.copyOf} in that method.
 *
 * <p>That trade is right for a large set and wrong for a small one. A modded registry has thousands
 * of these and the overwhelming majority hold a handful of entries, where the retained
 * {@code ImmutableSet} and its table cost far more than the membership test saves. Below the
 * threshold this scans the list instead, so {@code contentsSet} stays null and is never allocated.
 *
 * <p>Purely a memory change: the answer is identical either way, since {@code List.contains} and
 * {@code Set.contains} both compare with {@code equals}. Larger sets keep the lazy cache exactly as
 * before, so the sets where hashing actually pays are untouched.
 *
 * <p>Adapted from Lomka (MIT). Nothing else installed patches {@code HolderSet}.
 */
@Mixin(targets = "net.minecraft.core.HolderSet$Direct")
public abstract class DirectHolderSetMixin<T> {
    /**
     * Where a linear scan stops beating a hash lookup.
     *
     * <p>Four is upstream's figure and is deliberately conservative: at that size the scan is a few
     * reference comparisons against a cache-resident array, while the set costs an allocation, a
     * hash per lookup, and permanent retention. Raising it would trade more CPU for more saved
     * memory; the sets large enough for that to matter are rare enough not to.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final int DUTY_LINEAR_SCAN_LIMIT = 4;

    @Shadow
    @Final
    private List<Holder<T>> contents;

    @Shadow
    private Set<Holder<T>> contentsSet;

    /**
     * @author Duty (from Lomka by Starlevka, MIT)
     * @reason Do not retain a Set for a handful of entries.
     */
    @Overwrite
    public boolean contains(Holder<T> holder) {
        List<Holder<T>> entries = this.contents;
        if (entries.size() <= DUTY_LINEAR_SCAN_LIMIT) {
            return entries.contains(holder);
        }
        Set<Holder<T>> set = this.contentsSet;
        if (set == null) {
            set = Set.copyOf(entries);
            this.contentsSet = set;
        }
        return set.contains(holder);
    }
}
