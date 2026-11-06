package textlens.input;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import textlens.model.AnalysisResult;
import textlens.output.ActivityLogger;

/**
 * Finds all the .txt files inside a folder (and its sub-folders) and
 * analyzes them, either one after another or in parallel on several threads.
 */
public class BatchProcessor {

    /**
     * A job that analyzes one file. The controller supplies it, so this class
     * does not need to know about the controller.
     */
    public interface FileTask {
        AnalysisResult run(String path) throws IOException;
    }

    private final ActivityLogger logger;

    public BatchProcessor(ActivityLogger logger) {
        this.logger = logger;
    }

    /**
     * Finds every .txt file in a folder and all of its sub-folders.
     *
     * @param dir the folder path; surrounding quotes and spaces are removed
     * @return the files, in path order (empty if there are none)
     * @throws IOException if the folder does not exist or is not a folder
     */
    public List<File> findTextFiles(String dir) throws IOException {
        if (dir == null || dir.trim().isEmpty()) {
            throw new IOException("No folder path was given.");
        }
        File root = new File(dir.trim().replace("\"", ""));
        if (!root.exists()) {
            throw new IOException("Folder not found: " + root.getPath());
        }
        if (!root.isDirectory()) {
            throw new IOException("That is a file, not a folder: " + root.getPath());
        }
        List<File> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    // Recursion: a folder's contents are files and more folders. For each
    // sub-folder this method calls itself, so any depth of nesting is handled.
    // The base case is a folder with no sub-folders left to open.
    private void collect(File folder, List<File> found) {
        File[] items = folder.listFiles();
        if (items == null) {
            logger.info("Skipped unreadable folder: " + folder.getPath());
            return;
        }
        Arrays.sort(items);
        for (File item : items) {
            if (item.isDirectory()) {
                collect(item, found);
            } else if (FileHandler.isSupported(item.getName())) {
                found.add(item);
            }
        }
    }

    /**
     * Analyzes each file in turn. A file that cannot be read is logged and
     * skipped, so one bad file does not stop the whole batch.
     *
     * @param files the files to analyze
     * @param task  the job that analyzes one file
     * @return the results of the files that were analyzed successfully
     */
    public List<AnalysisResult> processAll(List<File> files, FileTask task) {
        List<AnalysisResult> results = new ArrayList<>();
        for (File file : files) {
            try {
                results.add(task.run(file.getPath()));
            } catch (IOException e) {
                logger.error("Skipped file: " + file.getPath(), e);
            }
        }
        return results;
    }

    /**
     * Analyzes the files in parallel using a pool of threads. The results
     * come back in the same order as the files, and a file that cannot be
     * read is logged and skipped, exactly as in processAll.
     *
     * @param files   the files to analyze
     * @param task    the job that analyzes one file
     * @param threads how many threads to use (at least 1)
     * @return the results of the files that were analyzed successfully
     */
    public List<AnalysisResult> processAllParallel(List<File> files, FileTask task, int threads) {
        List<AnalysisResult> results = new ArrayList<>();
        if (files.isEmpty()) {
            return results;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
        try {
            // Hand every file to the pool; each job runs on any free thread.
            List<Future<AnalysisResult>> futures = new ArrayList<>();
            for (File file : files) {
                Callable<AnalysisResult> job = () -> task.run(file.getPath());
                futures.add(pool.submit(job));
            }
            // Collect the answers in the original order.
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (ExecutionException e) {
                    logger.error("Skipped file: " + files.get(i).getPath(),
                            e.getCause() instanceof Exception
                                    ? (Exception) e.getCause() : e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();   // keep the "stop" signal
                    logger.error("Batch interrupted", e);
                    break;
                }
            }
        } finally {
            pool.shutdown();                              // always release the threads
        }
        return results;
    }
}