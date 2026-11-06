package textlens.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits raw text into clean, lower-case words using a regular expression.
 * Every analyzer works on these tokens, so the text is only split once.
 */
public class Tokenizer {

    // A word is a run of letters, optionally joined by an apostrophe or hyphen
    // (for example: don't, well-known). Digits and symbols are ignored.
    private static final Pattern WORD =
            Pattern.compile("\\p{L}+(?:['\u2019-]\\p{L}+)*");

    /**
     * Converts text into a list of lower-case words.
     *
     * @param text the raw text (may be null or empty)
     * @return the words in order of appearance; an empty list if there are none
     */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase());
        }
        return tokens;
    }
}