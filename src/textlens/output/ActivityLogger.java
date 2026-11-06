package textlens.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Records every action and error to a log file, one timestamped line each.
 * Logging must never crash the program, so file problems are only printed.
 */
public class ActivityLogger {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logFile;

    /** Creates a logger that writes to logs/activity.log. */
    public ActivityLogger() {
        this("logs/activity.log");
    }

    /** Creates a logger that writes to the given file path. */
    public ActivityLogger(String path) {
        this.logFile = Paths.get(path);
    }

    /** Records a normal event, for example "Loaded file". */
    public void info(String msg) {
        write("INFO", msg);
    }

    /** Records a problem, including the exception's type and message. */
    public void error(String msg, Exception ex) {
        String detail = (ex == null) ? ""
                : " (" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")";
        write("ERROR", msg + detail);
    }

    // synchronized so several threads can log safely later (multithreading phase)
    private synchronized void write(String level, String msg) {
        String line = LocalDateTime.now().format(FORMAT)
                + " [" + level + "] " + msg + System.lineSeparator();
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(logFile, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Could not write to log file: " + e.getMessage());
        }
    }
}