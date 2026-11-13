import {contrastRatio, formatRatio, MIN_CONTRAST, normalizeHex} from '../colorUtils';

describe('normalizeHex', () => {
    test.each([
        ['#0b3d91', '#0B3D91'],
        [' #FFFFFF ', '#FFFFFF'],
        ['#FFF', null],
        ['FFFFFF', null],
        ['#GGGGGG', null],
        ['', null],
        [null, null],
        [undefined, null]
    ])('%s -> %s', (value, expected) => {
        expect(normalizeHex(value)).toBe(expected);
    });
});

describe('contrastRatio', () => {
    test.each([
        ['#000000', '#FFFFFF', 21],
        ['#FFFFFF', '#000000', 21],
        ['#FFFFFF', '#FFFFFF', 1],
        ['#777777', '#888888', 1.26],
        ['#FFFFFF', '#0B3D91', 10.045],
        ['#FFFFFF', '#1B1B1B', 17.22]
    ])('%s on %s = %s', (a, b, expected) => {
        expect(contrastRatio(a, b)).toBeCloseTo(expected, 2);
    });

    test('is null when a colour is invalid', () => {
        expect(contrastRatio('#FFF', '#000000')).toBeNull();
    });

    test('the AAA threshold is 7', () => {
        expect(MIN_CONTRAST).toBe(7);
    });
});

describe('formatRatio', () => {
    test('rounds down to two decimals, so that 6.999 never reads as 7.00', () => {
        expect(formatRatio(17.2244)).toBe('17.22');
        expect(formatRatio(6.999)).toBe('6.99');
        expect(formatRatio(null)).toBe('');
    });
});
