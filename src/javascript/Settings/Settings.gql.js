import {gql} from '@apollo/client';

const SETTINGS_FIELDS = gql`
    fragment SecurityUpdatesSettingsFields on SecurityUpdatesSettings {
        enabled
        refreshIntervalHours
        useForgeSettings
        defaultStoreUrl
        extraFeedUrls
        bannerEnabled
        bannerMinimumSeverity
        bannerIncludeOwnerReports
        bannerBackgroundColor
        bannerTextColor
        bannerDismissPolicy
        bannerDismissHours
        contrastRatio
        canEdit
    }
`;

export const GET_SETTINGS = gql`
    query SecurityUpdatesSettings {
        securityUpdates {
            settings {
                ...SecurityUpdatesSettingsFields
            }
        }
    }
    ${SETTINGS_FIELDS}
`;

// Input types of graphql-java-annotations are prefixed with "Input".
export const UPDATE_SETTINGS = gql`
    mutation SecurityUpdatesUpdateSettings($input: InputSecurityUpdatesSettingsValues!) {
        securityUpdates {
            updateSettings(input: $input) {
                ok
                errors {
                    field
                    code
                    message
                }
                settings {
                    ...SecurityUpdatesSettingsFields
                }
            }
        }
    }
    ${SETTINGS_FIELDS}
`;

export const RESET_SETTINGS = gql`
    mutation SecurityUpdatesResetSettings {
        securityUpdates {
            resetSettings {
                ok
                errors {
                    field
                    code
                    message
                }
                settings {
                    ...SecurityUpdatesSettingsFields
                }
            }
        }
    }
    ${SETTINGS_FIELDS}
`;
