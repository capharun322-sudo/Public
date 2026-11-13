import java.util.Hashtable;
import java.util.Vector;

/**
 * Tiny Python-subset interpreter for CLDC 1.0 / MIDP 2.0 phones.
 * Supports: ints, strings, f-strings, True/False/None, variables,
 * + - * // / % ** and/or/not, comparisons, in, indexing, string methods,
 * print/input/int/str/len/abs/min/max, if/elif/else, while, for..range,
 * for over a string, break, continue, pass.
 * No generics / StringBuilder / other post-CLDC APIs are used on purpose.
 */
public class PyInterp {
    static final int NUM = 0, STR = 1, NAME = 2, OP = 3;
    static final Object NONE = new Object();
    static final Boolean T = new Boolean(true);
    static final Boolean F = new Boolean(false);
    static final int MAX_STR = 20000;

    static class Tok {
        int type;
        String s;
        long n;
        boolean f;

        Tok(int type, String s, long n) {
            this.type = type;
            this.s = s;
            this.n = n;
        }

        boolean isOp(String o) {
            return type == OP && s.equals(o);
        }

        boolean isName(String o) {
            return type == NAME && s.equals(o);
        }
    }

    private PyIO io;
    public volatile boolean stopped = false;

    private Vector lines = new Vector();
    private int[] indent;
    private int[] lineNo;
    private Hashtable vars = new Hashtable();
    private int flow = 0; // 1 = break, 2 = continue
    private int cur = 0; // current source line (for messages)

    // expression parser state
    private Tok[] tk;
    private int p, end, skip;
    private Hashtable lastKw;

    public PyInterp(PyIO io) {
        this.io = io;
    }

    // ------------------------------------------------------------ errors

    private PyError err(String m) {
        return new PyError(cur, m);
    }

    private PyError syntax() {
        return new PyError(cur, "SyntaxError: invalid syntax");
    }

    // ------------------------------------------------------------ run

    public void run(String src) throws PyError {
        Vector raw = new Vector();
        int st = 0, n = src.length();
        for (int i = 0; i <= n; i++) {
            if (i == n || src.charAt(i) == '\n') {
                raw.addElement(src.substring(st, i));
                st = i + 1;
            }
        }
        int cnt = raw.size();
        int[] ind = new int[cnt];
        int[] ln = new int[cnt];
        int m = 0;
        for (int i = 0; i < cnt; i++) {
            String s = (String) raw.elementAt(i);
            int w = 0, j = 0;
            while (j < s.length()) {
                char c = s.charAt(j);
                if (c == ' ') {
                    w++;
                } else if (c == '\t') {
                    w += 4;
                } else if (c != '\r') {
                    break;
                }
                j++;
            }
            cur = i + 1;
            Tok[] t = tokenize(s.substring(j));
            if (t.length == 0) {
                continue;
            }
            lines.addElement(t);
            ind[m] = w;
            ln[m] = i + 1;
            m++;
        }
        indent = ind;
        lineNo = ln;
        if (m == 0) {
            return;
        }
        if (indent[0] != 0) {
            cur = lineNo[0];
            throw err("IndentationError: unexpected indent");
        }
        execBlock(0, 0);
        if (flow != 0) {
            throw err("SyntaxError: 'break' or 'continue' outside loop");
        }
    }

    // ------------------------------------------------------------ tokenizer

    private static boolean isAlpha(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c > 127;
    }

    private static boolean isDig(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIn(String set, String x) {
        return ("|" + set + "|").indexOf("|" + x + "|") >= 0;
    }

    private Tok[] tokenize(String s) throws PyError {
        Vector v = new Vector();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r') {
                i++;
                continue;
            }
            if (c == '#') {
                break;
            }
            if (isDig(c)) {
                long val = 0;
                int j = i;
                while (j < n && isDig(s.charAt(j))) {
                    val = val * 10 + (s.charAt(j) - '0');
                    j++;
                }
                if (j < n && s.charAt(j) == '.') {
                    throw err("SyntaxError: decimal numbers are not supported");
                }
                v.addElement(new Tok(NUM, null, val));
                i = j;
                continue;
            }
            if (isAlpha(c)) {
                int j = i;
                while (j < n && (isAlpha(s.charAt(j)) || isDig(s.charAt(j)))) {
                    j++;
                }
                String w = s.substring(i, j);
                if (j < n && (s.charAt(j) == '"' || s.charAt(j) == '\'')
                        && (w.equals("f") || w.equals("F"))) {
                    i = readString(s, j, true, v);
                    continue;
                }
                v.addElement(new Tok(NAME, w, 0));
                i = j;
                continue;
            }
            if (c == '"' || c == '\'') {
                i = readString(s, i, false, v);
                continue;
            }
            String two = i + 1 < n ? s.substring(i, i + 2) : "";
            String three = i + 2 < n ? s.substring(i, i + 3) : "";
            if (three.equals("//=")) {
                v.addElement(new Tok(OP, three, 0));
                i += 3;
                continue;
            }
            if (two.length() == 2 && isIn("==|!=|<=|>=|//|**|+=|-=|*=|%=", two)) {
                v.addElement(new Tok(OP, two, 0));
                i += 2;
                continue;
            }
            if ("+-*/%(),:=<>[].".indexOf(c) >= 0) {
                v.addElement(new Tok(OP, s.substring(i, i + 1), 0));
                i++;
                continue;
            }
            throw err("SyntaxError: invalid character '" + c + "'");
        }
        Tok[] r = new Tok[v.size()];
        v.copyInto(r);
        return r;
    }

    private int readString(String s, int i, boolean f, Vector v) throws PyError {
        char q = s.charAt(i);
        int n = s.length();
        int j = i + 1;
        StringBuffer sb = new StringBuffer();
        while (true) {
            if (j >= n) {
                throw err("SyntaxError: unterminated string literal");
            }
            char c = s.charAt(j);
            if (c == q) {
                break;
            }
            if (c == '\\' && j + 1 < n) {
                char d = s.charAt(j + 1);
                j += 2;
                if (d == 'n') {
                    sb.append('\n');
                } else if (d == 't') {
                    sb.append('\t');
                } else if (d == '\\') {
                    sb.append('\\');
                } else if (d == '\'') {
                    sb.append('\'');
                } else if (d == '"') {
                    sb.append('"');
                } else {
                    sb.append('\\');
                    sb.append(d);
                }
                continue;
            }
            sb.append(c);
            j++;
        }
        Tok t = new Tok(STR, sb.toString(), 0);
        t.f = f;
        v.addElement(t);
        return j + 1;
    }

    // ------------------------------------------------------------ statements

    private int blockEnd(int i, int ind) {
        int n = lines.size();
        int e = i + 1;
        while (e < n && indent[e] > ind) {
            e++;
        }
        return e;
    }

    private int bodyIndent(int i, int ind) throws PyError {
        if (i + 1 >= lines.size() || indent[i + 1] <= ind) {
            throw err("IndentationError: expected an indented block");
        }
        return indent[i + 1];
    }

    private void needColon(Tok[] t) throws PyError {
        if (!t[t.length - 1].isOp(":")) {
            throw err("SyntaxError: expected ':' at end (put the block on the next lines)");
        }
    }

    private void runBlock(int i, int bi, int e) throws PyError {
        int r = execBlock(i + 1, bi);
        if (r != e) {
            throw new PyError(lineNo[r < lines.size() ? r : lines.size() - 1],
                    "IndentationError: unindent does not match any outer indentation level");
        }
    }

    /** Runs a loop body once. Returns true if the loop must stop (break). */
    private boolean loopBody(int i, int bi, int e) throws PyError {
        runBlock(i, bi, e);
        if (flow == 1) {
            flow = 0;
            return true;
        }
        if (flow == 2) {
            flow = 0;
        }
        return false;
    }

    private int execBlock(int i, int ind) throws PyError {
        int n = lines.size();
        while (i < n) {
            if (stopped) {
                throw new PyError(0, "Stopped");
            }
            int ci = indent[i];
            if (ci < ind) {
                return i;
            }
            cur = lineNo[i];
            if (ci > ind) {
                throw err("IndentationError: unexpected indent");
            }
            Tok[] t = (Tok[]) lines.elementAt(i);
            String kw = t[0].type == NAME ? t[0].s : "";
            if (kw.equals("if")) {
                i = execIf(i, ind);
            } else if (kw.equals("while")) {
                i = execWhile(i, ind);
            } else if (kw.equals("for")) {
                i = execFor(i, ind);
            } else if (kw.equals("break")) {
                flow = 1;
                i++;
            } else if (kw.equals("continue")) {
                flow = 2;
                i++;
            } else if (kw.equals("pass")) {
                i++;
            } else if (kw.equals("elif") || kw.equals("else")) {
                throw err("SyntaxError: '" + kw + "' without matching 'if'");
            } else {
                execSimple(t);
                i++;
            }
            if (flow != 0) {
                while (i < n && indent[i] >= ind) {
                    i++;
                }
                return i;
            }
        }
        return i;
    }

    private int execIf(int i, int ind) throws PyError {
        int n = lines.size();
        boolean done = false;
        while (true) {
            Tok[] t = (Tok[]) lines.elementAt(i);
            cur = lineNo[i];
            String kw = t[0].s;
            needColon(t);
            int bi = bodyIndent(i, ind);
            int e = blockEnd(i, ind);
            boolean take;
            if (kw.equals("else")) {
                if (t.length != 2) {
                    throw syntax();
                }
                take = !done;
            } else {
                take = !done && truthy(eval(t, 1, t.length - 1));
            }
            if (take) {
                done = true;
                runBlock(i, bi, e);
            }
            i = e;
            if (kw.equals("else") || flow != 0) {
                break;
            }
            if (i < n && indent[i] == ind) {
                Tok[] nt = (Tok[]) lines.elementAt(i);
                if (nt[0].isName("elif") || nt[0].isName("else")) {
                    continue;
                }
            }
            break;
        }
        return i;
    }

    private int execWhile(int i, int ind) throws PyError {
        Tok[] t = (Tok[]) lines.elementAt(i);
        needColon(t);
        int bi = bodyIndent(i, ind);
        int e = blockEnd(i, ind);
        while (true) {
            cur = lineNo[i];
            if (stopped) {
                throw new PyError(0, "Stopped");
            }
            if (!truthy(eval(t, 1, t.length - 1))) {
                break;
            }
            if (loopBody(i, bi, e)) {
                break;
            }
        }
        return e;
    }

    private int execFor(int i, int ind) throws PyError {
        Tok[] t = (Tok[]) lines.elementAt(i);
        needColon(t);
        if (t.length < 5 || t[1].type != NAME || !t[2].isName("in")) {
            throw syntax();
        }
        int bi = bodyIndent(i, ind);
        int e = blockEnd(i, ind);
        String var = t[1].s;
        if (t[3].isName("range") && t[4].isOp("(")) {
            Vector a = evalArgs(t, 4, t.length - 1);
            int c = a.size();
            if (c < 1 || c > 3) {
                throw err("TypeError: range expected 1 to 3 arguments");
            }
            long start = 0, stop, step = 1;
            if (c == 1) {
                stop = num(a.elementAt(0));
            } else {
                start = num(a.elementAt(0));
                stop = num(a.elementAt(1));
                if (c == 3) {
                    step = num(a.elementAt(2));
                }
            }
            if (step == 0) {
                throw err("ValueError: range() arg 3 must not be zero");
            }
            for (long k = start; step > 0 ? k < stop : k > stop; k += step) {
                vars.put(var, new Long(k));
                if (loopBody(i, bi, e)) {
                    break;
                }
            }
        } else {
            Object o = eval(t, 3, t.length - 1);
            if (!(o instanceof String)) {
                throw err("TypeError: object is not iterable (use range() or a string)");
            }
            String s = (String) o;
            for (int k = 0; k < s.length(); k++) {
                vars.put(var, s.substring(k, k + 1));
                if (loopBody(i, bi, e)) {
                    break;
                }
            }
        }
        return e;
    }

    private void execSimple(Tok[] t) throws PyError {
        if (t.length >= 2 && t[0].type == NAME && t[1].type == OP) {
            String op = t[1].s;
            String name = t[0].s;
            if (op.equals("=")) {
                vars.put(name, eval(t, 2, t.length));
                return;
            }
            if (isIn("+=|-=|*=|//=|%=", op)) {
                Object old = vars.get(name);
                if (old == null) {
                    throw err("NameError: name '" + name + "' is not defined");
                }
                Object r = eval(t, 2, t.length);
                vars.put(name, binop(op.substring(0, op.length() - 1), old, r));
                return;
            }
        }
        eval(t, 0, t.length);
    }

    // ------------------------------------------------------------ expressions

    private Object eval(Tok[] t, int from, int to) throws PyError {
        Tok[] st = tk;
        int sp = p, se = end, ss = skip;
        tk = t;
        p = from;
        end = to;
        skip = 0;
        try {
            if (from >= to) {
                throw syntax();
            }
            Object r = parseOr();
            if (p != end) {
                throw syntax();
            }
            return r;
        } finally {
            tk = st;
            p = sp;
            end = se;
            skip = ss;
        }
    }

    private Vector evalArgs(Tok[] t, int from, int to) throws PyError {
        Tok[] st = tk;
        int sp = p, se = end, ss = skip;
        tk = t;
        p = from;
        end = to;
        skip = 0;
        try {
            Vector a = parseArgs();
            if (p != end) {
                throw syntax();
            }
            return a;
        } finally {
            tk = st;
            p = sp;
            end = se;
            skip = ss;
        }
    }

    private void expectOp(String o) throws PyError {
        if (p >= end || !tk[p].isOp(o)) {
            throw syntax();
        }
        p++;
    }

    private Object parseOr() throws PyError {
        Object l = parseAnd();
        while (p < end && tk[p].isName("or")) {
            p++;
            if (skip > 0) {
                parseAnd();
            } else if (truthy(l)) {
                skip++;
                parseAnd();
                skip--;
            } else {
                l = parseAnd();
            }
        }
        return l;
    }

    private Object parseAnd() throws PyError {
        Object l = parseNot();
        while (p < end && tk[p].isName("and")) {
            p++;
            if (skip > 0) {
                parseNot();
            } else if (!truthy(l)) {
                skip++;
                parseNot();
                skip--;
            } else {
                l = parseNot();
            }
        }
        return l;
    }

    private Object parseNot() throws PyError {
        if (p < end && tk[p].isName("not")) {
            p++;
            Object v = parseNot();
            if (skip > 0) {
                return null;
            }
            return truthy(v) ? F : T;
        }
        return parseCmp();
    }

    private Object parseCmp() throws PyError {
        Object l = parseArith();
        while (p < end) {
            Tok t = tk[p];
            String op;
            if (t.type == OP && isIn("==|!=|<|>|<=|>=", t.s)) {
                op = t.s;
                p++;
            } else if (t.isName("in")) {
                op = "in";
                p++;
            } else if (t.isName("not") && p + 1 < end && tk[p + 1].isName("in")) {
                op = "not in";
                p += 2;
            } else {
                break;
            }
            Object r = parseArith();
            if (skip > 0) {
                l = null;
            } else {
                l = compare(op, l, r);
            }
        }
        return l;
    }

    private Object parseArith() throws PyError {
        Object l = parseTerm();
        while (p < end && (tk[p].isOp("+") || tk[p].isOp("-"))) {
            String op = tk[p++].s;
            Object r = parseTerm();
            l = skip > 0 ? null : binop(op, l, r);
        }
        return l;
    }

    private Object parseTerm() throws PyError {
        Object l = parseUnary();
        while (p < end && tk[p].type == OP && isIn("*|//|/|%", tk[p].s)) {
            String op = tk[p++].s;
            Object r = parseUnary();
            l = skip > 0 ? null : binop(op, l, r);
        }
        return l;
    }

    private Object parseUnary() throws PyError {
        if (p < end && tk[p].isOp("-")) {
            p++;
            Object v = parseUnary();
            return skip > 0 ? null : new Long(-num(v));
        }
        if (p < end && tk[p].isOp("+")) {
            p++;
            Object v = parseUnary();
            return skip > 0 ? null : new Long(num(v));
        }
        return parsePower();
    }

    private Object parsePower() throws PyError {
        Object l = parsePostfix();
        if (p < end && tk[p].isOp("**")) {
            p++;
            Object r = parseUnary();
            return skip > 0 ? null : binop("**", l, r);
        }
        return l;
    }

    private Object parsePostfix() throws PyError {
        Object l = parsePrimary();
        while (p < end) {
            if (tk[p].isOp("[")) {
                p++;
                Object ix = parseOr();
                expectOp("]");
                l = skip > 0 ? null : index(l, ix);
            } else if (tk[p].isOp(".")) {
                p++;
                if (p >= end || tk[p].type != NAME) {
                    throw syntax();
                }
                String m = tk[p++].s;
                if (p >= end || !tk[p].isOp("(")) {
                    throw syntax();
                }
                Vector a = parseArgs();
                l = skip > 0 ? null : method(l, m, a);
            } else {
                break;
            }
        }
        return l;
    }

    private Object parsePrimary() throws PyError {
        if (p >= end) {
            throw syntax();
        }
        Tok t = tk[p++];
        if (t.type == NUM) {
            return skip > 0 ? null : (Object) new Long(t.n);
        }
        if (t.type == STR) {
            StringBuffer sb = new StringBuffer();
            Tok c = t;
            while (true) {
                if (skip == 0) {
                    sb.append(c.f ? fstring(c.s) : c.s);
                }
                if (p < end && tk[p].type == STR) {
                    c = tk[p++];
                } else {
                    break;
                }
            }
            return skip > 0 ? null : (Object) sb.toString();
        }
        if (t.type == NAME) {
            String s = t.s;
            if (s.equals("True")) {
                return skip > 0 ? null : (Object) T;
            }
            if (s.equals("False")) {
                return skip > 0 ? null : (Object) F;
            }
            if (s.equals("None")) {
                return skip > 0 ? null : NONE;
            }
            if (p < end && tk[p].isOp("(")) {
                Vector a = parseArgs();
                Hashtable kw = lastKw;
                return skip > 0 ? null : call(s, a, kw);
            }
            if (skip > 0) {
                return null;
            }
            Object v = vars.get(s);
            if (v == null) {
                throw err("NameError: name '" + s + "' is not defined");
            }
            return v;
        }
        if (t.isOp("(")) {
            Object v = parseOr();
            expectOp(")");
            return v;
        }
        throw syntax();
    }

    private Vector parseArgs() throws PyError {
        Vector a = new Vector();
        Hashtable kw = null;
        p++; // '('
        if (p < end && tk[p].isOp(")")) {
            p++;
            lastKw = null;
            return a;
        }
        while (true) {
            if (p >= end) {
                throw syntax();
            }
            if (p + 1 < end && tk[p].type == NAME && tk[p + 1].isOp("=")) {
                String k = tk[p].s;
                p += 2;
                Object v = parseOr();
                if (kw == null) {
                    kw = new Hashtable();
                }
                if (skip == 0) {
                    kw.put(k, v);
                }
            } else {
                a.addElement(parseOr());
            }
            if (p >= end) {
                throw syntax();
            }
            if (tk[p].isOp(",")) {
                p++;
                if (p < end && tk[p].isOp(")")) {
                    p++;
                    break;
                }
                continue;
            }
            if (tk[p].isOp(")")) {
                p++;
                break;
            }
            throw syntax();
        }
        lastKw = kw;
        return a;
    }

    private String fstring(String s) throws PyError {
        StringBuffer sb = new StringBuffer();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '{') {
                if (i + 1 < n && s.charAt(i + 1) == '{') {
                    sb.append('{');
                    i += 2;
                    continue;
                }
                int j = s.indexOf('}', i + 1);
                if (j < 0) {
                    throw err("SyntaxError: f-string: expecting '}'");
                }
                Tok[] tt = tokenize(s.substring(i + 1, j));
                sb.append(str(eval(tt, 0, tt.length)));
                i = j + 1;
            } else if (c == '}' && i + 1 < n && s.charAt(i + 1) == '}') {
                sb.append('}');
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ values

    private static boolean truthy(Object o) {
        if (o instanceof Boolean) {
            return ((Boolean) o).booleanValue();
        }
        if (o instanceof Long) {
            return ((Long) o).longValue() != 0;
        }
        if (o instanceof String) {
            return ((String) o).length() > 0;
        }
        return o != NONE;
    }

    private static String str(Object o) {
        if (o instanceof String) {
            return (String) o;
        }
        if (o instanceof Boolean) {
            return ((Boolean) o).booleanValue() ? "True" : "False";
        }
        if (o == NONE) {
            return "None";
        }
        return o.toString();
    }

    private long num(Object o) throws PyError {
        if (o instanceof Long) {
            return ((Long) o).longValue();
        }
        if (o instanceof Boolean) {
            return ((Boolean) o).booleanValue() ? 1 : 0;
        }
        throw err("TypeError: expected a number, got " + typeName(o));
    }

    private static String typeName(Object o) {
        if (o instanceof String) {
            return "str";
        }
        if (o instanceof Long) {
            return "int";
        }
        if (o instanceof Boolean) {
            return "bool";
        }
        return "NoneType";
    }

    private Object compare(String op, Object a, Object b) throws PyError {
        if (op.equals("in") || op.equals("not in")) {
            if (!(a instanceof String) || !(b instanceof String)) {
                throw err("TypeError: 'in' needs two strings");
            }
            boolean r = ((String) b).indexOf((String) a) >= 0;
            return (op.equals("in") == r) ? T : F;
        }
        if (a instanceof Boolean) {
            a = new Long(num(a));
        }
        if (b instanceof Boolean) {
            b = new Long(num(b));
        }
        int c;
        boolean comparable = true;
        if (a instanceof Long && b instanceof Long) {
            long x = ((Long) a).longValue(), y = ((Long) b).longValue();
            c = x < y ? -1 : (x > y ? 1 : 0);
        } else if (a instanceof String && b instanceof String) {
            c = ((String) a).compareTo((String) b);
        } else {
            comparable = false;
            c = 0;
        }
        if (op.equals("==")) {
            return (comparable && c == 0) ? T : F;
        }
        if (op.equals("!=")) {
            return (comparable && c == 0) ? F : T;
        }
        if (!comparable) {
            throw err("TypeError: '" + op + "' not supported between " + typeName(a)
                    + " and " + typeName(b));
        }
        boolean r;
        if (op.equals("<")) {
            r = c < 0;
        } else if (op.equals(">")) {
            r = c > 0;
        } else if (op.equals("<=")) {
            r = c <= 0;
        } else {
            r = c >= 0;
        }
        return r ? T : F;
    }

    private Object binop(String op, Object a, Object b) throws PyError {
        if (a instanceof Boolean) {
            a = new Long(num(a));
        }
        if (b instanceof Boolean) {
            b = new Long(num(b));
        }
        if (a instanceof Long && b instanceof Long) {
            long x = ((Long) a).longValue(), y = ((Long) b).longValue();
            if (op.equals("+")) {
                return new Long(x + y);
            }
            if (op.equals("-")) {
                return new Long(x - y);
            }
            if (op.equals("*")) {
                return new Long(x * y);
            }
            if (op.equals("//") || op.equals("/")) {
                if (y == 0) {
                    throw err("ZeroDivisionError: division by zero");
                }
                long q = x / y;
                if ((x % y != 0) && ((x < 0) != (y < 0))) {
                    q--;
                }
                return new Long(q);
            }
            if (op.equals("%")) {
                if (y == 0) {
                    throw err("ZeroDivisionError: modulo by zero");
                }
                long r = x % y;
                if (r != 0 && ((r < 0) != (y < 0))) {
                    r += y;
                }
                return new Long(r);
            }
            if (op.equals("**")) {
                if (y < 0 || y > 62) {
                    throw err("ValueError: exponent must be between 0 and 62");
                }
                long r = 1;
                for (long k = 0; k < y; k++) {
                    r *= x;
                }
                return new Long(r);
            }
        }
        if (op.equals("+") && a instanceof String && b instanceof String) {
            String r = (String) a + (String) b;
            if (r.length() > MAX_STR) {
                throw err("MemoryError: string too long");
            }
            return r;
        }
        if (op.equals("*") && (a instanceof String && b instanceof Long
                || a instanceof Long && b instanceof String)) {
            String s = a instanceof String ? (String) a : (String) b;
            long k = a instanceof Long ? ((Long) a).longValue() : ((Long) b).longValue();
            if (k * s.length() > MAX_STR) {
                throw err("MemoryError: string too long");
            }
            StringBuffer sb = new StringBuffer();
            for (long i = 0; i < k; i++) {
                sb.append(s);
            }
            return sb.toString();
        }
        if (op.equals("+") && (a instanceof String || b instanceof String)) {
            throw err("TypeError: can only concatenate str with str (use str(x) or an f-string)");
        }
        throw err("TypeError: unsupported operand types for " + op + ": "
                + typeName(a) + " and " + typeName(b));
    }

    private Object index(Object l, Object ix) throws PyError {
        if (!(l instanceof String)) {
            throw err("TypeError: only strings can be indexed");
        }
        String s = (String) l;
        long k = num(ix);
        if (k < 0) {
            k += s.length();
        }
        if (k < 0 || k >= s.length()) {
            throw err("IndexError: string index out of range");
        }
        return s.substring((int) k, (int) k + 1);
    }

    // ------------------------------------------------------------ builtins

    private String strArg(Vector a, int i, String fn) throws PyError {
        if (i >= a.size() || !(a.elementAt(i) instanceof String)) {
            throw err("TypeError: " + fn + "() needs a string argument");
        }
        return (String) a.elementAt(i);
    }

    private Object call(String name, Vector a, Hashtable kw) throws PyError {
        int c = a.size();
        if (name.equals("print")) {
            String sep = " ", endS = "\n";
            if (kw != null) {
                if (kw.get("sep") != null) {
                    sep = str(kw.get("sep"));
                }
                if (kw.get("end") != null) {
                    endS = str(kw.get("end"));
                }
            }
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < c; i++) {
                if (i > 0) {
                    sb.append(sep);
                }
                sb.append(str(a.elementAt(i)));
            }
            sb.append(endS);
            io.print(sb.toString());
            return NONE;
        }
        if (name.equals("input")) {
            String prompt = c > 0 ? str(a.elementAt(0)) : "";
            if (prompt.length() > 0) {
                io.print(prompt);
            }
            String r = io.input(prompt);
            if (r == null || stopped) {
                throw new PyError(0, "Stopped");
            }
            io.print(r + "\n");
            return r;
        }
        if (name.equals("str")) {
            return c == 0 ? "" : str(a.elementAt(0));
        }
        if (name.equals("int")) {
            if (c == 0) {
                return new Long(0);
            }
            Object o = a.elementAt(0);
            if (o instanceof String) {
                String s = ((String) o).trim();
                boolean ok = s.length() > 0;
                int i = 0;
                if (ok && (s.charAt(0) == '-' || s.charAt(0) == '+')) {
                    i = 1;
                    ok = s.length() > 1;
                }
                for (int k = i; ok && k < s.length(); k++) {
                    if (!isDig(s.charAt(k))) {
                        ok = false;
                    }
                }
                if (!ok || s.length() > 18) {
                    throw err("ValueError: invalid literal for int(): '" + s + "'");
                }
                return new Long(Long.parseLong(s.charAt(0) == '+' ? s.substring(1) : s));
            }
            return new Long(num(o));
        }
        if (name.equals("len")) {
            return new Long(strArg(a, 0, "len").length());
        }
        if (name.equals("abs")) {
            if (c != 1) {
                throw err("TypeError: abs() takes 1 argument");
            }
            long x = num(a.elementAt(0));
            return new Long(x < 0 ? -x : x);
        }
        if (name.equals("min") || name.equals("max")) {
            if (c < 2) {
                throw err("TypeError: " + name + "() needs at least 2 arguments");
            }
            long best = num(a.elementAt(0));
            for (int i = 1; i < c; i++) {
                long x = num(a.elementAt(i));
                if (name.equals("min") ? x < best : x > best) {
                    best = x;
                }
            }
            return new Long(best);
        }
        if (name.equals("range")) {
            throw err("TypeError: range() only works in a 'for' line, e.g. for i in range(5):");
        }
        throw err("NameError: name '" + name + "' is not defined");
    }

    private Object method(Object l, String m, Vector a) throws PyError {
        if (!(l instanceof String)) {
            throw err("AttributeError: " + typeName(l) + " has no method '" + m + "'");
        }
        String s = (String) l;
        if (m.equals("upper")) {
            return s.toUpperCase();
        }
        if (m.equals("lower")) {
            return s.toLowerCase();
        }
        if (m.equals("strip")) {
            return s.trim();
        }
        if (m.equals("startswith")) {
            return s.startsWith(strArg(a, 0, m)) ? T : F;
        }
        if (m.equals("endswith")) {
            return s.endsWith(strArg(a, 0, m)) ? T : F;
        }
        if (m.equals("find")) {
            return new Long(s.indexOf(strArg(a, 0, m)));
        }
        if (m.equals("isdigit")) {
            boolean ok = s.length() > 0;
            for (int i = 0; ok && i < s.length(); i++) {
                if (!isDig(s.charAt(i))) {
                    ok = false;
                }
            }
            return ok ? T : F;
        }
        if (m.equals("replace")) {
            String o = strArg(a, 0, m), nw = strArg(a, 1, m);
            if (o.length() == 0) {
                return s;
            }
            StringBuffer sb = new StringBuffer();
            int i = 0;
            while (true) {
                int j = s.indexOf(o, i);
                if (j < 0) {
                    sb.append(s.substring(i));
                    break;
                }
                sb.append(s.substring(i, j));
                sb.append(nw);
                i = j + o.length();
            }
            return sb.toString();
        }
        throw err("AttributeError: str has no method '" + m + "'");
    }
}
