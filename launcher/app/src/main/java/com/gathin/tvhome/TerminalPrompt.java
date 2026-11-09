package com.gathin.tvhome;

/**
 * The dock title read as a shell prompt: a theme's `terminal_typing` types the app's name out one character
 * at a time and `terminal_prompt` keeps a blinking block cursor behind it. Both are off until a theme pushes
 * them, and neither spends anything on the reduced-motion path, where the name lands whole and the cursor
 * stays lit with nothing scheduled.
 */
final class TerminalPrompt {
    /** The block cursor drawn behind the label. A leading space keeps it clear of the last character. */
    static final String CURSOR = " █";
    /** The focus marker in front of a line of text: the focused row, and only it, is prefixed with it. */
    static final String PROMPT = "> ";
    /** The terminal text colour when the theme names none: phosphor red, the colour of a hacker terminal. */
    static final int DEFAULT_COLOR = 0xFFFF3B30;
    /** The user the prompt runs as. */
    static final String USER = "root";
    /** The host and path the prompt names when the theme's terminal_host and terminal_path say nothing. */
    static final String DEFAULT_HOST = "hacker-kali", DEFAULT_PATH = "/home/";

    private TerminalPrompt() {}

    /** True when the label is typed out character by character; with reduced motion it lands whole. */
    static boolean types(String label, boolean typing, boolean reducedMotion) {
        return typing && !reducedMotion && label != null && label.length() > 1;
    }

    /**
     * The text the shelf shows: the label typed out this far, with the block cursor behind it when prompt
     * is on. A prompt that is off draws the label alone, exactly as the plain shelf does.
     */
    static String text(String label, int typed, boolean prompt, boolean cursorOn) {
        if (label == null) label = "";
        int shown = Math.max(0, Math.min(typed, label.length()));
        StringBuilder out = new StringBuilder(label.length() + CURSOR.length()).append(label, 0, shown);
        if (prompt) out.append(cursorOn ? CURSOR : " ");
        return out.toString();
    }

    /**
     * A line of a text-only Home: the app's name, with the focus marker in front of the focused row. The
     * marker rather than the colour carries the focus, so it survives a TV that flattens the greens.
     */
    static String row(String label, boolean focused) {
        if (label == null) label = "";
        return focused ? PROMPT + label : label;
    }

    /**
     * The prompt's context line, the long one above the command: {@code ┌─(root@host)-[path]}. Box-drawing
     * characters are the classic look, and a TV whose mono font lacks them still reads {@code +-} and the
     * text, so nothing is lost either way.
     */
    static String context(String host, String path) {
        if (host == null || host.isEmpty()) host = DEFAULT_HOST;
        if (path == null || path.isEmpty()) path = DEFAULT_PATH;
        return "\u250c\u2500(" + USER + "@" + host + ")-[" + path + "]";
    }

    /**
     * The prompt's command line under it: {@code └─# youtube}, typed out character by character with the
     * block cursor behind it while the prompt is on. Only the label is typed: {@code └─# } is written at
     * once, the way a shell shows what it was asked before the answer.
     */
    static String command(String label, int typed, boolean prompt, boolean cursorOn) {
        StringBuilder out = new StringBuilder(COMMAND).append(text(label, typed, false, false));
        return prompt ? out.append(cursorOn ? CURSOR : " ").toString() : out.toString();
    }

    /** The command line's fixed part: {@code └─# }. */
    static final String COMMAND = "\u2514\u2500# ";

    /**
     * The same colour dimmed for the rows that are not focused. The focus marker carries the focus, and this
     * is what makes the lit row stand out from the rest at three metres; alpha alone would let the black
     * background through and pale the phosphor.
     */
    static int dim(int argb) {
        int red = ((argb >> 16) & 0xFF) * 55 / 100;
        int green = ((argb >> 8) & 0xFF) * 55 / 100;
        int blue = (argb & 0xFF) * 55 / 100;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
