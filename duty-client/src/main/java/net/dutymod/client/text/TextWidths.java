package net.dutymod.client.text;

/**
 * Holds the one {@link TextWidthCache}.
 *
 * <p>A separate holder rather than a static field on the mixin: a {@code @Unique} static on a mixin
 * lives on the target class after application, which would put Duty's cache inside {@code Font} and
 * make its lifetime that of the font rather than the game.
 *
 * <p>Initialised eagerly. The cache is reached from {@code Font.width}, which is called during the
 * loading screen, so a lazy holder would have to be thread-safe for no benefit -- the object is a
 * map and two ints.
 */
public final class TextWidths {
    private static final TextWidthCache CACHE = new TextWidthCache();

    private TextWidths() {}

    public static TextWidthCache cache() {
        return CACHE;
    }
}
