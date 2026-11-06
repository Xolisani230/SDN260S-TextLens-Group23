package textlens.ui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import textlens.controller.AnalyzerController;
import textlens.model.AnalysisResult;
import textlens.output.ActivityLogger;

/**
 * The console menu. It only handles input and output: all analysis is done
 * by the AnalyzerController.
 */
public class MainCLI {

    private final Scanner scanner = new Scanner(System.in);
    private final ActivityLogger logger = new ActivityLogger();
    private AnalyzerController controller;

    public static void main(String[] args) {
        new MainCLI().run();
    }

    /** Starts the controller, then keeps showing the menu until the user exits. */
    private void run() {
        logger.info("TextLens started");
        try {
            controller = new AnalyzerController(logger);
        } catch (IOException e) {
            System.out.println("Could not load the word lists: " + e.getMessage());
            System.out.println("Check that data/dictionary.txt, data/positive.txt and "
                    + "data/negative.txt exist.");
            logger.error("Could not load word lists", e);
            return;
        }
        boolean running = true;
        while (running && scanner.hasNextLine()) {
            showMenu();
            String choice = scanner.nextLine().trim();
            running = handleChoice(choice);
        }
        logger.info("TextLens closed");
        System.out.println("Goodbye!");
    }

    private void showMenu() {
        System.out.println();
        System.out.println("===== TextLens =====");
        System.out.println("1. Analyze a text file");
        System.out.println("2. Analyze pasted text");
        System.out.println("3. Analyze a folder (all .txt files inside)");
        System.out.println("4. Save the last analysis as a report");
        System.out.println("5. Save the last analysis to reopen later");
        System.out.println("6. Open a saved analysis");
        System.out.println("7. Compare speed: one thread vs several threads");
        System.out.println("0. Exit");
        System.out.print("Choose an option: ");
    }

    /** @return false when the program should stop */
    private boolean handleChoice(String choice) {
        switch (choice) {
            case "1":
                analyzeFile();
                return true;
            case "2":
                analyzePastedText();
                return true;
            case "3":
                analyzeFolder();
                return true;
            case "4":
                saveReport();
                return true;
            case "5":
                saveSession();
                return true;
            case "6":
                openSession();
                return true;
            case "7":
                compareSpeed();
                return true;
            case "0":
                return false;
            default:
                System.out.println("Invalid choice. Please enter a number from the menu.");
                return true;
        }
    }

    private void analyzeFile() {
        System.out.print("Enter the path of a text file: ");
        String path = scanner.hasNextLine() ? scanner.nextLine() : "";
        try {
            printResult(controller.analyzeFile(path));
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            logger.error("Could not load file: " + path, e);
        }
    }

    private void analyzeFolder() {
        System.out.print("Enter the path of a folder: ");
        String path = scanner.hasNextLine() ? scanner.nextLine() : "";
        try {
            List<AnalysisResult> results = controller.analyzeFolder(path);
            if (results.isEmpty()) {
                System.out.println("No .txt files were analyzed in that folder.");
                return;
            }
            for (AnalysisResult result : results) {
                System.out.println();
                System.out.println("----------------------------------------");
                printResult(result);
            }
            System.out.println();
            System.out.println("Analyzed " + results.size() + " file(s). Any file that could "
                    + "not be read is listed in logs/activity.log.");
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            logger.error("Could not analyze folder: " + path, e);
        }
    }

    private void compareSpeed() {
        System.out.print("Enter the path of a folder to time: ");
        String path = scanner.hasNextLine() ? scanner.nextLine() : "";
        try {
            System.out.println("Timing (this may take a few seconds)...");
            AnalyzerController.SpeedResult speed = controller.compareSpeed(path);
            System.out.println("Files analyzed: " + speed.getFiles());
            System.out.println("Threads used:   " + speed.getThreads());
            System.out.println("One thread:     " + speed.getSequentialMs() + " ms");
            System.out.println("Several threads: " + speed.getParallelMs() + " ms");
            if (speed.getSpeedUp() >= 1.0) {
                System.out.printf("Several threads were %.1f times faster.%n", speed.getSpeedUp());
            } else {
                System.out.println("One thread was faster here: the files are too small to "
                        + "repay the cost of starting threads.");
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            logger.error("Could not time folder: " + path, e);
        }
    }

    private void saveReport() {
        String name = askName("Report name (press Enter for \"report\"): ", "report");
        try {
            String base = controller.saveReport(name);
            System.out.println("Report saved: " + base + ".txt and " + base + ".csv");
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            logger.error("Could not save report", e);
        }
    }

    private void saveSession() {
        String name = askName("Name for this analysis (press Enter for \"session\"): ", "session");
        try {
            System.out.println("Saved: " + controller.saveSession(name));
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            logger.error("Could not save session", e);
        }
    }

    private void openSession() {
        String name = askName("Name of the saved analysis (press Enter for \"session\"): ", "session");
        try {
            List<AnalysisResult> results = controller.loadSession(name);
            for (AnalysisResult result : results) {
                System.out.println();
                System.out.println("----------------------------------------");
                printResult(result);
            }
            System.out.println();
            System.out.println("Opened " + results.size() + " result(s).");
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            logger.error("Could not open saved analysis", e);
        }
    }

    // Asks for a name and falls back to a default if the user just presses Enter.
    private String askName(String prompt, String defaultName) {
        System.out.print(prompt);
        String name = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        return name.isEmpty() ? defaultName : name;
    }

    private void analyzePastedText() {
        System.out.println("Type or paste your text. Finish with an empty line:");
        StringBuilder text = new StringBuilder();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().isEmpty()) {
                break;
            }
            text.append(line).append("\n");
        }
        if (text.length() == 0) {
            System.out.println("No text was entered.");
            return;
        }
        printResult(controller.analyzeText(text.toString()));
    }

    /** Prints every part of a result, with a suggestion for each misspelling. */
    private void printResult(AnalysisResult result) {
        System.out.println("Source: " + result.getSource());
        System.out.println("Word count: " + result.getWordCount());
        System.out.println("Top words:");
        for (Map.Entry<String, Integer> entry : result.getTopWords()) {
            System.out.println("  " + entry.getKey() + " - " + entry.getValue());
        }
        List<String> misspelled = result.getMisspelled();
        System.out.println("Possible spelling mistakes: " + misspelled.size());
        for (String word : misspelled) {
            String suggestion = controller.suggest(word);
            System.out.println("  " + word + " -> "
                    + (suggestion.isEmpty() ? "(no suggestion)" : suggestion));
        }
        System.out.println("Sentiment: " + result.getSentiment());
    }
}