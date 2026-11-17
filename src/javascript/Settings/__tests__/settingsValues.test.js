import {FIELDS, fromSettings, restartsSchedule, SCHEDULE_FIELDS, toInput} from '../settingsValues';
import {BANNER_POSITIONS, DEFAULT_BANNER_POSITION} from '../../common/constants';

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
    bannerPosition: 'BOTTOM_RIGHT',
    bannerDismissPolicy: 'SESSION',
    bannerDismissHours: 24,
    emailEnabled: true,
    emailFrequency: 'DAILY',
    emailMinimumSeverity: 'CRITICAL',
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
            'bannerPosition',
            'bannerDismissPolicy',
            'bannerDismissHours',
            'emailEnabled',
            'emailFrequency',
            'emailMinimumSeverity'
        ]);
    });

    test('the email settings are kept as returned and sent typed', () => {
        const {input} = toInput(fromSettings(settings));
        expect(input.emailEnabled).toBe(true);
        expect(input.emailFrequency).toBe('DAILY');
        expect(input.emailMinimumSeverity).toBe('CRITICAL');
    });

    test('email choices the server does not return take the server defaults, and the email stays off', () => {
        const form = fromSettings({...settings, emailEnabled: undefined, emailFrequency: undefined, emailMinimumSeverity: null});
        expect(form.emailEnabled).toBe(false);
        expect(form.emailFrequency).toBe('WEEKLY');
        expect(form.emailMinimumSeverity).toBe('HIGH');
    });

    test('an email change does not restart the schedule', () => {
        expect(restartsSchedule(settings, {...settings, emailEnabled: false, emailFrequency: 'MONTHLY'})).toBe(false);
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

    test('the banner positions are those of the server, top by default', () => {
        expect(BANNER_POSITIONS).toEqual(['TOP', 'BOTTOM', 'TOP_RIGHT', 'BOTTOM_RIGHT']);
        expect(DEFAULT_BANNER_POSITION).toBe('TOP');
    });

    test('the banner position is kept as returned and sent as text', () => {
        const form = fromSettings(settings);

        expect(form.bannerPosition).toBe('BOTTOM_RIGHT');
        expect(toInput(form).input.bannerPosition).toBe('BOTTOM_RIGHT');
    });

    test.each([undefined, null])('a banner position the server does not return (%s) takes the default, so a save sends a valid value', missing => {
        const form = fromSettings({...settings, bannerPosition: missing});

        expect(form.bannerPosition).toBe('TOP');
        expect(toInput(form).input.bannerPosition).toBe('TOP');
    });

    test('a text setting the server does not return stays empty (no default)', () => {
        expect(fromSettings({...settings, extraFeedUrls: null}).extraFeedUrls).toBe('');
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
        expect(restartsSchedule({...before, bannerPosition: 'TOP'}, {...before, bannerPosition: 'BOTTOM'})).toBe(false);
        expect(restartsSchedule(before, before)).toBe(false);
    });

    test('checks turned off never announce a next check; unknown previous settings with checks on do', () => {
        expect(restartsSchedule(before, {...before, enabled: false, refreshIntervalHours: 1})).toBe(false);
        expect(restartsSchedule(null, before)).toBe(true);
        expect(restartsSchedule(before, null)).toBe(false);
        expect(restartsSchedule({enabled: true}, {enabled: true})).toBe(false);
    });
});
