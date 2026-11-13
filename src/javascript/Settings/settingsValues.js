// Conversion between the settings returned by GraphQL, the form state (text for the inputs) and the mutation input.

export const FIELDS = [
    'enabled',
    'refreshIntervalHours',
    'useForgeSettings',
    'defaultStoreUrl',
    'extraFeedUrls',
    'bannerEnabled',
    'bannerMinimumSeverity',
    'bannerIncludeOwnerReports',
    'bannerBackgroundColor',
    'bannerTextColor',
    'bannerDismissPolicy',
    'bannerDismissHours'
];

/** The settings whose change restarts the check schedule on the server (Settings.sameSchedule). */
export const SCHEDULE_FIELDS = ['enabled', 'refreshIntervalHours', 'useForgeSettings', 'defaultStoreUrl', 'extraFeedUrls'];

/**
 * Whether a save or a reset restarted the check schedule while checks are on: the next check then runs about one
 * minute later. Other changes (the banner) apply at once.
 *
 * @param {object} before the settings returned by the server before the change
 * @param {object} after the settings returned by the change
 * @returns {boolean}
 */
export const restartsSchedule = (before, after) => Boolean(after?.enabled) &&
    SCHEDULE_FIELDS.some(field => String(before?.[field] ?? '') !== String(after?.[field] ?? ''));

export const BOOLEAN_FIELDS = ['enabled', 'useForgeSettings', 'bannerEnabled', 'bannerIncludeOwnerReports'];
export const NUMBER_FIELDS = ['refreshIntervalHours', 'bannerDismissHours'];

const WHOLE_NUMBER = /^-?\d+$/;

/** Form state from the GraphQL settings: booleans for the checkboxes, text for every other control. */
export const fromSettings = settings => FIELDS.reduce((form, field) => ({
    ...form,
    [field]: BOOLEAN_FIELDS.includes(field) ? Boolean(settings[field]) : String(settings[field] ?? '')
}), {});

/**
 * Mutation input from the form state. Only the number fields are checked here (GraphQL needs an Int); every rule
 * is enforced by the server, which answers with per-field errors.
 *
 * @returns {{input: object|null, errors: object}} the input, or null with the field errors
 */
export const toInput = form => {
    const errors = {};
    const input = {};
    FIELDS.forEach(field => {
        const value = form[field];
        if (BOOLEAN_FIELDS.includes(field)) {
            input[field] = Boolean(value);
        } else if (NUMBER_FIELDS.includes(field)) {
            const text = String(value).trim();
            if (WHOLE_NUMBER.test(text)) {
                input[field] = parseInt(text, 10);
            } else {
                errors[field] = 'INVALID_NUMBER';
            }
        } else {
            input[field] = String(value);
        }
    });
    return Object.keys(errors).length > 0 ? {input: null, errors} : {input, errors};
};
