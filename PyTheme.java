/** Colour themes for the editor. Order of each row:
 *  bg, text, keyword, builtin, string, number, comment, operator, def-name,
 *  line-number, current-line, bar, bar-text, cursor */
public class PyTheme {
    public static final String[] NAMES = {
        "Koyu", "A\u00e7\u0131k", "Monokai", "Okyanus", "Terminal", "G\u00fcn bat\u0131m\u0131", "Y\u00fcksek kontrast"
    };

    private static final int[][] C = {
        {0x1E1E1E, 0xD4D4D4, 0x569CD6, 0x4EC9B0, 0xCE9178, 0xB5CEA8, 0x6A9955, 0xD4D4D4, 0xDCDCAA,
            0x858585, 0x2A2D2E, 0x007ACC, 0xFFFFFF, 0xFFFFFF},
        {0xFFFFFF, 0x000000, 0x0000FF, 0x795E26, 0xA31515, 0x098658, 0x008000, 0x000000, 0x795E26,
            0x999999, 0xEEEEEE, 0x0066CC, 0xFFFFFF, 0x000000},
        {0x272822, 0xF8F8F2, 0xF92672, 0x66D9EF, 0xE6DB74, 0xAE81FF, 0x75715E, 0xF92672, 0xA6E22E,
            0x75715E, 0x3E3D32, 0x49483E, 0xF8F8F2, 0xF8F8F0},
        {0x002B36, 0x839496, 0x859900, 0x268BD2, 0x2AA198, 0xD33682, 0x586E75, 0x93A1A1, 0xB58900,
            0x586E75, 0x073642, 0x073642, 0x93A1A1, 0x93A1A1},
        {0x000000, 0x00CC00, 0x00FF66, 0x66FFFF, 0xFFFF66, 0xFF9966, 0x007700, 0x00CC00, 0x00FFCC,
            0x006600, 0x0A1A0A, 0x003300, 0x00FF00, 0x00FF00},
        {0x2D1B33, 0xF5E6CC, 0xFF7B54, 0xFFB26B, 0xFFD93D, 0x6BCB77, 0x8D7B91, 0xFF9A8B, 0x4D96FF,
            0x8D7B91, 0x3E2647, 0x5C2A5E, 0xFFF1E0, 0xFFD93D},
        {0x000000, 0xFFFFFF, 0xFFFF00, 0x00FFFF, 0x00FF00, 0xFF8800, 0x999999, 0xFFFFFF, 0xFF66FF,
            0xAAAAAA, 0x222222, 0x0000AA, 0xFFFFFF, 0xFFFFFF}
    };

    public static final int BG = 0, TEXT = 1, LNUM = 9, CURLINE = 10, BAR = 11, BARTEXT = 12, CURSOR = 13;

    public static int[] get(int i) {
        if (i < 0 || i >= C.length) {
            i = 0;
        }
        return C[i];
    }

    /** colour for a PySyntax class */
    public static int color(int[] t, byte cls) {
        // N, KW, BI, STR, NUM, COM, OP, DEF -> columns 1,2,3,4,5,6,7,8
        return t[1 + cls];
    }
}
