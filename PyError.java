public class PyError extends Exception {
    public int line;

    public PyError(int line, String msg) {
        super(msg);
        this.line = line;
    }
}
