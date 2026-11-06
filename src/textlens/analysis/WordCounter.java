package textlens.analysis;

import textlens.model.TextDocument;

/**
 * Counts the total number of words in a document.
 * The words were already found by the Tokenizer, so this simply reports
 * how many there are.
 */
public class WordCounter implements Analyzer<Integer> {

    /**
     * @param doc the text to analyze
     * @return the number of words in the document
     */
    @Override
    public Integer analyze(TextDocument doc) {
        return doc.getWordCount();
    }
}