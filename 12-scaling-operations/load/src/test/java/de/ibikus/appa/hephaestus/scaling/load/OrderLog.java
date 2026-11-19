package de.ibikus.appa.hephaestus.scaling.load;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every accepted order, one line {@code orderId,style,expected}, for the reconciliation after the run
 * (scripts/reconcile.sh). Gatling calls it from many threads; a lost line would hide a lost order.
 */
public final class OrderLog implements AutoCloseable {

    private final BufferedWriter out;

    /** Creates (or replaces) the file. */
    public OrderLog(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized void append(String orderId, String style, String expected) {
        try {
            // \n, not newLine(): the same file on every platform (Windows would write \r\n)
            out.write(orderId + "," + style + "," + expected + "\n");
            // A crashed run still leaves every order accepted so far
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
