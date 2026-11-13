import {FIELDS, fromSettings, toInput} from '../settingsValues';

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
