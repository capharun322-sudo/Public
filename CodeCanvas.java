import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Colour code editor. Typing goes straight into the text; the centre key
 * (FIRE) opens the phone's own text box for the current line.
 */
public class CodeCanvas extends Canvas {
    public interface Host {
        void editLine(int row);
    }

    public static final int MAX_CHARS = 20000;
    public static final int MAX_LINES = 600;

    private Host host;
    private Vector lines = new Vector();
    private int row = 0, col = 0, top = 0, left = 0;
    private int[] th;
    private Font font;
    private int cw, lh;
    public int caseMode = 0; // 0 = as received, 1 = force lower case
    private boolean upperNext = false;

    public CodeCanvas(Host host, int theme, int fontSize) {
        this.host = host;
        setTheme(theme);
        setFontSize(fontSize);
        lines.addElement("");
    }

    public void setTheme(int t) {
        th = PyTheme.get(t);
        repaint();
    }

    public void setFontSize(int s) {
        font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN,
                s == 0 ? Font.SIZE_SMALL : Font.SIZE_MEDIUM);
        cw = font.charWidth('M');
        lh = font.getHeight() + 1;
        repaint();
    }

    public void setUpperNext() {
        upperNext = true;
        repaint();
    }

    // ------------------------------------------------------------ text model

    public String getText() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append((String) lines.elementAt(i));
        }
        return sb.toString();
    }

    public void setText(String s) {
        lines = new Vector();
        int st = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == '\n') {
                String l = s.substring(st, i);
                if (l.length() > 0 && l.charAt(l.length() - 1) == '\r') {
                    l = l.substring(0, l.length() - 1);
                }
                if (lines.size() < MAX_LINES) {
                    lines.addElement(l);
                }
                st = i + 1;
            }
        }
        if (lines.size() == 0) {
            lines.addElement("");
        }
        row = 0;
        col = 0;
        top = 0;
        left = 0;
        repaint();
    }

    /** Replace the text but keep the cursor near where it was. */
    public void setTextKeep(String s, int newRow) {
        setText(s);
        gotoLine(newRow);
    }

    public int getRow() {
        return row;
    }

    public int getLineCount() {
        return lines.size();
    }

    public void gotoLine(int r) {
        if (r < 0) {
            r = 0;
        }
        if (r >= lines.size()) {
            r = lines.size() - 1;
        }
        row = r;
        col = line(row).length();
        repaint();
    }

    private String line(int r) {
        return (String) lines.elementAt(r);
    }

    private int charCount() {
        int n = 0;
        for (int i = 0; i < lines.size(); i++) {
            n += line(i).length() + 1;
        }
        return n;
    }

    private static String spaces(int n) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < n; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private void typeChar(char c) {
        if (charCount() >= MAX_CHARS) {
            return;
        }
        boolean up = c >= 'A' && c <= 'Z';
        boolean lo = c >= 'a' && c <= 'z';
        if (up && caseMode == 1 && !upperNext) {
            c = (char) (c + 32);
        } else if (lo && upperNext) {
            c = (char) (c - 32);
        }
        if (up || lo) {
            upperNext = false;
        }
        String l = line(row);
        lines.setElementAt(l.substring(0, col) + c + l.substring(col), row);
        col++;
    }

    public void insertText(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                newline();
            } else if (c >= 32) {
                if (charCount() >= MAX_CHARS) {
                    break;
                }
                String l = line(row);
                lines.setElementAt(l.substring(0, col) + c + l.substring(col), row);
                col++;
            }
        }
        repaint();
    }

    public void newline() {
        if (lines.size() >= MAX_LINES || charCount() >= MAX_CHARS) {
            return;
        }
        String l = line(row);
        String before = l.substring(0, col), after = l.substring(col);
        int ind = 0;
        while (ind < before.length() && before.charAt(ind) == ' ') {
            ind++;
        }
        String t = before.trim();
        if (t.length() > 0 && t.charAt(t.length() - 1) == ':' && t.charAt(0) != '#') {
            ind += 4;
        }
        String pad = spaces(ind);
        lines.setElementAt(before, row);
        lines.insertElementAt(pad + after, row + 1);
        row++;
        col = pad.length();
    }

    private void backspace() {
        if (col > 0) {
            String l = line(row);
            int cut = 1;
            if (col >= 4 && col % 4 == 0 && l.substring(0, col).trim().length() == 0) {
                cut = 4;
            }
            lines.setElementAt(l.substring(0, col - cut) + l.substring(col), row);
            col -= cut;
        } else if (row > 0) {
            String prev = line(row - 1);
            lines.setElementAt(prev + line(row), row - 1);
            lines.removeElementAt(row);
            row--;
            col = prev.length();
        }
    }

    // ------------------------------------------------------------ keys

    protected void keyPressed(int k) {
        handle(k);
        repaint();
    }

    protected void keyRepeated(int k) {
        handle(k);
        repaint();
    }

    private void moveLeft() {
        if (col > 0) {
            col--;
        } else if (row > 0) {
            row--;
            col = line(row).length();
        }
    }

    private void moveRight() {
        if (col < line(row).length()) {
            col++;
        } else if (row < lines.size() - 1) {
            row++;
            col = 0;
        }
    }

    private void moveUp() {
        if (row > 0) {
            row--;
            if (col > line(row).length()) {
                col = line(row).length();
            }
        }
    }

    private void moveDown() {
        if (row < lines.size() - 1) {
            row++;
            if (col > line(row).length()) {
                col = line(row).length();
            }
        }
    }

    private void handle(int k) {
        if (k >= 32 && k != 127) {
            typeChar((char) k);
            return;
        }
        if (k == 10 || k == 13) {
            newline();
            return;
        }
        if (k == 8 || k == -8) {
            backspace();
            return;
        }
        int ga = 0;
        try {
            ga = getGameAction(k);
        } catch (IllegalArgumentException e) {
            ga = 0;
        }
        if (ga == UP || k == -1) {
            moveUp();
        } else if (ga == DOWN || k == -2) {
            moveDown();
        } else if (ga == LEFT || k == -3) {
            moveLeft();
        } else if (ga == RIGHT || k == -4) {
            moveRight();
        } else if (ga == FIRE || k == -5) {
            host.editLine(row);
        }
    }

    // ------------------------------------------------------------ painting

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(th[PyTheme.BG]);
        g.fillRect(0, 0, w, h);
        g.setFont(font);

        int statusH = lh + 2;
        int rows = (h - statusH) / lh;
        if (rows < 1) {
            rows = 1;
        }
        if (row < top) {
            top = row;
        }
        if (row >= top + rows) {
            top = row - rows + 1;
        }
        int gutter = font.stringWidth("000") + 6;
        int avail = w - gutter - 3;

        String cl = line(row);
        if (col < left) {
            left = col;
        }
        while (left < col && font.substringWidth(cl, left, col - left) > avail) {
            left++;
        }

        for (int r = 0; r < rows; r++) {
            int idx = top + r;
            if (idx >= lines.size()) {
                break;
            }
            int y = r * lh;
            String s = line(idx);
            if (idx == row) {
                g.setColor(th[PyTheme.CURLINE]);
                g.fillRect(0, y, w, lh);
            }
            String num = String.valueOf(idx + 1);
            g.setColor(th[PyTheme.LNUM]);
            g.drawString(num, gutter - 4 - font.stringWidth(num), y + 1, Graphics.TOP | Graphics.LEFT);

            g.setClip(gutter, y, w - gutter, lh);
            byte[] cls = PySyntax.classify(s);
            int n = s.length();
            int i = left < n ? left : n;
            int x = gutter;
            while (i < n) {
                int j = i;
                byte kind = cls[i];
                while (j < n && cls[j] == kind) {
                    j++;
                }
                String seg = s.substring(i, j);
                g.setColor(PyTheme.color(th, kind));
                g.drawString(seg, x, y + 1, Graphics.TOP | Graphics.LEFT);
                x += font.stringWidth(seg);
                i = j;
            }
            if (idx == row) {
                int cx = gutter + (col > left ? font.substringWidth(s, left, col - left) : 0);
                g.setColor(th[PyTheme.CURSOR]);
                g.fillRect(cx, y, 2, lh);
            }
            g.setClip(0, 0, w, h);
        }

        int by = h - statusH;
        g.setColor(th[PyTheme.BAR]);
        g.fillRect(0, by, w, statusH);
        g.setColor(th[PyTheme.BARTEXT]);
        String st = (row + 1) + "/" + lines.size() + "  s" + (col + 1);
        if (upperNext) {
            st = st + "  ABC";
        }
        g.drawString(st, 3, by + 1, Graphics.TOP | Graphics.LEFT);
    }
}
