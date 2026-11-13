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
});
