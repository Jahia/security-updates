import {FIELDS, fromSettings, restartsSchedule, SCHEDULE_FIELDS, toInput} from '../settingsValues';

const settings = {
    enabled: true,
    refreshIntervalHours: 24,
    useForgeSettings: true,
    defaultStoreUrl: 'https://store.jahia.com',
    extraFeedUrls: '',
    bannerEnabled: true,
    bannerMinimumSeverity: 'HIGH',
    bannerIncludeOwnerReports: true,
    bannerBackgroundColor: '#1B1B1B',
    bannerTextColor: '#FFFFFF',
    bannerDismissPolicy: 'SESSION',
    bannerDismissHours: 24,
    contrastRatio: 17.22
};

describe('settings form values', () => {
    test('the form covers every configuration key', () => {
        expect(FIELDS).toEqual([
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
        ]);
    });

    test('numbers become editable text, booleans stay booleans', () => {
        const form = fromSettings(settings);
        expect(form.refreshIntervalHours).toBe('24');
        expect(form.enabled).toBe(true);
        expect(form).not.toHaveProperty('contrastRatio');
    });

    test('the input is typed for the mutation; text is sent as typed, the server validates it', () => {
        const form = {...fromSettings(settings), refreshIntervalHours: ' 0 ', bannerBackgroundColor: '#zzz'};

        const {input, errors} = toInput(form);

        expect(errors).toEqual({});
        expect(input.refreshIntervalHours).toBe(0);
        expect(input.bannerDismissHours).toBe(24);
        expect(input.bannerBackgroundColor).toBe('#zzz');
        expect(input.enabled).toBe(true);
        expect(Object.keys(input)).toEqual(FIELDS);
    });

    test.each(['', 'ten', '2.5', '1e3'])('a number field holding [%s] is refused before sending', value => {
        const {input, errors} = toInput({...fromSettings(settings), bannerDismissHours: value});

        expect(input).toBeNull();
        expect(errors).toEqual({bannerDismissHours: 'INVALID_NUMBER'});
    });
});

describe('restartsSchedule', () => {
    const before = {enabled: true, refreshIntervalHours: 24, useForgeSettings: true, defaultStoreUrl: 'https://store.jahia.com', extraFeedUrls: '', bannerMinimumSeverity: 'HIGH'};

    test('the schedule fields are those of Settings.sameSchedule', () => {
        expect(SCHEDULE_FIELDS).toEqual(['enabled', 'refreshIntervalHours', 'useForgeSettings', 'defaultStoreUrl', 'extraFeedUrls']);
    });

    test.each(SCHEDULE_FIELDS.filter(field => field !== 'enabled'))('a change of %s restarts the schedule', field => {
        expect(restartsSchedule(before, {...before, [field]: field === 'useForgeSettings' ? false : 'changed'})).toBe(true);
    });

    test('a banner change, or no change, does not', () => {
        expect(restartsSchedule(before, {...before, bannerMinimumSeverity: 'LOW'})).toBe(false);
        expect(restartsSchedule(before, before)).toBe(false);
    });

    test('checks turned off never announce a next check; unknown previous settings with checks on do', () => {
        expect(restartsSchedule(before, {...before, enabled: false, refreshIntervalHours: 1})).toBe(false);
        expect(restartsSchedule(null, before)).toBe(true);
        expect(restartsSchedule(before, null)).toBe(false);
        expect(restartsSchedule({enabled: true}, {enabled: true})).toBe(false);
    });
});
