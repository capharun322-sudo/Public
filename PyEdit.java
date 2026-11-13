import java.util.Vector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;

/**
 * PyPhone - a small Python editor + runner for J2ME phones.
 * Menus: File, Build, Goto, Chars, Settings.
 */
public class PyEdit extends MIDlet implements CommandListener {
    private static final String STORE = "pyfiles";
    private static final String SETSTORE = "pyset";
    private static final String HELLO = "print(\"Hello, World!\")\n";
    private static final int EDIT_MAX = 8000;

    private static final String[] CH_LABEL = {
        "TAB (4 spaces)", "(", ")", "[", "]", "{", "}", ":", "\"", "'", "=", "==", "!=",
        "<=", ">=", "<", ">", "+", "-", "*", "//", "%", "#", ",", ".", "_", "\\",
        "&", "|", "!", "@", ";", "~", "^", "$", "?",
        "print(\"\")", "input(\"\")", "for i in range(10):", "if x == 1:", "while True:"
    };
    private static final String[] CH_TEXT = {
        "    ", "(", ")", "[", "]", "{", "}", ":", "\"", "'", "=", "==", "!=",
        "<=", ">=", "<", ">", "+", "-", "*", "//", "%", "#", ",", ".", "_", "\\",
        "&", "|", "!", "@", ";", "~", "^", "$", "?",
        "print(\"\")", "input(\"\")", "for i in range(10):", "if x == 1:", "while True:"
    };

    private Display display;

    private Form home, outForm, settingsForm, helpForm;
    private StringItem outText;
    private ChoiceGroup optGroup;
    private TextBox editor, nameBox, lineBox, inputBox;
    private List fileMenu, openList, gotoList, charsList;

    private final Command cFile = new Command("File", Command.SCREEN, 1);
    private final Command cBuild = new Command("Build", Command.SCREEN, 2);
    private final Command cGoto = new Command("Goto", Command.SCREEN, 3);
    private final Command cChars = new Command("Chars", Command.SCREEN, 4);
    private final Command cSettings = new Command("Settings", Command.SCREEN, 5);
    private final Command cBack = new Command("Back", Command.BACK, 1);
    private final Command cOk = new Command("OK", Command.OK, 1);
    private final Command cDel = new Command("Delete", Command.SCREEN, 2);
    private final Command cStop = new Command("Stop", Command.STOP, 1);
    private final Command cExit = new Command("Exit", Command.EXIT, 1);
    private final Command cHelp = new Command("Help", Command.HELP, 3);
    private final Command cAddBelow = new Command("New line below", Command.SCREEN, 2);
    private final Command cDelLine = new Command("Delete line", Command.SCREEN, 3);

    private String curName = null;
    private boolean autoSave = true;
    private int nameMode = 0; // 0 = new file, 1 = save as
    private Vector openNames;
    private Vector gotoLines;
    private int gotoIdx;
    private Runner runner;

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

    private void buildScreens() {
        home = new Form("PyPhone");
        home.append("Open or create a file\nto get started.");
        home.addCommand(cFile);
        home.addCommand(cSettings);
        home.addCommand(cExit);
        home.setCommandListener(this);

        fileMenu = new List("File", Choice.IMPLICIT,
                new String[] {"New", "Open", "Save", "Save as...", "Close", "Exit"}, null);
        fileMenu.addCommand(cBack);
        fileMenu.setCommandListener(this);

        charsList = new List("Chars", Choice.IMPLICIT, CH_LABEL, null);
        charsList.addCommand(cBack);
        charsList.setCommandListener(this);

        outForm = new Form("Output");
        outText = new StringItem(null, "");
        outForm.append(outText);
        outForm.addCommand(cBack);
        outForm.addCommand(cStop);
        outForm.setCommandListener(this);

        settingsForm = new Form("Settings");
        optGroup = new ChoiceGroup("Options", Choice.MULTIPLE,
                new String[] {"Save file before Build"}, null);
        optGroup.setSelectedIndex(0, autoSave);
        settingsForm.append(optGroup);
        settingsForm.addCommand(cOk);
        settingsForm.addCommand(cHelp);
        settingsForm.addCommand(cBack);
        settingsForm.setCommandListener(this);

        helpForm = new Form("Help");
        helpForm.append("Python subset:\n"
                + "print() input() int() str() len() abs() min() max()\n"
                + "if / elif / else, while, for x in range(..), break, continue, pass\n"
                + "numbers are whole numbers, strings, f\"{x}\"\n"
                + "string methods: upper lower strip replace find startswith endswith isdigit\n"
                + "/ is whole-number division. No lists or def yet.\n\n"
                + "File: New/Open/Save. Build: run.\n"
                + "Goto: pick a line to edit, add or delete it.\n"
                + "Chars: insert symbols and snippets.");
        helpForm.addCommand(cBack);
        helpForm.setCommandListener(this);
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

    // ------------------------------------------------------------ commands

    public void commandAction(Command c, Displayable d) {
        if (d == home || d == editor) {
            if (c == cFile) {
                show(fileMenu);
            } else if (c == cBuild) {
                build();
            } else if (c == cGoto) {
                showGoto(0);
            } else if (c == cChars) {
                show(charsList);
            } else if (c == cSettings) {
                optGroup.setSelectedIndex(0, autoSave);
                show(settingsForm);
            } else if (c == cExit) {
                quit();
            }
        } else if (d == fileMenu) {
            if (c == cBack) {
                show(cur());
            } else if (c == List.SELECT_COMMAND) {
                fileAction(fileMenu.getSelectedIndex());
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
                editLine(gotoList.getSelectedIndex());
            }
        } else if (d == lineBox) {
            lineAction(c);
        } else if (d == charsList) {
            if (c == cBack) {
                show(editor);
            } else if (c == List.SELECT_COMMAND) {
                insertText(CH_TEXT[charsList.getSelectedIndex()]);
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
        } else if (d == settingsForm) {
            if (c == cOk) {
                autoSave = optGroup.isSelected(0);
                saveSettings();
                info("Settings saved", cur());
            } else if (c == cHelp) {
                show(helpForm);
            } else if (c == cBack) {
                show(cur());
            }
        } else if (d == helpForm) {
            show(settingsForm);
        }
    }

    // ------------------------------------------------------------ File menu

    private void fileAction(int idx) {
        if (idx == 0) {
            nameMode = 0;
            askName("New file name", "hello.py");
        } else if (idx == 1) {
            showOpen();
        } else if (idx == 2) {
            if (curName == null) {
                info("No file open", home);
            } else if (saveCurrent()) {
                info("Saved " + curName, editor);
            }
        } else if (idx == 3) {
            if (curName == null) {
                info("No file open", home);
            } else {
                nameMode = 1;
                askName("Save as", curName);
            }
        } else if (idx == 4) {
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

    private void nameDone() {
        String n = normName(nameBox.getString());
        if (n == null) {
            info("Invalid file name", nameBox);
            return;
        }
        if (nameMode == 0) {
            saveCurrent();
            String t = loadFile(n);
            if (t == null) {
                saveFile(n, HELLO);
            }
            openFile(n);
        } else {
            if (saveFile(n, editor.getString())) {
                curName = n;
                editor.setTitle(n);
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
            info("No files yet. Use New.", cur());
            return;
        }
        openNames = n;
        openList = new List("Open", Choice.IMPLICIT);
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
            info("Cannot open " + name, cur());
            return;
        }
        TextBox tb = new TextBox(name, "", EDIT_MAX, TextField.ANY | TextField.NON_PREDICTIVE);
        int m = tb.getMaxSize();
        boolean cut = false;
        if (text.length() > m) {
            text = text.substring(0, m);
            cut = true;
        }
        tb.setString(text);
        tb.addCommand(cFile);
        tb.addCommand(cBuild);
        tb.addCommand(cGoto);
        tb.addCommand(cChars);
        tb.addCommand(cSettings);
        tb.setCommandListener(this);
        editor = tb;
        curName = name;
        if (cut) {
            info("File was cut to " + m + " chars", editor);
        } else {
            show(editor);
        }
    }

    // ------------------------------------------------------------ Goto

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
        gotoLines = splitLines(editor.getString());
        gotoList = new List("Goto line", Choice.IMPLICIT);
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

    private void editLine(int idx) {
        gotoIdx = idx;
        lineBox = new TextBox("Line " + (idx + 1), (String) gotoLines.elementAt(idx), 200,
                TextField.ANY | TextField.NON_PREDICTIVE);
        lineBox.addCommand(cOk);
        lineBox.addCommand(cAddBelow);
        lineBox.addCommand(cDelLine);
        lineBox.addCommand(cBack);
        lineBox.setCommandListener(this);
        show(lineBox);
    }

    private void lineAction(Command c) {
        int sel = gotoIdx;
        if (c == cBack) {
            showGoto(gotoIdx);
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
        if (t.length() > editor.getMaxSize()) {
            info("Text too long", lineBox);
            return;
        }
        editor.setString(t);
        showGoto(sel);
    }

    // ------------------------------------------------------------ Chars

    private void insertText(String s) {
        try {
            editor.insert(s, editor.getCaretPosition());
        } catch (IllegalArgumentException e) {
            info("Editor is full", editor);
            return;
        }
        show(editor);
    }

    // ------------------------------------------------------------ Build / run

    private void build() {
        if (curName == null || editor == null) {
            info("No file open", home);
            return;
        }
        if (autoSave) {
            saveCurrent();
        }
        if (runner != null) {
            runner.stop();
        }
        runner = new Runner(editor.getString());
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
                finish("[Done]");
            } catch (PyError e) {
                if (stopped || e.line == 0) {
                    finish("[Stopped]");
                } else {
                    finish("Line " + e.line + ": " + e.getMessage());
                }
            } catch (Throwable t) {
                finish("Error: " + t);
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
                inputBox = new TextBox(prompt.length() == 0 ? "input" : trunc(prompt, 24), "",
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
            Alert a = new Alert("Save failed", String.valueOf(e), null, AlertType.ERROR);
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
        return saveFile(curName, editor.getString());
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
                    autoSave = b.length > 0 && b[0] == 1;
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
            byte[] b = new byte[] {(byte) (autoSave ? 1 : 0)};
            if (rs.getNumRecords() == 0) {
                rs.addRecord(b, 0, 1);
            } else {
                RecordEnumeration en = rs.enumerateRecords(null, null, false);
                try {
                    rs.setRecord(en.nextRecordId(), b, 0, 1);
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
