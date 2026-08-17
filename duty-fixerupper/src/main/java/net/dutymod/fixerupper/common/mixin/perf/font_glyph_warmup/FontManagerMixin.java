package net.dutymod.fixerupper.common.mixin.perf.font_glyph_warmup;

import com.mojang.blaze3d.font.GlyphProvider;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Warms each glyph only on the provider that will actually serve it.
 *
 * <h2>What vanilla does</h2>
 *
 * <p>{@code finalizeProviderLoading} unions every provider's supported glyphs into one set and
 * then, for each codepoint in that union, walks <em>every</em> provider calling {@code getGlyph}
 * and throwing the answer away. The call is a warm-up: it exists to make providers build their
 * glyphs now rather than during the first frame that needs them.
 *
 * <p>That is providers x codepoints calls. The unicode font alone supplies tens of thousands of
 * codepoints, and a resource pack stack has several providers, so the multiplication is the cost.
 *
 * <h2>What this does instead</h2>
 *
 * <p>Walk the providers, and for each one only visit the codepoints it actually claims to support,
 * skipping any that an earlier provider already answered for. Every codepoint still gets warmed;
 * it just gets warmed once instead of once per provider.
 *
 * <h2>Why replacing it is safe here</h2>
 *
 * <p>Because the results are discarded. The method returns {@code void} and every {@code getGlyph}
 * return value is dropped on the floor, so the only thing the loop produces is warmed caches --
 * warming a provider that will never be asked for that codepoint is work with no observable
 * result. The one real side effect is inserting the fallback at index 0, which is preserved below
 * as the first thing this does.
 *
 * <p>Its sibling {@code FontSet.selectProviders} has a similar shape and is deliberately left
 * alone: that one computes {@code glyphsByWidth} and returns the provider list the font actually
 * looks up through, so the same rewrite there changes state rather than just cache warmth.
 *
 * <p>An {@link Inject} that cancels rather than an {@code @Overwrite}: two mods can inject into one
 * method and be ordered, where two overwrites are a hard conflict.
 */
@Mixin(FontManager.class)
@ClientOnlyMixin
public class FontManagerMixin {

    @Inject(method = "finalizeProviderLoading", at = @At("HEAD"), cancellable = true)
    private void duty$warmGlyphsOnce(List<GlyphProvider.Conditional> providers,
                                     GlyphProvider.Conditional fallback, CallbackInfo ci) {
        // The list mutation vanilla performs. Not an optimisation and not optional -- the fallback
        // has to be in the list for the providers below, and for everything downstream of them.
        providers.add(0, fallback);

        IntSet claimed = new IntOpenHashSet();
        // Last to first, matching the order vanilla warms in (it wraps the list in Lists.reverse).
        // Which provider ends up warm for a given codepoint is not observable -- see above -- but
        // following vanilla's order means the cache that gets filled is the one vanilla filled last
        // and therefore the one most likely to be asked.
        for (int i = providers.size() - 1; i >= 0; i--) {
            GlyphProvider provider = providers.get(i).provider();
            for (int codepoint : provider.getSupportedGlyphs()) {
                if (!claimed.contains(codepoint) && provider.getGlyph(codepoint) != null) {
                    claimed.add(codepoint);
                }
            }
        }

        ci.cancel();
    }
}
