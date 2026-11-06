package textlens.model;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Holds the combined findings for one analyzed text, and where the text
 * came from (a file path or "Pasted text").
 * It is Serializable so a result can be saved to a file and loaded back later.
 */
public class AnalysisResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String source;
    private final int wordCount;
    private final List<Map.Entry<String, Integer>> topWords;
    private final List<String> misspelled;
    private final Sentiment sentiment;

    /**
     * Creates a result. The lists are copied so later changes to the
     * originals cannot affect this result (and so it can be serialized).
     */
    public AnalysisResult(String source,
                          int wordCount,
                          List<Map.Entry<String, Integer>> topWords,
                          List<String> misspelled,
                          Sentiment sentiment) {
        this.source = source;
        this.wordCount = wordCount;
        this.topWords = new ArrayList<>();
        for (Map.Entry<String, Integer> e : topWords) {
            this.topWords.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
        }
        this.misspelled = new ArrayList<>(misspelled);
        this.sentiment = sentiment;
    }

    public String getSource() { return source; }

    public int getWordCount() { return wordCount; }

    public List<Map.Entry<String, Integer>> getTopWords() { return topWords; }

    public List<String> getMisspelled() { return misspelled; }

    public Sentiment getSentiment() { return sentiment; }

    /** @return a readable multi-line summary of all findings */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(source).append("\n");
        sb.append("Word count: ").append(wordCount).append("\n");
        sb.append("Top words:\n");
        for (Map.Entry<String, Integer> e : topWords) {
            sb.append("  ").append(e.getKey()).append(" - ").append(e.getValue()).append("\n");
        }
        sb.append("Possible spelling mistakes: ")
          .append(misspelled.isEmpty() ? "none" : String.join(", ", misspelled)).append("\n");
        sb.append("Sentiment: ").append(sentiment);
        return sb.toString();
    }
}