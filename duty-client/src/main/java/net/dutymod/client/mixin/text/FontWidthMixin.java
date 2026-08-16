package net.dutymod.client.mixin.text;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.dutymod.client.text.TextWidthCache;
import net.dutymod.client.text.TextWidths;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Serves {@code Font.width(String)} from {@link TextWidthCache}.
 *
 * <p>The target is pinned by full descriptor because {@code width} has three overloads in 26.1.2 --
 * {@code String}, {@code FormattedText} and {@code FormattedCharSequence}. Only the {@code String}
 * form is cacheable: the other two carry style and component structure that a plain string key
 * cannot represent. Naming the method without its descriptor would let mixin pick whichever it
 * resolved first, which is how the {@code tooltip} overload mismatch took down Liteminer.
 *
 * <p>Idea from Sodium-Relief (MIT); the cache and this wrapper are Duty's.
 */
@Mixin(Font.class)
public abstract class FontWidthMixin {
    @WrapMethod(method = "width(Ljava/lang/String;)I")
    private int duty$cacheStringWidth(String text, Operation<Integer> original) {
        if (text == null || text.isEmpty()) {
            return original.call(text);
        }
        TextWidthCache cache = TextWidths.cache();
        int cached = cache.get(text);
        if (cached >= 0) {
            return cached;
        }
        int width = original.call(text);
        cache.put(text, width);
        return width;
    }
}
