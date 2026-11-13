package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.StoreErrorDetail;

/**
 * Bounds the nesting of a JSON document before it is parsed: org.json parses recursively, so a document nested
 * thousands of levels deep would exhaust the stack. One linear pass, brackets inside strings ignored. Strings are
 * delimited by double quotes, or by single quotes, which org.json's tokenizer also accepts: a quote of the other kind
 * inside a string is a plain character, a backslash escapes the next character in both.
 */
final class JsonNesting {

    /** Deepest nesting of arrays and objects accepted; the feeds need 6 levels. */
    static final int MAX_DEPTH = 64;

    private JsonNesting() {
    }

    /**
     * @param json      the document (not null)
     * @param what      the name of the document, for the error message
     * @throws FeedFormatException when arrays and objects are nested more than {@link #MAX_DEPTH} levels
     */
    static void check(String json, String what) throws FeedFormatException {
        int depth = 0;
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"' || c == '\'') {
                i = closingQuote(json, i + 1, c);
            } else if (c == '[' || c == '{') {
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new FeedFormatException(StoreErrorDetail.INVALID_JSON,
                            what + " is nested more than " + MAX_DEPTH + " levels deep");
                }
            } else if (c == ']' || c == '}') {
                depth--;
            }
            i++;
        }
    }

    /**
     * @param quote the quote that opened the string
     * @return the index of the quote that closes the string starting at {@code from}, or the document length when
     *         the string is not closed; escaped characters are skipped
     */
    private static int closingQuote(String json, int from, char quote) {
        int i = from;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i;
            } else {
                i++;
            }
        }
        return json.length();
    }
}
