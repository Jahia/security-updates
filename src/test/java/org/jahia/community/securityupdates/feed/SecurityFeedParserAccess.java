package org.jahia.community.securityupdates.feed;

/** Gives tests of other packages the limits of {@link SecurityFeedParser}. */
public final class SecurityFeedParserAccess {

    private SecurityFeedParserAccess() {
    }

    public static int maxEntries() {
        return SecurityFeedParser.MAX_ENTRIES;
    }
}
