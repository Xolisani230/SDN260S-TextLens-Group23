package textlens.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import textlens.model.TextDocument;

/**
 * Finds words that are not in the dictionary and suggests corrections.
 *
 * Data structure: the dictionary is a HashSet, so checking one word is
 * O(1) on average, and checking a whole document is O(n).
 *
 * Algorithm: suggestions use the edit distance (Damerau-Levenshtein), the
 * smallest number of single changes (insert, delete, replace, or swap two
 * neighbouring letters) needed to turn one word into another.
 */
public class SpellChecker implements Analyzer<List<String>> {

    // Suggestions further away than this many changes are not offered.
    private static final int MAX_DISTANCE = 2;

    private final Set<String> dict;

    /** @param dictionary the set of correctly spelled lower-case words */
    public SpellChecker(Set<String> dictionary) {
        this.dict = dictionary;
    }

    @Override
    public List<String> analyze(TextDocument doc) {
        return findErrors(doc);
    }

    /**
     * Finds the unknown words in a document.
     *
     * @return each misspelled word once, in the order it first appears
     */
    public List<String> findErrors(TextDocument doc) {
        Set<String> errors = new LinkedHashSet<>();
        for (String word : doc.getTokens()) {
            if (!isCorrect(word)) {
                errors.add(word);
            }
        }
        return new ArrayList<>(errors);
    }

    /** @return true if the word (or each part of it) is in the dictionary */
    public boolean isCorrect(String word) {
        String w = word.replace('\u2019', '\'');
        if (dict.contains(w)) {
            return true;
        }
        // Hyphenated words such as "well-known": every part must be correct.
        if (w.contains("-")) {
            for (String part : w.split("-")) {
                if (!part.isEmpty() && !isCorrect(part)) {
                    return false;
                }
            }
            return true;
        }
        // Contractions such as "don't" or "student's".
        if (w.contains("'")) {
            if (w.equals("can't") || w.equals("won't") || w.equals("shan't")) {
                return true;
            }
            String base = w.endsWith("n't")
                    ? w.substring(0, w.length() - 3)
                    : w.substring(0, w.indexOf('\''));
            return dict.contains(base);
        }
        return false;
    }

    /**
     * Suggests the closest dictionary word.
     *
     * @return the best match, or an empty string if nothing is close enough
     */
    public String suggest(String word) {
        String target = word.toLowerCase();
        String best = "";
        int bestDistance = MAX_DISTANCE;
        for (String candidate : dict) {
            // Words whose lengths differ too much cannot be close enough.
            if (Math.abs(candidate.length() - target.length()) > bestDistance) {
                continue;
            }
            int d = editDistance(target, candidate);
            boolean closer = d < bestDistance;
            boolean sameButEarlier = d == bestDistance
                    && (best.isEmpty() || candidate.compareTo(best) < 0);
            if (closer || sameButEarlier) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    // Edit distance with dynamic programming. Each cell d[i][j] holds the
    // smallest number of changes needed to turn the first i letters of a
    // into the first j letters of b. Swapping two neighbouring letters
    // ("ie" -> "ei") counts as ONE change, because that is a very common typo.
    // Time O(a.length * b.length).
    private int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;                           // delete all letters of a
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;                           // insert all letters of b
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1,        // delete
                                            d[i][j - 1] + 1),       // insert
                                   d[i - 1][j - 1] + cost);         // replace
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);   // swap
                }
            }
        }
        return d[a.length()][b.length()];
    }
}