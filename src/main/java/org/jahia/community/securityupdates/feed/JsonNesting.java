package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.StoreErrorDetail;

/**
 * Bounds the nesting of a JSON document before it is parsed: org.json parses recursively, so a document nested
 * thousands of levels deep would exhaust the stack. One linear pass, brackets inside strings ignored.
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
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new FeedFormatException(StoreErrorDetail.INVALID_JSON,
                            what + " is nested more than " + MAX_DEPTH + " levels deep");
                }
            } else if (c == ']' || c == '}') {
                depth--;
            }
        }
    }
}
