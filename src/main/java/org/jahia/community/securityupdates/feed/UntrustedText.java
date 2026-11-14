package org.jahia.community.securityupdates.feed;

/**
 * Cleans text taken from a store response before it is kept: control characters are removed (they could
 * forge log lines or confuse a terminal), whitespace is trimmed and the length is bounded. HTML is left as
 * is: the admin UI renders text with React, which escapes it.
 */
final class UntrustedText {

    private UntrustedText() {
    }

    /**
     * @return the cleaned text, at most {@code maxLength} characters, or null when nothing is left
     */
    static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(Math.min(value.length(), maxLength));
        for (int i = 0; i < value.length() && sb.length() < maxLength; i++) {
            char c = value.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') {
                sb.append(' ');
            } else if (!Character.isISOControl(c) && Character.getType(c) != Character.FORMAT) {
                sb.append(c);
            }
        }
        String cleaned = sb.toString().trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
