package textlens.model;

/*
 * The overall tone of a text, as decided by the SentimentAnalyzer.
 * An enum is used because a text can only ever be one of these three values.
 */
public enum Sentiment {
    POSITIVE,
    NEUTRAL,
    NEGATIVE
}