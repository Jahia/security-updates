package org.jahia.community.securityupdates.service;

import java.util.Objects;

/**
 * One email: its subject, its plain text body and its HTML body.
 */
public final class EmailMessage {

    private final String subject;
    private final String text;
    private final String html;

    public EmailMessage(String subject, String text, String html) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.text = Objects.requireNonNull(text, "text");
        this.html = Objects.requireNonNull(html, "html");
    }

    public String getSubject() {
        return subject;
    }

    public String getText() {
        return text;
    }

    public String getHtml() {
        return html;
    }
}
