package textlens.analysis;

import java.util.Set;
import textlens.model.Sentiment;
import textlens.model.TextDocument;

/**
 * Rates the overall tone of a text using two word lists.
 *
 * The score is the number of positive words minus the number of negative
 * words. A positive score means POSITIVE, a negative score means NEGATIVE,
 * and zero means NEUTRAL.
 *
 * Limitation: it counts words only, so it cannot understand sarcasm or
 * negation such as "not good".
 */
public class SentimentAnalyzer implements Analyzer<Sentiment> {

    private final Set<String> pos;
    private final Set<String> neg;

    /**
     * @param positiveWords lower-case words that express a positive tone
     * @param negativeWords lower-case words that express a negative tone
     */
    public SentimentAnalyzer(Set<String> positiveWords, Set<String> negativeWords) {
        this.pos = positiveWords;
        this.neg = negativeWords;
    }

    @Override
    public Sentiment analyze(TextDocument doc) {
        return label(score(doc));
    }

    /** @return positive word count minus negative word count */
    public int score(TextDocument doc) {
        int score = 0;
        for (String word : doc.getTokens()) {
            if (pos.contains(word)) {
                score++;
            } else if (neg.contains(word)) {
                score--;
            }
        }
        return score;
    }

    /** Turns a score into a Sentiment label. */
    public Sentiment label(int score) {
        if (score > 0) {
            return Sentiment.POSITIVE;
        }
        if (score < 0) {
            return Sentiment.NEGATIVE;
        }
        return Sentiment.NEUTRAL;
    }
}