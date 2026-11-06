package textlens.output;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import textlens.model.AnalysisResult;
import textlens.model.Sentiment;

/**
 * Turns analysis results into a readable text report and a CSV summary,
 * and saves them to files.
 */
public class ReportGenerator {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LINE = "----------------------------------------";

    // Looks up a suggested correction for a misspelled word.
    private final Function<String, String> suggester;

    /**
     * @param suggester a function that returns the suggested correction for
     *                  a word (or an empty string if there is none)
     */
    public ReportGenerator(Function<String, String> suggester) {
        this.suggester = suggester;
    }

    /** @return the full text report for one or more results */
    public String toText(List<AnalysisResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("TextLens Report\n");
        sb.append("Generated: ").append(LocalDateTime.now().format(FORMAT)).append("\n");
        sb.append("Texts analyzed: ").append(results.size()).append("\n");
        sb.append("========================================\n");

        for (AnalysisResult r : results) {
            sb.append("Source: ").append(r.getSource()).append("\n");
            sb.append("Word count: ").append(r.getWordCount()).append("\n");
            sb.append("Top words:\n");
            for (Map.Entry<String, Integer> e : r.getTopWords()) {
                sb.append("  ").append(e.getKey()).append(" - ").append(e.getValue()).append("\n");
            }
            sb.append("Possible spelling mistakes: ").append(r.getMisspelled().size()).append("\n");
            for (String word : r.getMisspelled()) {
                String suggestion = suggester.apply(word);
                sb.append("  ").append(word).append(" -> ")
                  .append(suggestion.isEmpty() ? "(no suggestion)" : suggestion).append("\n");
            }
            sb.append("Sentiment: ").append(r.getSentiment()).append("\n");
            sb.append(LINE).append("\n");
        }

        // Totals, calculated with streams.
        int totalWords = results.stream().mapToInt(AnalysisResult::getWordCount).sum();
        int totalMistakes = results.stream().mapToInt(r -> r.getMisspelled().size()).sum();
        long positive = results.stream().filter(r -> r.getSentiment() == Sentiment.POSITIVE).count();
        long neutral = results.stream().filter(r -> r.getSentiment() == Sentiment.NEUTRAL).count();
        long negative = results.stream().filter(r -> r.getSentiment() == Sentiment.NEGATIVE).count();
        sb.append("Summary\n");
        sb.append("  Total words: ").append(totalWords).append("\n");
        sb.append("  Total possible spelling mistakes: ").append(totalMistakes).append("\n");
        sb.append("  Sentiment: ").append(positive).append(" positive, ")
          .append(neutral).append(" neutral, ").append(negative).append(" negative\n");
        return sb.toString();
    }

    /** @return a CSV summary with one row per result */
    public String toCsv(List<AnalysisResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("source,word_count,spelling_mistakes,sentiment,top_words\n");
        for (AnalysisResult r : results) {
            StringBuilder top = new StringBuilder();
            for (Map.Entry<String, Integer> e : r.getTopWords()) {
                if (top.length() > 0) {
                    top.append("; ");
                }
                top.append(e.getKey()).append(" (").append(e.getValue()).append(")");
            }
            sb.append(csv(r.getSource())).append(",")
              .append(r.getWordCount()).append(",")
              .append(r.getMisspelled().size()).append(",")
              .append(r.getSentiment()).append(",")
              .append(csv(top.toString())).append("\n");
        }
        return sb.toString();
    }

    /** Saves the text report to a file. */
    public void saveReport(List<AnalysisResult> results, String path) throws IOException {
        write(path, toText(results));
    }

    /** Saves the CSV summary to a file. */
    public void saveCsv(List<AnalysisResult> results, String path) throws IOException {
        write(path, toCsv(results));
    }

    // Wraps a value in quotes (doubling any quotes inside) so commas are safe.
    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // Writes text to a file, creating the folder first if needed.
    private void write(String path, String content) throws IOException {
        Path file = Paths.get(path);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }
}