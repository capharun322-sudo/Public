/** Input/output bridge between the interpreter and the UI. */
public interface PyIO {
    void print(String s);

    /** Returns the typed text, or null if the user stopped the program. */
    String input(String prompt);
}
