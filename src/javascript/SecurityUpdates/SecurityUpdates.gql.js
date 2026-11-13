import {gql} from '@apollo/client';

const REPORT_FIELDS = gql`
    fragment SecurityUpdatesReportFields on SecurityUpdatesReport {
        status
        checkedAt
        lastSuccess
        jahiaVersion
        errors {
            storeUrl
            kind
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
