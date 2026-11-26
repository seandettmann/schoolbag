package io.github.seandettmann.schoolbag.core;

/**
 * The game's lettering, as TrueType versions of its bitmap fonts (in the art
 * bundle). Each font's ascent, as a share of its size, places text the way
 * the mock-ups do: for these fonts the line's content area is exactly the
 * font size.
 */
public enum Face {
    /** Team Meat 20: titles and names. */
    TITLE("fonts/isaac-teammeat20.ttf", 1400f / 1600f),
    /** Team Meat 16: the smaller hand lettering and the tabs. */
    SMALL("fonts/isaac-teammeat16.ttf", 1100f / 1250f),
    /** Tempesta: the game's numbers. */
    NUMBER("fonts/isaac-tempesta.ttf", 550f / 650f),
    /** The system's font, for languages the game's fonts don't cover. */
    SYSTEM(null, 0.88f);

    public final String file;
    public final float ascent;

    Face(String file, float ascent) {
        this.file = file;
        this.ascent = ascent;
    }

    /** The baseline for text set in a line box of the given height whose top is at top. */
    public float baseline(float top, float size, float lineHeight) {
        return top + (lineHeight - size) / 2f + ascent * size;
    }
}
