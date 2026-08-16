package net.dutymod.client.text;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;

/**
 * Memoizes {@code Font.width(String)}.
 *
 * <p>Every visible inventory slot draws its stack count every frame, and measuring "64" costs a
 * walk over the string's glyphs each time. A full inventory screen is dozens of those per frame,
 * over a working set of a few dozen distinct strings.
 *
 * <p>A string's width is deterministic for the current font, so this is only correct while the font
 * cannot change underneath it. It can: a resource pack swap, a language change, or toggling forced
 * unicode all re-derive glyph metrics. {@link #clear()} is wired into the client resource reload in
 * {@code DutyClient}, which is the one event covering all three.
 *
 * <p>Two deliberate differences from the mod this idea came from (Sodium-Relief, MIT):
 *
 * <ul>
 *   <li>fastutil's primitive map rather than {@code LinkedHashMap<String, Integer>}. Widths are
 *       ints; a boxed map would allocate an {@link Integer} per distinct string and box again on
 *       every hit above the small-value cache.
 *   <li>No synchronization. Upstream synchronizes every read; {@code Font.width} is a render-thread
 *       call, and taking a monitor per measurement to guard against a caller that does not exist
 *       costs more than the measurement saved. {@link #clear()} runs on the same thread, during
 *       reload.
 * </ul>
 *
 * <p>Insertion-ordered rather than access-ordered, for upstream's stated reason: the working set is
 * far below the cap so eviction effectively never fires, and access ordering would turn every read
 * into a structural write to maintain a policy that never triggers.
 */
public final class TextWidthCache {
    /** Comfortably above the distinct-string count of a full inventory, small enough to be free. */
    private static final int MAX_ENTRIES = 512;

    private static final int ABSENT = -1;

    private final Object2IntLinkedOpenHashMap<String> widths = new Object2IntLinkedOpenHashMap<>(256, 0.75F);

    public TextWidthCache() {
        widths.defaultReturnValue(ABSENT);
    }

    /** {@return the cached width of {@code text}, or {@code -1} if it has not been measured} */
    public int get(String text) {
        return widths.getInt(text);
    }

    public void put(String text, int width) {
        // A measured width is never negative, so a negative value would collide with the
        // absent marker and be re-measured forever. Nothing should produce one; skip if it does.
        if (width < 0) {
            return;
        }
        if (widths.size() >= MAX_ENTRIES) {
            widths.removeFirstInt();
        }
        widths.put(text, width);
    }

    /** Drops every entry. Must run whenever glyph metrics can have changed. */
    public void clear() {
        if (!widths.isEmpty()) {
            widths.clear();
        }
    }
}
