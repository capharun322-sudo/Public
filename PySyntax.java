/** Splits a line of Python into colour classes (pure Java, no MIDP). */
public class PySyntax {
    public static final byte N = 0, KW = 1, BI = 2, STR = 3, NUM = 4, COM = 5, OP = 6, DEF = 7;

    private static final String KWS = "|and|as|break|continue|def|elif|else|for|global|if|in|is|not|or|pass|return|while|"
            + "True|False|None|import|from|class|try|except|finally|with|lambda|del|";
    private static final String BIS = "|print|input|int|str|len|range|abs|min|max|sum|list|chr|ord|bool|"
            + "append|pop|insert|sort|reverse|remove|index|upper|lower|strip|replace|find|startswith|endswith|"
            + "isdigit|split|join|count|";

    private static boolean isAlpha(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c > 127;
    }

    private static boolean isDig(char c) {
        return c >= '0' && c <= '9';
    }

    private static int string(String s, int i, byte[] c) {
        int n = s.length();
        char q = s.charAt(i);
        int j = i + 1;
        while (j < n && s.charAt(j) != q) {
            if (s.charAt(j) == '\\') {
                j++;
            }
            j++;
        }
        j = j < n ? j + 1 : n;
        for (int k = i; k < j; k++) {
            c[k] = STR;
        }
        return j;
    }

    public static byte[] classify(String s) {
        int n = s.length();
        byte[] c = new byte[n];
        String prev = "";
        int i = 0;
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '#') {
                for (int k = i; k < n; k++) {
                    c[k] = COM;
                }
                break;
            }
            if (ch == '"' || ch == '\'') {
                i = string(s, i, c);
                prev = "";
                continue;
            }
            if (isDig(ch)) {
                int j = i;
                while (j < n && isDig(s.charAt(j))) {
                    c[j] = NUM;
                    j++;
                }
                i = j;
                prev = "";
                continue;
            }
            if (isAlpha(ch)) {
                int j = i;
                while (j < n && (isAlpha(s.charAt(j)) || isDig(s.charAt(j)))) {
                    j++;
                }
                String w = s.substring(i, j);
                if (j < n && (s.charAt(j) == '"' || s.charAt(j) == '\'')
                        && (w.equals("f") || w.equals("F"))) {
                    c[i] = STR;
                    i = string(s, j, c);
                    prev = "";
                    continue;
                }
                byte k = N;
                if (prev.equals("def")) {
                    k = DEF;
                } else if (KWS.indexOf("|" + w + "|") >= 0) {
                    k = KW;
                } else if (BIS.indexOf("|" + w + "|") >= 0) {
                    k = BI;
                }
                for (int x = i; x < j; x++) {
                    c[x] = k;
                }
                prev = w;
                i = j;
                continue;
            }
            if (ch == ' ' || ch == '\t') {
                i++;
                continue;
            }
            if ("+-*/%=<>!()[]{},:.".indexOf(ch) >= 0) {
                c[i] = OP;
            }
            prev = "";
            i++;
        }
        return c;
    }
}
