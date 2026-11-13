import {gql} from '@apollo/client';

const REPORT_FIELDS = gql`
    fragment SecurityUpdatesReportFields on SecurityUpdatesReport {
        status
        inProgress
        stale
        checkedAt
        lastSuccess
        nextCheckAt
        jahiaVersion
        errors {
            storeUrl
            kind
            detail
            message
        }
        findings {
            component
            groupId
            installedVersion
            recommendedVersion
            compatibility
            requiredJahiaVersion
            severity
            source
            advisories {
                id
                title
                url
                cves
                severity
            }
        }
    }
`;

export const GET_SECURITY_REPORT = gql`
    query SecurityUpdatesReport {
        securityUpdates {
            report {
                ...SecurityUpdatesReportFields
            }
        }
    }
    ${REPORT_FIELDS}
`;

// Non-blocking: starts a check in the background (or joins the running one) and answers at once with inProgress.
export const REFRESH_SECURITY_REPORT = gql`
    mutation SecurityUpdatesRefresh {
        securityUpdates {
            refresh {
                ...SecurityUpdatesReportFields
            }
        }
    }
    ${REPORT_FIELDS}
`;
