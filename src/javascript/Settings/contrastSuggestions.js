// Suggests a colour reaching 7:1 against a fixed one: keeps the hue and saturation of the colour to change and moves
// its HSL lightness by the smallest step (thousandths, towards black and white at the same distance), checking each
// candidate after rounding to #RRGGBB. Same algorithm as ContrastSuggestions.java: a shared table of cases
// (src/test/resources/contrast-suggestions.json) checks that both give the same results. Lightness 0 and 1 are black
// and white whatever the hue, so when nothing passes there is no suggestion (null).

import {contrastRatio, formatRatio, MIN_CONTRAST, normalizeHex} from './colorUtils';

const STEPS = 1000;

const toHsl = hex => {
    const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.substring(i, i + 2), 16) / 255);
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const l = (max + min) / 2;
    if (max === min) {
        return [0, 0, l];
    }

    const d = max - min;
    const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    let h;
    if (max === r) {
        h = ((g - b) / d) + (g < b ? 6 : 0);
    } else if (max === g) {
        h = ((b - r) / d) + 2;
    } else {
        h = ((r - g) / d) + 4;
    }

    return [h / 6, s, l];
};

const hueToRgb = (p, q, hue) => {
    let t = hue;
    if (t < 0) {
        t += 1;
    }

    if (t > 1) {
        t -= 1;
    }

    if (t < 1 / 6) {
        return p + ((q - p) * 6 * t);
    }

    if (t < 1 / 2) {
        return q;
    }

    if (t < 2 / 3) {
        return p + ((q - p) * ((2 / 3) - t) * 6);
    }

    return p;
};

const toByte = value => Math.floor((value * 255) + 0.5).toString(16).toUpperCase().padStart(2, '0');

const fromHsl = (h, s, l) => {
    if (s === 0) {
        return `#${toByte(l)}${toByte(l)}${toByte(l)}`;
    }

    const q = l < 0.5 ? l * (1 + s) : l + s - (l * s);
    const p = (2 * l) - q;
    return `#${toByte(hueToRgb(p, q, h + (1 / 3)))}${toByte(hueToRgb(p, q, h))}${toByte(hueToRgb(p, q, h - (1 / 3)))}`;
};

/**
 * @param {string} fixed the colour that is kept, #RRGGBB
 * @param {string} moving the colour to change, #RRGGBB
 * @returns {{value: string, ratio: string}|null} the suggestion and its ratio, or null when the pair already passes,
 *          a colour is invalid, or no lightness of that hue passes
 */
export const suggestColor = (fixed, moving) => {
    const kept = normalizeHex(fixed);
    const changed = normalizeHex(moving);
    if (!kept || !changed || contrastRatio(kept, changed) >= MIN_CONTRAST) {
        return null;
    }

    const [h, s, l] = toHsl(changed);
    const start = Math.floor((l * STEPS) + 0.5);
    for (let distance = 1; distance <= STEPS; distance++) {
        let best = null;
        [start - distance, start + distance].forEach(candidate => {
            if (candidate < 0 || candidate > STEPS) {
                return;
            }

            const hex = fromHsl(h, s, candidate / STEPS);
            const ratio = contrastRatio(kept, hex);
            if (ratio >= MIN_CONTRAST && (!best || ratio > best.exact)) {
                best = {value: hex, exact: ratio};
            }
        });
        if (best) {
            return {value: best.value, ratio: formatRatio(best.exact)};
        }
    }

    return null;
};
