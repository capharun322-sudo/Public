import java.util.Vector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;

/**
 * PyPhone v2 - Python editor + runner for J2ME phones (Turkish menus,
 * colour editor, themes, ready-made example programs).
 */
public class PyEdit extends MIDlet implements CommandListener, CodeCanvas.Host {
    private static final String STORE = "pyfiles";
    private static final String SETSTORE = "pyset";
    private static final String HELLO = "print(\"Merhaba D\u00fcnya!\")\n";
    private static final int EDIT_MAX = 8000;

    private static final String[] CH_LABEL = {
        "Sonraki harf B\u00dcY\u00dcK", "ENTER (yeni sat\u0131r)", "ENTER + 4 bo\u015fluk", "TAB (4 bo\u015fluk)",
        "(", ")", "[", "]", "{", "}", ":", "\"", "'", "=", "==", "!=",
        "<=", ">=", "<", ">", "+", "-", "*", "//", "%", "#", ",", ".", "_", "\\",
        "&", "|", "!", "@", ";", "~", "^", "$", "?",
        "\u00e7", "\u011f", "\u0131", "\u00f6", "\u015f", "\u00fc",
        "\u00c7", "\u011e", "\u0130", "\u00d6", "\u015e", "\u00dc",
        "print(\"\")", "input(\"\")", "for i in range(10):", "if x == 1:", "while True:", "def f(x):"
    };
    private static final String[] CH_TEXT = {
        null, "\n", "\n    ", "    ",
        "(", ")", "[", "]", "{", "}", ":", "\"", "'", "=", "==", "!=",
        "<=", ">=", "<", ">", "+", "-", "*", "//", "%", "#", ",", ".", "_", "\\",
        "&", "|", "!", "@", ";", "~", "^", "$", "?",
        "\u00e7", "\u011f", "\u0131", "\u00f6", "\u015f", "\u00fc",
        "\u00c7", "\u011e", "\u0130", "\u00d6", "\u015e", "\u00dc",
        "print(\"\")", "input(\"\")", "for i in range(10):", "if x == 1:", "while True:", "def f(x):"
    };

    private Display display;

    private List home, fileMenu, openList, gotoList, charsList, samplesList, themeList, settingsList;
    private Form outForm, helpForm;
    private StringItem outText;
    private TextBox classic, nameBox, lineBox, inputBox;
    private CodeCanvas canvas;
    private KeyTest keyTest;
    private Displayable editor; // canvas or classic, null when no file is open

    private final Command cFile = new Command("Dosya", Command.SCREEN, 1);
    private final Command cRun = new Command("\u00c7al\u0131\u015ft\u0131r", Command.SCREEN, 2);
    private final Command cGoto = new Command("Sat\u0131ra git", Command.SCREEN, 3);
    private final Command cChars = new Command("Karakterler", Command.SCREEN, 4);
    private final Command cSettings = new Command("Ayarlar", Command.SCREEN, 5);
    private final Command cBack = new Command("Geri", Command.BACK, 1);
    private final Command cOk = new Command("Tamam", Command.OK, 1);
    private final Command cDel = new Command("Sil", Command.SCREEN, 2);
    private final Command cStop = new Command("Dur", Command.STOP, 1);
    private final Command cExit = new Command("\u00c7\u0131k\u0131\u015f", Command.EXIT, 1);
    private final Command cAddBelow = new Command("Alt\u0131na sat\u0131r ekle", Command.SCREEN, 2);
    private final Command cDelLine = new Command("Sat\u0131r\u0131 sil", Command.SCREEN, 3);

    private String curName = null;
    private boolean autoSave = true;
    private boolean useCanvas = true;
    private int theme = 0;
    private int caseMode = 0;
    private int fontSize = 0;
    private int nameMode = 0; // 0 new file, 1 save as, 2 from example
    private int tplIdx = 0;
    private Vector openNames;
    private Vector gotoLines;
    private int gotoIdx;
    private boolean lineFromCanvas = false;
    private Runner runner;
    private Displayable helpBack;

    // ------------------------------------------------------------ lifecycle

    public void startApp() {
        if (display != null) {
            return;
        }
        display = Display.getDisplay(this);
        loadSettings();
        buildScreens();
        display.setCurrent(home);
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
        if (runner != null) {
            runner.stop();
        }
    }

    private void quit() {
        saveCurrent();
        destroyApp(true);
        notifyDestroyed();
    }

    // ------------------------------------------------------------ screens

    private List newList(String title, String[] items) {
        List l = new List(title, Choice.IMPLICIT, items, null);
        l.addCommand(cBack);
        l.setCommandListener(this);
        return l;
    }

    private void buildScreens() {
        home = new List("PyPhone", Choice.IMPLICIT, new String[] {
            "Yeni dosya", "Dosya a\u00e7", "\u00d6rnek kodlar", "Ayarlar", "Yard\u0131m", "\u00c7\u0131k\u0131\u015f"}, null);
        home.addCommand(cExit);
        home.setCommandListener(this);

        fileMenu = newList("Dosya", new String[] {
            "Yeni", "\u00d6rnek kodlar", "A\u00e7", "Kaydet", "Farkl\u0131 kaydet", "Kapat", "\u00c7\u0131k\u0131\u015f"});
        charsList = newList("Karakterler", CH_LABEL);
        samplesList = newList("\u00d6rnek kodlar", Templates.NAMES);
        themeList = newList("Tema", PyTheme.NAMES);

        settingsList = newList("Ayarlar", new String[] {"", "", "", "", "", "", ""});
        refreshSettings();

        outForm = new Form("\u00c7\u0131kt\u0131");
        outText = new StringItem(null, "");
        outForm.append(outText);
        outForm.addCommand(cBack);
        outForm.addCommand(cStop);
        outForm.setCommandListener(this);

        helpForm = new Form("Yard\u0131m");
        helpForm.append("Nas\u0131l kullan\u0131l\u0131r:\n"
                + "- Yaz\u0131 yaz: tu\u015flarla direkt yaz\u0131l\u0131r.\n"
                + "- Orta tu\u015f (FIRE): o sat\u0131r\u0131 telefonun kutusunda d\u00fczenler.\n"
                + "- Se\u00e7enekler: Dosya, \u00c7al\u0131\u015ft\u0131r, Sat\u0131ra git, Karakterler, Ayarlar.\n"
                + "- Karakterler: ( ) : \" gibi i\u015faretler ve T\u00fcrk\u00e7e harfler.\n"
                + "- Enter sat\u0131r\u0131 b\u00f6ler. Tu\u015f \u00e7al\u0131\u015fm\u0131yorsa Karakterler > ENTER.\n"
                + "- ':' ile biten sat\u0131rdan sonra girinti otomatik gelir.\n\n"
                + "Python: print input int str bool len abs min max sum range list chr ord\n"
                + "if elif else while for break continue pass def return global\n"
                + "listeler [1,2,3], l[0], l[1:3], append pop insert remove sort reverse\n"
                + "yaz\u0131: upper lower strip replace split join find count startswith endswith isdigit\n"
                + "f\"{x}\" kullan\u0131labilir. Ondal\u0131k say\u0131 yok, / tam say\u0131 b\u00f6lmesi.");
        helpForm.addCommand(cBack);
        helpForm.setCommandListener(this);

        keyTest = new KeyTest();
        keyTest.addCommand(cBack);
        keyTest.setCommandListener(this);
    }

    private String settingLabel(int i) {
        switch (i) {
            case 0: return "Tema: " + PyTheme.NAMES[theme];
            case 1: return "Edit\u00f6r: " + (useCanvas ? "Renkli" : "Klasik kutu");
            case 2: return "Harf modu: " + (caseMode == 0 ? "Normal" : "Hep k\u00fc\u00e7\u00fck");
            case 3: return "Yaz\u0131 boyu: " + (fontSize == 0 ? "K\u00fc\u00e7\u00fck" : "Orta");
            case 4: return "\u00d6nce kaydet: " + (autoSave ? "A\u00e7\u0131k" : "Kapal\u0131");
            case 5: return "Tu\u015f testi";
            default: return "Yard\u0131m";
        }
    }

    private void refreshSettings() {
        for (int i = 0; i < 7; i++) {
            settingsList.set(i, settingLabel(i), null);
        }
    }

    private Displayable cur() {
        if (curName != null && editor != null) {
            return editor;
        }
        return home;
    }

    private void show(Displayable d) {
        display.setCurrent(d);
    }

    private void info(String msg, Displayable next) {
        Alert a = new Alert("PyPhone", msg, null, AlertType.INFO);
        a.setTimeout(1500);
        display.setCurrent(a, next);
    }

    // ------------------------------------------------------------ editor access

    private String edGet() {
        if (editor == canvas && canvas != null) {
            return canvas.getText();
        }
        return classic.getString();
    }

    private void edSet(String t, int row) {
        if (editor == canvas && canvas != null) {
            canvas.setTextKeep(t, row);
        } else {
            classic.setString(t);
        }
    }

    private int edMax() {
        if (editor == canvas && canvas != null) {
            return CodeCanvas.MAX_CHARS;
        }
        return classic.getMaxSize();
    }

    private void createEditor(String name, String text) {
        if (useCanvas) {
            if (canvas == null) {
                canvas = new CodeCanvas(this, theme, fontSize);
                canvas.addCommand(cFile);
                canvas.addCommand(cRun);
                canvas.addCommand(cGoto);
                canvas.addCommand(cChars);
                canvas.addCommand(cSettings);
                canvas.setCommandListener(this);
            }
            canvas.caseMode = caseMode;
            canvas.setText(text);
            editor = canvas;
        } else {
            TextBox tb = new TextBox(name, "", EDIT_MAX, TextField.ANY | TextField.NON_PREDICTIVE);
            int m = tb.getMaxSize();
            if (text.length() > m) {
                text = text.substring(0, m);
            }
            tb.setString(text);
            tb.addCommand(cFile);
            tb.addCommand(cRun);
            tb.addCommand(cGoto);
            tb.addCommand(cChars);
            tb.addCommand(cSettings);
            tb.setCommandListener(this);
            classic = tb;
            editor = tb;
        }
        curName = name;
    }

    // ------------------------------------------------------------ commands

    public void commandAction(Command c, Displayable d) {
        if (d == editor) {
            if (c == cFile) {
                show(fileMenu);
            } else if (c == cRun) {
                build();
            } else if (c == cGoto) {
                showGoto(0);
            } else if (c == cChars) {
                show(charsList);
            } else if (c == cSettings) {
                refreshSettings();
                show(settingsList);
            }
        } else if (d == home) {
            if (c == cExit) {
                quit();
            } else if (c == List.SELECT_COMMAND) {
                homeAction(home.getSelectedIndex());
            }
        } else if (d == fileMenu) {
            if (c == cBack) {
                show(cur());
            } else if (c == List.SELECT_COMMAND) {
                fileAction(fileMenu.getSelectedIndex());
            }
        } else if (d == samplesList) {
            if (c == cBack) {
                show(cur());
            } else if (c == List.SELECT_COMMAND) {
                tplIdx = samplesList.getSelectedIndex();
                nameMode = 2;
                askName("Dosya ad\u0131", Templates.FILES[tplIdx]);
            }
        } else if (d == nameBox) {
            if (c == cBack) {
                show(cur());
            } else if (c == cOk) {
                nameDone();
            }
        } else if (d == openList) {
            if (c == cBack) {
                show(cur());
            } else if (c == cDel) {
                deleteSelected();
            } else if (c == List.SELECT_COMMAND) {
                String n = (String) openNames.elementAt(openList.getSelectedIndex());
                saveCurrent();
                openFile(n);
            }
        } else if (d == gotoList) {
            if (c == cBack) {
                show(editor);
            } else if (c == List.SELECT_COMMAND) {
                lineFromCanvas = false;
                openLineBox(gotoList.getSelectedIndex());
            }
        } else if (d == lineBox) {
            lineAction(c);
        } else if (d == charsList) {
            if (c == cBack) {
                show(editor);
            } else if (c == List.SELECT_COMMAND) {
                charPicked(charsList.getSelectedIndex());
            }
        } else if (d == outForm) {
            if (c == cStop) {
                if (runner != null) {
                    runner.stop();
                }
            } else if (c == cBack) {
                if (runner != null) {
                    runner.stop();
                }
                show(cur());
            }
        } else if (d == inputBox) {
            if (runner != null) {
                if (c == cOk) {
                    runner.provide(inputBox.getString());
                } else {
                    runner.stop();
                }
            }
            show(outForm);
        } else if (d == settingsList) {
            if (c == cBack) {
                show(cur());
            } else if (c == List.SELECT_COMMAND) {
                settingAction(settingsList.getSelectedIndex());
            }
        } else if (d == themeList) {
            if (c == cBack) {
                show(settingsList);
            } else if (c == List.SELECT_COMMAND) {
                theme = themeList.getSelectedIndex();
                if (canvas != null) {
                    canvas.setTheme(theme);
                }
                saveSettings();
                refreshSettings();
                show(settingsList);
            }
        } else if (d == helpForm) {
            show(helpBack);
        } else if (d == keyTest) {
            show(settingsList);
        }
    }

    private void homeAction(int idx) {
        if (idx == 0) {
            nameMode = 0;
            askName("Yeni dosya ad\u0131", "merhaba.py");
        } else if (idx == 1) {
            showOpen();
        } else if (idx == 2) {
            show(samplesList);
        } else if (idx == 3) {
            refreshSettings();
            show(settingsList);
        } else if (idx == 4) {
            helpBack = home;
            show(helpForm);
        } else {
            quit();
        }
    }

    private void settingAction(int idx) {
        if (idx == 0) {
            themeList.setSelectedIndex(theme, true);
            show(themeList);
            return;
        }
        if (idx == 1) {
            String t = null;
            if (curName != null && editor != null) {
                t = edGet();
            }
            useCanvas = !useCanvas;
            if (t != null) {
                createEditor(curName, t);
            }
        } else if (idx == 2) {
            caseMode = 1 - caseMode;
            if (canvas != null) {
                canvas.caseMode = caseMode;
            }
        } else if (idx == 3) {
            fontSize = 1 - fontSize;
            if (canvas != null) {
                canvas.setFontSize(fontSize);
            }
        } else if (idx == 4) {
            autoSave = !autoSave;
        } else if (idx == 5) {
            show(keyTest);
            return;
        } else {
            helpBack = settingsList;
            show(helpForm);
            return;
        }
        saveSettings();
        refreshSettings();
    }

    // ------------------------------------------------------------ File menu

    private void fileAction(int idx) {
        if (idx == 0) {
            nameMode = 0;
            askName("Yeni dosya ad\u0131", "merhaba.py");
        } else if (idx == 1) {
            show(samplesList);
        } else if (idx == 2) {
            showOpen();
        } else if (idx == 3) {
            if (curName == null) {
                info("A\u00e7\u0131k dosya yok", home);
            } else if (saveCurrent()) {
                info("Kaydedildi: " + curName, editor);
            }
        } else if (idx == 4) {
            if (curName == null) {
                info("A\u00e7\u0131k dosya yok", home);
            } else {
                nameMode = 1;
                askName("Farkl\u0131 kaydet", curName);
            }
        } else if (idx == 5) {
            saveCurrent();
            curName = null;
            editor = null;
            show(home);
        } else {
            quit();
        }
    }

    private void askName(String title, String initial) {
        nameBox = new TextBox(title, initial, 30, TextField.ANY);
        nameBox.addCommand(cOk);
        nameBox.addCommand(cBack);
        nameBox.setCommandListener(this);
        show(nameBox);
    }

    private String uniqueName(String n) {
        if (loadFile(n) == null) {
            return n;
        }
        String base = n, ext = "";
        int dot = n.lastIndexOf('.');
        if (dot > 0) {
            base = n.substring(0, dot);
            ext = n.substring(dot);
        }
        for (int i = 2; i < 100; i++) {
            String t = base + i + ext;
            if (loadFile(t) == null) {
                return t;
            }
        }
        return n;
    }

    private void nameDone() {
        String n = normName(nameBox.getString());
        if (n == null) {
            info("Ge\u00e7ersiz dosya ad\u0131", nameBox);
            return;
        }
        if (nameMode == 0) {
            saveCurrent();
            if (loadFile(n) == null) {
                saveFile(n, HELLO);
            }
            openFile(n);
        } else if (nameMode == 2) {
            saveCurrent();
            n = uniqueName(n);
            saveFile(n, Templates.CODE[tplIdx]);
            openFile(n);
        } else {
            if (saveFile(n, edGet())) {
                curName = n;
                if (editor == classic && classic != null) {
                    classic.setTitle(n);
                }
                show(editor);
            }
        }
    }

    private String normName(String s) {
        StringBuffer sb = new StringBuffer();
        s = s.trim();
        for (int i = 0; i < s.length() && sb.length() < 30; i++) {
            char c = s.charAt(i);
            if (c != '/' && c != '\\' && c != '\n' && c != '\r') {
                sb.append(c);
            }
        }
        String r = sb.toString().trim();
        if (r.length() == 0) {
            return null;
        }
        if (r.indexOf('.') < 0) {
            r = r + ".py";
        }
        return r;
    }

    private void showOpen() {
        Vector n = names();
        if (n.size() == 0) {
            info("Hen\u00fcz dosya yok. Yeni'yi kullan.", cur());
            return;
        }
        openNames = n;
        openList = new List("A\u00e7", Choice.IMPLICIT);
        for (int i = 0; i < n.size(); i++) {
            openList.append((String) n.elementAt(i), null);
        }
        openList.addCommand(cBack);
        openList.addCommand(cDel);
        openList.setCommandListener(this);
        show(openList);
    }

    private void deleteSelected() {
        String n = (String) openNames.elementAt(openList.getSelectedIndex());
        deleteFile(n);
        if (n.equals(curName)) {
            curName = null;
            editor = null;
        }
        showOpen();
    }

    private void openFile(String name) {
        String text = loadFile(name);
        if (text == null) {
            info("A\u00e7\u0131lamad\u0131: " + name, cur());
            return;
        }
        createEditor(name, text);
        show(editor);
    }

    // ------------------------------------------------------------ Goto / line edit

    private Vector splitLines(String s) {
        Vector v = new Vector();
        int st = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == '\n') {
                String l = s.substring(st, i);
                if (l.length() > 0 && l.charAt(l.length() - 1) == '\r') {
                    l = l.substring(0, l.length() - 1);
                }
                v.addElement(l);
                st = i + 1;
            }
        }
        return v;
    }

    private String joinLines(Vector v) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < v.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append((String) v.elementAt(i));
        }
        return sb.toString();
    }

    private String trunc(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "~" : s;
    }

    private void showGoto(int sel) {
        gotoLines = splitLines(edGet());
        gotoList = new List("Sat\u0131ra git", Choice.IMPLICIT);
        for (int i = 0; i < gotoLines.size(); i++) {
            gotoList.append((i + 1) + ": " + trunc((String) gotoLines.elementAt(i), 36), null);
        }
        gotoList.addCommand(cBack);
        gotoList.setCommandListener(this);
        if (sel >= 0 && sel < gotoList.size()) {
            gotoList.setSelectedIndex(sel, true);
        }
        show(gotoList);
    }

    /** Called by the colour editor when the centre key is pressed. */
    public void editLine(int row) {
        gotoLines = splitLines(edGet());
        lineFromCanvas = true;
        if (row >= gotoLines.size()) {
            row = gotoLines.size() - 1;
        }
        openLineBox(row);
    }

    private void openLineBox(int idx) {
        String t = (String) gotoLines.elementAt(idx);
        if (t.length() > 300) {
            info("Sat\u0131r \u00e7ok uzun", cur());
            return;
        }
        gotoIdx = idx;
        lineBox = new TextBox("Sat\u0131r " + (idx + 1), "", 300, TextField.ANY | TextField.NON_PREDICTIVE);
        lineBox.setString(t);
        lineBox.addCommand(cOk);
        lineBox.addCommand(cAddBelow);
        lineBox.addCommand(cDelLine);
        lineBox.addCommand(cBack);
        lineBox.setCommandListener(this);
        show(lineBox);
    }

    private void backFromLine(int sel) {
        if (lineFromCanvas) {
            if (editor == canvas && canvas != null) {
                canvas.gotoLine(sel);
            }
            show(editor);
        } else {
            showGoto(sel);
        }
    }

    private void lineAction(Command c) {
        int sel = gotoIdx;
        if (c == cBack) {
            backFromLine(sel);
            return;
        }
        gotoLines.setElementAt(lineBox.getString(), gotoIdx);
        if (c == cAddBelow) {
            gotoLines.insertElementAt("", gotoIdx + 1);
            sel = gotoIdx + 1;
        } else if (c == cDelLine) {
            if (gotoLines.size() > 1) {
                gotoLines.removeElementAt(gotoIdx);
                if (sel >= gotoLines.size()) {
                    sel = gotoLines.size() - 1;
                }
            } else {
                gotoLines.setElementAt("", 0);
            }
        }
        String t = joinLines(gotoLines);
        if (t.length() > edMax()) {
            info("Metin \u00e7ok uzun", lineBox);
            return;
        }
        edSet(t, sel);
        backFromLine(sel);
    }

    // ------------------------------------------------------------ Chars

    private void charPicked(int idx) {
        if (idx == 0) {
            if (editor == canvas && canvas != null) {
                canvas.setUpperNext();
                show(editor);
            } else {
                info("Sadece renkli edit\u00f6rde", editor);
            }
            return;
        }
        insertText(CH_TEXT[idx]);
    }

    private void insertText(String s) {
        if (editor == canvas && canvas != null) {
            canvas.insertText(s);
            show(editor);
            return;
        }
        try {
            classic.insert(s, classic.getCaretPosition());
        } catch (IllegalArgumentException e) {
            info("Edit\u00f6r dolu", editor);
            return;
        }
        show(editor);
    }

    // ------------------------------------------------------------ key test screen

    private class KeyTest extends Canvas {
        String l1 = "Bir tu\u015fa bas", l2 = "", l3 = "";

        protected void paint(Graphics g) {
            g.setColor(0xFFFFFF);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0x000000);
            g.setFont(Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
            g.drawString("TU\u015e TEST\u0130", 4, 4, Graphics.TOP | Graphics.LEFT);
            g.drawString(l1, 4, 34, Graphics.TOP | Graphics.LEFT);
            g.drawString(l2, 4, 62, Graphics.TOP | Graphics.LEFT);
            g.drawString(l3, 4, 90, Graphics.TOP | Graphics.LEFT);
            g.drawString("Geri: Se\u00e7enekler", 4, 130, Graphics.TOP | Graphics.LEFT);
        }

        protected void keyPressed(int k) {
            l1 = "kod: " + k;
            String nm = "";
            try {
                nm = getKeyName(k);
            } catch (Exception e) {
                nm = "?";
            }
            l2 = "ad: " + nm;
            int ga = 0;
            try {
                ga = getGameAction(k);
            } catch (Exception e) {
                ga = -1;
            }
            l3 = "oyun tu\u015fu: " + ga;
            repaint();
        }
    }

    // ------------------------------------------------------------ Run

    private void build() {
        if (curName == null || editor == null) {
            info("A\u00e7\u0131k dosya yok", home);
            return;
        }
        if (autoSave) {
            saveCurrent();
        }
        if (runner != null) {
            runner.stop();
        }
        runner = new Runner(edGet());
        outText.setText("");
        show(outForm);
        new Thread(runner).start();
    }

    private class Runner implements Runnable, PyIO {
        final PyInterp interp;
        final String src;
        final Object lock = new Object();
        StringBuffer buf = new StringBuffer();
        long last = 0;
        volatile boolean stopped = false;
        String inVal;
        boolean inDone;

        Runner(String src) {
            this.src = src;
            this.interp = new PyInterp(this);
        }

        public void run() {
            try {
                interp.run(src);
                finish("[Bitti]");
            } catch (PyError e) {
                if (stopped || e.line == 0) {
                    finish("[Durduruldu]");
                } else {
                    finish("Sat\u0131r " + e.line + ": " + e.getMessage());
                }
            } catch (Throwable t) {
                finish("Hata: " + t);
            }
        }

        private void finish(String msg) {
            if (buf.length() > 0 && buf.charAt(buf.length() - 1) != '\n') {
                buf.append('\n');
            }
            buf.append(msg);
            buf.append('\n');
            flush();
        }

        void flush() {
            last = System.currentTimeMillis();
            if (runner == this) {
                outText.setText(buf.toString());
            }
        }

        public void print(String s) {
            buf.append(s);
            if (buf.length() > 2500) {
                String t = buf.toString();
                buf = new StringBuffer(t.substring(t.length() - 2000));
            }
            if (System.currentTimeMillis() - last > 250) {
                flush();
            }
        }

        public String input(String prompt) {
            synchronized (lock) {
                inDone = false;
                inVal = null;
                flush();
                inputBox = new TextBox(prompt.length() == 0 ? "giri\u015f" : trunc(prompt, 24), "",
                        100, TextField.ANY);
                inputBox.addCommand(cOk);
                inputBox.addCommand(cStop);
                inputBox.setCommandListener(PyEdit.this);
                display.setCurrent(inputBox);
                while (!inDone && !stopped) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                    }
                }
                return stopped ? null : inVal;
            }
        }

        void provide(String s) {
            synchronized (lock) {
                inVal = s;
                inDone = true;
                lock.notifyAll();
            }
        }

        void stop() {
            stopped = true;
            interp.stopped = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    // ------------------------------------------------------------ storage (RMS)

    private void close(RecordStore rs) {
        try {
            if (rs != null) {
                rs.closeRecordStore();
            }
        } catch (Exception e) {
        }
    }

    private int findId(RecordStore rs, String name) throws Exception {
        RecordEnumeration en = rs.enumerateRecords(null, null, false);
        try {
            while (en.hasNextElement()) {
                int id = en.nextRecordId();
                String s = new String(rs.getRecord(id), "UTF-8");
                int nl = s.indexOf('\n');
                if (nl >= 0 && s.substring(0, nl).equals(name)) {
                    return id;
                }
            }
        } finally {
            en.destroy();
        }
        return -1;
    }

    private Vector names() {
        Vector v = new Vector();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE, true);
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            try {
                while (en.hasNextElement()) {
                    String s = new String(rs.getRecord(en.nextRecordId()), "UTF-8");
                    int nl = s.indexOf('\n');
                    if (nl >= 0) {
                        v.addElement(s.substring(0, nl));
                    }
                }
            } finally {
                en.destroy();
            }
        } catch (Exception e) {
        } finally {
            close(rs);
        }
        return v;
    }

    private String loadFile(String name) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE, true);
            int id = findId(rs, name);
            if (id < 0) {
                return null;
            }
            String s = new String(rs.getRecord(id), "UTF-8");
            return s.substring(s.indexOf('\n') + 1);
        } catch (Exception e) {
            return null;
        } finally {
            close(rs);
        }
    }

    private boolean saveFile(String name, String text) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE, true);
            byte[] b = (name + "\n" + text).getBytes("UTF-8");
            int id = findId(rs, name);
            if (id < 0) {
                rs.addRecord(b, 0, b.length);
            } else {
                rs.setRecord(id, b, 0, b.length);
            }
            return true;
        } catch (Exception e) {
            Alert a = new Alert("Kaydedilemedi", String.valueOf(e), null, AlertType.ERROR);
            a.setTimeout(Alert.FOREVER);
            display.setCurrent(a, cur());
            return false;
        } finally {
            close(rs);
        }
    }

    private boolean saveCurrent() {
        if (curName == null || editor == null) {
            return true;
        }
        return saveFile(curName, edGet());
    }

    private void deleteFile(String name) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE, true);
            int id = findId(rs, name);
            if (id >= 0) {
                rs.deleteRecord(id);
            }
        } catch (Exception e) {
        } finally {
            close(rs);
        }
    }

    private void loadSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(SETSTORE, true);
            if (rs.getNumRecords() > 0) {
                RecordEnumeration en = rs.enumerateRecords(null, null, false);
                try {
                    byte[] b = rs.getRecord(en.nextRecordId());
                    if (b.length >= 5) {
                        autoSave = b[0] == 1;
                        theme = b[1];
                        useCanvas = b[2] == 1;
                        caseMode = b[3];
                        fontSize = b[4];
                        if (theme < 0 || theme >= PyTheme.NAMES.length) {
                            theme = 0;
                        }
                        if (caseMode != 1) {
                            caseMode = 0;
                        }
                        if (fontSize != 1) {
                            fontSize = 0;
                        }
                    }
                } finally {
                    en.destroy();
                }
            }
        } catch (Exception e) {
        } finally {
            close(rs);
        }
    }

    private void saveSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(SETSTORE, true);
            byte[] b = new byte[] {(byte) (autoSave ? 1 : 0), (byte) theme, (byte) (useCanvas ? 1 : 0),
                (byte) caseMode, (byte) fontSize};
            if (rs.getNumRecords() == 0) {
                rs.addRecord(b, 0, b.length);
            } else {
                RecordEnumeration en = rs.enumerateRecords(null, null, false);
                try {
                    rs.setRecord(en.nextRecordId(), b, 0, b.length);
                } finally {
                    en.destroy();
                }
            }
        } catch (Exception e) {
        } finally {
            close(rs);
        }
    }
}
