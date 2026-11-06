package textlens.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import textlens.analysis.FrequencyAnalyzer;
import textlens.analysis.SentimentAnalyzer;
import textlens.analysis.SpellChecker;
import textlens.analysis.WordCounter;
import textlens.input.BatchProcessor;
import textlens.input.FileHandler;
import textlens.model.AnalysisResult;
import textlens.model.Sentiment;
import textlens.model.TextDocument;
import textlens.output.ActivityLogger;
import textlens.output.ReportGenerator;
import textlens.util.WordListLoader;

/**
 * The "head chef" of TextLens. The user interface asks the controller to
 * analyze a file, a folder or some text; the controller loads the text, hands
 * the same TextDocument to every analyzer, and combines their answers into one
 * AnalysisResult. Folders are analyzed in parallel on several threads. The
 * controller also remembers the latest results so they can be saved as a
 * report or as a reloadable session. The interface never talks to the
 * analyzers directly.
 */
public class AnalyzerController {

    private static final String DICTIONARY_PATH = "data/dictionary.txt";
    private static final String POSITIVE_PATH = "data/positive.txt";
    private static final String NEGATIVE_PATH = "data/negative.txt";
    private static final String REPORTS_FOLDER = "reports/";
    private static final String SAVED_FOLDER = "saved/";
    private static final int TOP_WORDS = 20;
    private static final int SPEED_TEST_RUNS = 3;

    /** The timings from a sequential-versus-parallel speed test. */
    public static class SpeedResult {
        private final int files;
        private final int threads;
        private final long sequentialMs;
        private final long parallelMs;

        public SpeedResult(int files, int threads, long sequentialMs, long parallelMs) {
            this.files = files;
            this.threads = threads;
            this.sequentialMs = sequentialMs;
            this.parallelMs = parallelMs;
        }

        public int getFiles() { return files; }

        public int getThreads() { return threads; }

        public long getSequentialMs() { return sequentialMs; }

        public long getParallelMs() { return parallelMs; }

        /** @return how many times faster the parallel run was (below 1 means slower) */
        public double getSpeedUp() {
            return (double) sequentialMs / Math.max(1, parallelMs);
        }
    }

    private final FileHandler fileHandler = new FileHandler();
    private final BatchProcessor batchProcessor;
    private final ActivityLogger logger;
    private final WordCounter wordCounter = new WordCounter();
    private final FrequencyAnalyzer frequencyAnalyzer = new FrequencyAnalyzer(TOP_WORDS);
    private final SpellChecker spellChecker;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final ReportGenerator reportGenerator;

    // The results of the most recent analysis (or loaded session).
    private List<AnalysisResult> lastResults = new ArrayList<>();

    /**
     * Creates the controller and loads the word lists from the data folder.
     *
     * @param logger records every action and error
     * @throws IOException if a word list file is missing or unreadable
     */
    public AnalyzerController(ActivityLogger logger) throws IOException {
        this.logger = logger;
        this.batchProcessor = new BatchProcessor(logger);
        this.spellChecker = new SpellChecker(WordListLoader.load(DICTIONARY_PATH));
        this.sentimentAnalyzer = new SentimentAnalyzer(
                WordListLoader.load(POSITIVE_PATH),
                WordListLoader.load(NEGATIVE_PATH));
        this.reportGenerator = new ReportGenerator(spellChecker::suggest);
        logger.info("Word lists loaded");
    }

    /**
     * Analyzes a text file.
     *
     * @param path the path of the file
     * @return the combined findings
     * @throws IOException if the file cannot be read
     */
    public AnalysisResult analyzeFile(String path) throws IOException {
        AnalysisResult result = process(path, true);
        lastResults = new ArrayList<>(List.of(result));
        return result;
    }

    /**
     * Analyzes every .txt file in a folder and its sub-folders, using
     * several threads at once.
     *
     * @param dir the folder path
     * @return one result per file that could be read (possibly empty)
     * @throws IOException if the folder does not exist or is not a folder
     */
    public List<AnalysisResult> analyzeFolder(String dir) throws IOException {
        List<File> files = batchProcessor.findTextFiles(dir);
        int threads = threadCount(files.size());
        logger.info("Found " + files.size() + " text file(s) in " + dir
                + "; using " + threads + " thread(s)");
        List<AnalysisResult> results =
                batchProcessor.processAllParallel(files, path -> process(path, true), threads);
        logger.info("Batch finished: " + results.size() + " of " + files.size()
                + " files analyzed");
        lastResults = new ArrayList<>(results);
        return results;
    }

    /** Analyzes text that the user typed or pasted. */
    public AnalysisResult analyzeText(String text) {
        logger.info("Analyzing pasted text");
        AnalysisResult result = analyze("Pasted text", new TextDocument(text), true);
        lastResults = new ArrayList<>(List.of(result));
        return result;
    }

    /**
     * Times the same folder analysis done one file at a time and in parallel,
     * so the two can be compared. One warm-up run is done first, then each
     * way is timed several times and averaged. The remembered results are
     * not changed.
     *
     * @throws IOException if the folder is invalid or has no .txt files
     */
    public SpeedResult compareSpeed(String dir) throws IOException {
        List<File> files = batchProcessor.findTextFiles(dir);
        if (files.isEmpty()) {
            throw new IOException("There are no .txt files in that folder to time.");
        }
        int threads = threadCount(files.size());
        BatchProcessor.FileTask quiet = path -> process(path, false);

        // Warm-up, so Java's start-up work does not unfairly slow the first run.
        batchProcessor.processAll(files, quiet);
        batchProcessor.processAllParallel(files, quiet, threads);

        long sequentialTotal = 0;
        long parallelTotal = 0;
        for (int run = 0; run < SPEED_TEST_RUNS; run++) {
            long start = System.nanoTime();
            batchProcessor.processAll(files, quiet);
            sequentialTotal += (System.nanoTime() - start) / 1_000_000;

            start = System.nanoTime();
            batchProcessor.processAllParallel(files, quiet, threads);
            parallelTotal += (System.nanoTime() - start) / 1_000_000;
        }
        SpeedResult result = new SpeedResult(files.size(), threads,
                sequentialTotal / SPEED_TEST_RUNS, parallelTotal / SPEED_TEST_RUNS);
        logger.info("Speed test on " + dir + ": " + result.getFiles() + " files, "
                + result.getThreads() + " threads, sequential " + result.getSequentialMs()
                + " ms, parallel " + result.getParallelMs() + " ms");
        return result;
    }

    /** @return the closest dictionary word, or an empty string if none is close */
    public String suggest(String word) {
        return spellChecker.suggest(word);
    }

    /**
     * Saves the latest results as a text report and a CSV file in the
     * reports folder.
     *
     * @param name the file name to use (letters, digits, - and _ only)
     * @return the path without extension, for example "reports/myreport"
     * @throws IOException if there is nothing to save or the files cannot be written
     */
    public String saveReport(String name) throws IOException {
        requireResults();
        String base = REPORTS_FOLDER + safeName(name);
        reportGenerator.saveReport(lastResults, base + ".txt");
        reportGenerator.saveCsv(lastResults, base + ".csv");
        logger.info("Report saved: " + base + ".txt and " + base + ".csv");
        return base;
    }

    /**
     * Saves the latest results so they can be opened again later.
     *
     * @return the path of the saved file
     * @throws IOException if there is nothing to save or the file cannot be written
     */
    public String saveSession(String name) throws IOException {
        requireResults();
        String path = SAVED_FOLDER + safeName(name) + ".ser";
        fileHandler.saveResults(lastResults, path);
        logger.info("Session saved: " + path);
        return path;
    }

    /**
     * Loads a session saved earlier. The loaded results become the latest
     * results, so a report can be made from them.
     *
     * @throws IOException if the session does not exist or is not valid
     */
    public List<AnalysisResult> loadSession(String name) throws IOException {
        String path = SAVED_FOLDER + safeName(name) + ".ser";
        List<AnalysisResult> loaded = fileHandler.loadResults(path);
        lastResults = new ArrayList<>(loaded);
        logger.info("Session loaded: " + path + " (" + loaded.size() + " result(s))");
        return loaded;
    }

    // Reads and analyzes one file without changing the remembered results,
    // so it can be used by the folder batch (including from several threads).
    private AnalysisResult process(String path, boolean log) throws IOException {
        TextDocument doc = fileHandler.readText(path);
        if (log) {
            logger.info("Loaded file: " + path);
        }
        return analyze(path.trim().replace("\"", ""), doc, log);
    }

    // Runs every analyzer on the same document and combines the answers.
    private AnalysisResult analyze(String source, TextDocument doc, boolean log) {
        int words = wordCounter.analyze(doc);
        List<Map.Entry<String, Integer>> top = frequencyAnalyzer.analyze(doc);
        List<String> misspelled = spellChecker.analyze(doc);
        Sentiment sentiment = sentimentAnalyzer.analyze(doc);
        if (log) {
            logger.info("Analyzed " + words + " words, " + misspelled.size()
                    + " possible spelling mistakes, sentiment " + sentiment);
        }
        return new AnalysisResult(source, words, top, misspelled, sentiment);
    }

    // One thread per processor, but never more threads than files.
    private int threadCount(int fileCount) {
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), fileCount));
    }

    private void requireResults() throws IOException {
        if (lastResults.isEmpty()) {
            throw new IOException("There is no analysis to save yet. "
                    + "Analyze a file, some text or a folder first.");
        }
    }

    // Keeps only letters, digits, - and _ so a name can never point to
    // another folder.
    private String safeName(String name) {
        String cleaned = (name == null) ? "" : name.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return cleaned.isEmpty() ? "analysis" : cleaned;
    }
}