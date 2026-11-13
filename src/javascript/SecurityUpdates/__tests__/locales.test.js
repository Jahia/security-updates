import fs from 'fs';
import path from 'path';
import en from '../../../main/resources/javascript/locales/en.json';
import fr from '../../../main/resources/javascript/locales/fr.json';

const keys = (object, prefix = '') => Object.entries(object).flatMap(([key, value]) =>
    (typeof value === 'object' ? keys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));

const get = (locale, key) => key.split('.').reduce((node, part) => (node && typeof node === 'object' ? node[part] : undefined), locale);

const placeholders = text => [...String(text).matchAll(/{{\s*(\w+)\s*}}/g)].map(match => match[1]).sort();

// Every source file of the module (not the tests), to find the keys given to t().
const sourceDir = path.resolve(__dirname, '../..');
const sources = dir => fs.readdirSync(dir, {withFileTypes: true}).flatMap(entry => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
        return ['__tests__', '__mocks__'].includes(entry.name) ? [] : sources(full);
    }

    return /\.jsx?$/.test(entry.name) ? [full] : [];
});
const code = sources(sourceDir).map(file => fs.readFileSync(file, 'utf8')).join('\n');

describe('locales', () => {
    test('en and fr define the same keys', () => {
        expect(keys(fr).sort()).toEqual(keys(en).sort());
    });

    test('en and fr use the same placeholders in every text', () => {
        keys(en).forEach(key => {
            expect([key, placeholders(get(fr, key))]).toEqual([key, placeholders(get(en, key))]);
        });
    });

    test('every literal key given to t() exists in English', () => {
        const literal = [...code.matchAll(/\bt\('([\w.]+)'/g)].map(match => match[1]);
        expect(literal.length).toBeGreaterThan(20);
        literal.forEach(key => expect([key, typeof get(en, key)]).toEqual([key, 'string']));
    });

    test('every key prefix built with a template exists in English', () => {
        const prefixes = [...code.matchAll(/\bt\(`([\w.]+)\.\$\{/g)].map(match => match[1]);
        expect(prefixes.length).toBeGreaterThan(5);
        prefixes.forEach(prefix => expect([prefix, typeof get(en, prefix)]).toEqual([prefix, 'object']));
    });

    test('texts use the typographic apostrophe in French', () => {
        keys(fr).forEach(key => expect([key, get(fr, key).includes('\'')]).toEqual([key, false]));
    });

    test('plural texts have a one and an other form, and say the number', () => {
        ['page.summary', 'page.bannerCount', 'page.filter.showing', 'banner.message', 'banner.hidden.HOURS'].forEach(key => {
            [en, fr].forEach(locale => {
                expect(get(locale, `${key}.one`)).toContain('{{n}}');
                expect(get(locale, `${key}.other`)).toContain('{{n}}');
            });
        });
    });

    test.each(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'])('severity %s has a text label', severity => {
        expect(en.page.severity[severity]).toBeTruthy();
        expect(fr.page.severity[severity]).toBeTruthy();
    });

    test('every report status, view status, error kind, error detail, compatibility and source has a text', () => {
        ['NOT_CHECKED', 'DISABLED', 'PARTIAL', 'PARTIAL_EMPTY', 'UNAVAILABLE', 'NOT_PUBLISHED', 'NOT_PUBLISHED_OTHER', 'STALE']
            .forEach(s => expect(en.page.status[s]).toBeTruthy());
        ['NOT_CHECKED', 'DISABLED', 'UP_TO_DATE', 'UPDATES_NEEDED', 'PARTIAL', 'NO_DATA', 'NOT_AVAILABLE_YET']
            .forEach(s => expect(en.page.statusLabel[s]).toBeTruthy());
        ['NOT_PUBLISHED', 'UNAVAILABLE', 'NOT_UNDERSTOOD', 'INVALID_URL', 'CATALOGUE_UNAVAILABLE', 'MALFORMED_VERSION', 'NO_STORE', 'STALE']
            .forEach(k => {
                expect(en.page.errorKind[k]).toBeTruthy();
                expect(en.page.errorHelp[k]).toBeTruthy();
            });
        ['NOT_HTTPS',
            'SSRF_BLOCKED',
            'TIMEOUT',
            'CONNECTION',
            'REDIRECT',
            'AUTH',
            'HTTP_STATUS',
            'TOO_LARGE',
            'INVALID_JSON',
            'SCHEMA_UNSUPPORTED',
            'ENTRIES_SKIPPED',
            'FORGE_SETTINGS_UNREADABLE',
            'CHECK_FAILED']
            .forEach(d => expect(en.page.errorDetail[d]).toBeTruthy());
        ['COMPATIBLE', 'INCOMPATIBLE', 'UNKNOWN', 'JAHIA_UPGRADE'].forEach(c => expect(en.page.compatibility[c]).toBeTruthy());
        ['ADVISORY', 'OWNER_REPORTED'].forEach(s => expect(en.page.source[s]).toBeTruthy());
    });

    test('the SSRF help names the Jahia property that allows an internal host', () => {
        [en, fr].forEach(locale => expect(locale.page.errorDetail.SSRF_BLOCKED).toContain('jahia.httpClient.allowedInternalHosts'));
    });

    test.each(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])('the banner states the %s threshold in both languages', severity => {
        expect(en.banner.threshold[severity]).toBeTruthy();
        expect(fr.banner.threshold[severity]).toBeTruthy();
    });

    test('the banner message states the threshold and does not speak of Jahia advisories only', () => {
        [en, fr].forEach(locale => {
            expect(locale.banner.message.other).toContain('{{threshold}}');
        });
        // The message counts module owner reports too (when set to): it does not call them advisories. The text said
        // after hiding the banner names what makes it come back (a new advisory or component), and is not checked.
        const shown = locale => JSON.stringify({message: locale.banner.message, threshold: locale.banner.threshold, link: locale.banner.link});
        expect(shown(en)).not.toMatch(/advisor/i);
        expect(shown(fr)).not.toMatch(/bulletin/i);
    });

    test('the banner is a "banner" in English and a "bandeau" in French, never a notice or an avis', () => {
        keys(en).forEach(key => expect([key, /\bnotice\b/i.test(get(en, key))]).toEqual([key, false]));
        keys(fr).forEach(key => expect([key, /\bavis\b/i.test(get(fr, key))]).toEqual([key, false]));
        expect(en.settings.groups.banner).toMatch(/banner/i);
        expect(fr.settings.groups.banner).toMatch(/bandeau/i);
    });

    test('plain language: no configuration jargon in the texts', () => {
        [en, fr].forEach(locale => {
            keys(locale).forEach(key => {
                expect([key, /karaf|\bPID\b|#RRGGBB|NOT_PUBLISHED|\bfeeds?\b|\bflux\b/i.test(get(locale, key))]).toEqual([key, false]);
            });
        });
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
            'NOT_HTTPS',
            'TOO_MANY',
            'INVALID_VALUE',
            'INVALID_COLOR',
            'LOW_CONTRAST',
            'WRITE_FAILED',
            'DENIED',
            'REQUEST_FAILED'].forEach(code => expect(en.settings.errors[code]).toBeTruthy());
        ['SESSION', 'HOURS', 'UNTIL_CHANGE', 'NEVER'].forEach(policy => expect(en.settings.dismiss[policy]).toBeTruthy());
        expect(en.settings.errors.LOW_CONTRAST).toContain('{{ratio}}');
    });

    test('the session dismissal is described as the browser tab', () => {
        expect(en.settings.dismiss.SESSION).toMatch(/tab/);
        expect(fr.settings.dismiss.SESSION).toMatch(/onglet/);
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
