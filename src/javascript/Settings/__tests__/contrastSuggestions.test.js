import cases from '../../../test/resources/contrast-suggestions.json';
import {suggestColor} from '../contrastSuggestions';
import {contrastRatio, formatRatio} from '../colorUtils';

describe('suggestColor (same table as ContrastSuggestionsTest.java)', () => {
    test.each(cases)('$text on $background', ({background, text, ratio, textSuggestion, backgroundSuggestion}) => {
        expect(formatRatio(contrastRatio(background, text))).toBe(ratio);
        expect(suggestColor(background, text)).toEqual(textSuggestion);
        expect(suggestColor(text, background)).toEqual(backgroundSuggestion);
    });

    test('every suggestion reaches 7:1 as written', () => {
        cases.forEach(({background, text}) => {
            const forText = suggestColor(background, text);
            if (forText) {
                expect(contrastRatio(background, forText.value)).toBeGreaterThanOrEqual(7);
            }

            const forBackground = suggestColor(text, background);
            if (forBackground) {
                expect(contrastRatio(text, forBackground.value)).toBeGreaterThanOrEqual(7);
            }
        });
    });

    test('invalid colours give no suggestion', () => {
        expect(suggestColor('#FFF', '#000000')).toBeNull();
        expect(suggestColor('#FFFFFF', 'red')).toBeNull();
    });
});
