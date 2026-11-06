package textlens.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads a word list (one word per line) from a file into a HashSet.
 * Used by the SpellChecker (dictionary) and the SentimentAnalyzer
 * (positive and negative word lists), so the loading code is written once.
 */
public class WordListLoader {

    /**
     * Reads a word list file.
     *
     * Words are trimmed and converted to lower case. Blank lines and lines
     * starting with # are ignored. A HashSet is used because checking
     * whether a word is in the list takes O(1) on average.
     *
     * @param path the path of the word list file
     * @return the set of words
     * @throws IOException if the file cannot be read
     */
    public static Set<String> load(String path) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        Set<String> words = new HashSet<>();
        for (String line : lines) {
            // Remove an invisible "BOM" mark that some Windows editors add.
            String word = line.replace("\uFEFF", "").trim().toLowerCase();
            if (!word.isEmpty() && !word.startsWith("#")) {
                words.add(word);
            }
        }
        return words;
    }
}
