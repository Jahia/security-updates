import en from '../../../main/resources/javascript/locales/en.json';
import fr from '../../../main/resources/javascript/locales/fr.json';

const keys = (object, prefix = '') => Object.entries(object).flatMap(([key, value]) =>
    (typeof value === 'object' ? keys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));

describe('locales', () => {
    test('en and fr define the same keys', () => {
        expect(keys(fr).sort()).toEqual(keys(en).sort());
    });

    test.each(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'])('severity %s has a text label', severity => {
        expect(en.page.severity[severity]).toBeTruthy();
        expect(fr.page.severity[severity]).toBeTruthy();
    });

    test('every report status, error kind, compatibility and source has a label', () => {
        ['NOT_CHECKED', 'DISABLED', 'PARTIAL', 'UNAVAILABLE'].forEach(s => expect(en.page.status[s]).toBeTruthy());
        ['NOT_PUBLISHED', 'UNAVAILABLE', 'NOT_UNDERSTOOD', 'INVALID_URL', 'CATALOGUE_UNAVAILABLE', 'MALFORMED_VERSION', 'NO_STORE']
            .forEach(k => expect(en.page.errorKind[k]).toBeTruthy());
        ['COMPATIBLE', 'INCOMPATIBLE', 'UNKNOWN', 'JAHIA_UPGRADE'].forEach(c => expect(en.page.compatibility[c]).toBeTruthy());
        ['ADVISORY', 'OWNER_REPORTED'].forEach(s => expect(en.page.source[s]).toBeTruthy());
    });

    test.each(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])('the banner states the %s threshold in both languages', severity => {
        expect(en.banner.threshold[severity]).toBeTruthy();
        expect(fr.banner.threshold[severity]).toBeTruthy();
    });

    test('the banner message no longer speaks of Jahia advisories only and states the threshold', () => {
        [en, fr].forEach(locale => {
            expect(locale.banner.message).toContain('{{count}}');
            expect(locale.banner.message).toContain('{{threshold}}');
        });
        expect(en.banner.message).not.toMatch(/advisor/i);
        expect(fr.banner.message).not.toMatch(/bulletin/i);
    });

    test('every setting has a label and a help text, every colour a picker label, in both languages', () => {
        const fields = ['enabled',
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
            'bannerDismissHours'];
        [en, fr].forEach(locale => {
            fields.forEach(field => {
                expect(locale.settings.fields[field].label).toBeTruthy();
                expect(locale.settings.fields[field].help).toBeTruthy();
            });
            expect(locale.settings.fields.bannerBackgroundColor.picker).toBeTruthy();
            expect(locale.settings.fields.bannerTextColor.picker).toBeTruthy();
        });
    });

    test('every error code and dismiss policy has a text', () => {
        ['MISSING',
            'INVALID_BOOLEAN',
            'INVALID_NUMBER',
            'OUT_OF_RANGE',
            'INVALID_URL',
            'INVALID_VALUE',
            'INVALID_COLOR',
            'LOW_CONTRAST',
            'WRITE_FAILED'].forEach(code => expect(en.settings.errors[code]).toBeTruthy());
        ['SESSION', 'HOURS', 'UNTIL_CHANGE', 'NEVER'].forEach(policy => expect(en.settings.dismiss[policy]).toBeTruthy());
        expect(en.settings.errors.LOW_CONTRAST).toContain('{{ratio}}');
    });

    test('suggestion buttons and announcements name the colour value', () => {
        [en, fr].forEach(locale => {
            ['use_text', 'use_background', 'appliedText', 'appliedBackground', 'text', 'background'].forEach(key => {
                expect(locale.settings.suggest[key]).toContain('{{value}}');
            });
            expect(locale.settings.suggest.useDefaults).toContain('{{background}}');
        });
    });
});
