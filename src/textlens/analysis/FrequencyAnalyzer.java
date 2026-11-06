package textlens.analysis;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import textlens.model.TextDocument;

/**
 * Finds the most frequent words in a document.
 *
 * Data structure: a HashMap stores each word with its count. Adding or
 * updating a word takes O(1) on average, so counting n words is O(n).
 *
 * Algorithm: the words are then ranked with a recursive merge sort,
 * which takes O(m log m) where m is the number of different words.
 *
 * Thread safety: this class keeps no data between calls (the HashMap is
 * created inside each call), so one instance can safely be shared by
 * several threads.
 */
public class FrequencyAnalyzer implements Analyzer<List<Map.Entry<String, Integer>>> {

    private final int defaultN;

    /** Creates an analyzer that reports the 10 most frequent words. */
    public FrequencyAnalyzer() {
        this(10);
    }

    /** Creates an analyzer that reports the n most frequent words. */
    public FrequencyAnalyzer(int n) {
        this.defaultN = n;
    }

    /** Counts the words, then returns the most frequent ones. */
    @Override
    public List<Map.Entry<String, Integer>> analyze(TextDocument doc) {
        return topN(countWords(doc), defaultN);
    }

    /** Builds a new word-to-count map for a document. */
    public Map<String, Integer> countWords(TextDocument doc) {
        Map<String, Integer> freq = new HashMap<>();
        for (String word : doc.getTokens()) {
            freq.merge(word, 1, Integer::sum);
        }
        return freq;
    }

    /**
     * Returns the n most frequent words from a word-to-count map,
     * highest count first. Words with the same count are listed
     * alphabetically.
     */
    public List<Map.Entry<String, Integer>> topN(Map<String, Integer> freq, int n) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            entries.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
        }
        List<Map.Entry<String, Integer>> sorted = mergeSort(entries);
        int limit = Math.max(0, Math.min(n, sorted.size()));
        return new ArrayList<>(sorted.subList(0, limit));
    }

    // Recursive merge sort: split the list in half, sort each half
    // (by calling itself), then merge the two sorted halves.
    private List<Map.Entry<String, Integer>> mergeSort(List<Map.Entry<String, Integer>> list) {
        if (list.size() <= 1) {
            return list;                        // base case: already sorted
        }
        int mid = list.size() / 2;
        List<Map.Entry<String, Integer>> left =
                mergeSort(new ArrayList<>(list.subList(0, mid)));
        List<Map.Entry<String, Integer>> right =
                mergeSort(new ArrayList<>(list.subList(mid, list.size())));
        return merge(left, right);
    }

    // Combines two sorted lists into one sorted list.
    private List<Map.Entry<String, Integer>> merge(List<Map.Entry<String, Integer>> left,
                                                   List<Map.Entry<String, Integer>> right) {
        List<Map.Entry<String, Integer>> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            if (comesBefore(left.get(i), right.get(j))) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }
        while (i < left.size()) {
            result.add(left.get(i++));
        }
        while (j < right.size()) {
            result.add(right.get(j++));
        }
        return result;
    }

    // Higher count first; if counts are equal, alphabetical order.
    private boolean comesBefore(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
        if (!a.getValue().equals(b.getValue())) {
            return a.getValue() > b.getValue();
        }
        return a.getKey().compareTo(b.getKey()) <= 0;
    }
}