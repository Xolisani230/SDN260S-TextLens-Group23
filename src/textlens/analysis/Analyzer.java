package textlens.analysis;

import textlens.model.TextDocument;

/**
 * The common contract for every analysis (word count, top words, spelling,
 * sentiment). Each analyzer reads a TextDocument and returns its own kind
 * of result, which is why the result type T is generic.
 *
 * @param <T> the type of result this analyzer produces
 */
public interface Analyzer<T> {

    /**
     * Analyzes a document.
     *
     * @param doc the text to analyze
     * @return this analyzer's result
     */
    T analyze(TextDocument doc);
}