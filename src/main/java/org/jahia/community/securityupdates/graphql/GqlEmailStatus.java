package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.community.securityupdates.service.EmailStatus;

/**
 * GraphQL view of the security email: whether Jahia's mail service can send it, to whom, from which node, and when
 * the last one went out.
 */
@GraphQLName("SecurityUpdatesEmailStatus")
@GraphQLDescription("Whether the security updates email can be sent through Jahia's mail service")
public class GqlEmailStatus {

    private final EmailStatus status;
    private final boolean showRecipient;

    /**
     * @param status        the status of the service
     * @param showRecipient whether the current user may see the recipient (server administrators)
     */
    public GqlEmailStatus(EmailStatus status, boolean showRecipient) {
        this.status = status;
        this.showRecipient = showRecipient;
    }

    @GraphQLField
    @GraphQLName("mailServerReady")
    @GraphQLDescription("True when Jahia's mail service is on and configured")
    public boolean isMailServerReady() {
        return status.isMailServerReady();
    }

    @GraphQLField
    @GraphQLName("recipient")
    @GraphQLDescription("The administrator email address of Jahia's mail server settings; null when the mail service "
            + "chooses the recipient (the default recipient of the mail-service module), or for a user who is not a "
            + "server administrator")
    public String getRecipient() {
        return showRecipient ? status.getRecipient() : null;
    }

    @GraphQLField
    @GraphQLName("recipientMissing")
    @GraphQLDescription("True when Jahia's mail server settings have no administrator email address: no email can be sent")
    public boolean isRecipientMissing() {
        return status.isRecipientMissing();
    }

    @GraphQLField
    @GraphQLName("processingServer")
    @GraphQLDescription("True when this cluster node is the processing server, the only node that sends the scheduled email")
    public boolean isProcessingServer() {
        return status.isProcessingServer();
    }

    @GraphQLField
    @GraphQLName("lastSentAt")
    @GraphQLDescription("ISO-8601 time of the last scheduled email sent from this node, null if none")
    public String getLastSentAt() {
        return status.getLastSentAt() == null ? null : status.getLastSentAt().toString();
    }
}
