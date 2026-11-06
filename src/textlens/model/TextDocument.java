package textlens.model;

import java.util.Collections;
import java.util.List;
import textlens.util.Tokenizer;

/**
 * Holds one piece of text and its words. This is the "form" that every
 * Analyzer receives, so the text is tokenized once and shared.
 */
public class TextDocument {

    private final String content;
    private final List<String> tokens;

    /**
     * Creates a document and tokenizes its content straight away.
     *
     * @param content the raw text (null is treated as empty text)
     */
    public TextDocument(String content) {
        this.content = (content == null) ? "" : content;
        this.tokens = Tokenizer.tokenize(this.content);
    }

    /** @return the original, unchanged text */
    public String getContent() {
        return content;
    }

    /** @return the words of the text (read-only) */
    public List<String> getTokens() {
        return Collections.unmodifiableList(tokens);
    }

    /** @return how many words the text contains */
    public int getWordCount() {
        return tokens.size();
    }
}