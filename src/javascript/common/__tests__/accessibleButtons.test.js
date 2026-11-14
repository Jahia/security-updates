/**
 * @jest-environment node
 */
// Compiles the module's stylesheets with Sass and checks the colours of the Moonstone buttons the module renders
// (all carry the module's .target class): every state that shows text reaches 7:1 (WCAG 1.4.6), and the focus ring
// is 3px with a white gap. Moonstone's own accent and danger colours (white on #00a0e3 / #f44053) do not.
import path from 'path';
import * as sass from 'sass';
import {contrastRatio} from '../../Settings/colorUtils';

const STYLESHEETS = ['SecurityUpdates/SecurityUpdates.scss', 'Settings/SettingsForm.scss'];
const PAGE_BACKGROUND = '#FFFFFF';

const rules = css => [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map(([, selector, body]) => ({
    selector: selector.trim(),
    declarations: Object.fromEntries([...body.matchAll(/([\w-]+)\s*:\s*([^;]+);?/g)].map(([, name, value]) => [name, value.replace('!important', '').trim()]))
}));

const normalize = value => {
    const hex = String(value).trim().toUpperCase();
    if (hex === '#FFF' || hex === 'WHITE') {
        return '#FFFFFF';
    }

    return /^#[0-9A-F]{6}$/.test(hex) ? hex : null;
};

// Escapes every character that has a meaning in a regular expression, backslash included.
const escapeRegExp = text => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

describe.each(STYLESHEETS)('%s', file => {
    const css = sass.compile(path.resolve(__dirname, '../..', file), {loadPaths: [path.resolve(__dirname, '../..')]}).css;
    const buttonRules = rules(css).filter(rule => rule.selector.includes('.target.target'));

    test('overrides the accent and danger buttons in every state, and the hover and active states of the others', () => {
        const selectors = buttonRules.map(rule => rule.selector).join('\n');
        ['[data-variant=default][data-color=accent]', '[data-variant=default][data-color=danger]', '[data-variant=outlined]', '[data-variant=ghost]']
            .forEach(kind => {
                expect(selectors).toContain(kind);
                ['hover', 'active'].forEach(state => expect(selectors).toMatch(new RegExp(`${escapeRegExp(kind)}[^\\n]*:${state}`)));
            });
        expect(selectors).toMatch(/\[data-color=accent\]:focus-visible/);
    });

    test('every text colour reaches 7:1 against its background (or the white page)', () => {
        // "inherit" (the label inside a button) takes the colour of its button, checked here.
        const coloured = buttonRules.filter(rule => rule.declarations.color && rule.declarations.color !== 'inherit');
        expect(coloured.length).toBeGreaterThan(8);
        coloured.forEach(({selector, declarations}) => {
            const text = normalize(declarations.color);
            const background = declarations['background-color'] === 'transparent' || !declarations['background-color'] ?
                PAGE_BACKGROUND :
                normalize(declarations['background-color']);
            expect([selector, text !== null && background !== null]).toEqual([selector, true]);
            expect([selector, contrastRatio(text, background) >= 7]).toEqual([selector, true]);
        });
    });

    test('the focus ring is 3px, at least 3:1 against the page, with a white gap against the button', () => {
        const focus = buttonRules.find(rule => /\.target\.target:focus-visible$/.test(rule.selector.split(',').pop().trim()));
        expect(focus.declarations.outline).toMatch(/^3px solid #/);
        expect(contrastRatio(normalize(focus.declarations.outline.split(' ').pop()), PAGE_BACKGROUND)).toBeGreaterThanOrEqual(3);
        expect(focus.declarations['box-shadow']).toMatch(/0 0 0 2px #(fff|FFFFFF)/i);
    });
});
