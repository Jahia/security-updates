import {pluralKey, values} from '../i18n';

describe('i18n helpers', () => {
    test('values turns off HTML escaping and keeps the variables', () => {
        expect(values({n: 2})).toEqual({n: 2, interpolation: {escapeValue: false}});
    });

    test.each([
        [1, 'en', 'one'],
        [2, 'en', 'other'],
        [0, 'en', 'other'],
        [0, 'fr', 'one'],
        [1, 'fr', 'one'],
        [2, 'fr', 'other'],
        [1000000, 'fr', 'other']
    ])('%d in %s is %s', (count, language, expected) => {
        expect(pluralKey(count, language)).toBe(expected);
    });

    test('an unknown language falls back to one/other', () => {
        expect(pluralKey(1, 'not a language!')).toBe('one');
        expect(pluralKey(3, 'not a language!')).toBe('other');
    });
});
