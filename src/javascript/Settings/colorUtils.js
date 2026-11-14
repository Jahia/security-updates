// Colour helpers shared by the settings preview and its validation; same formula as the server (Colors.java).

export const MIN_CONTRAST = 7;

const HEX = /^#[0-9A-Fa-f]{6}$/;

export const normalizeHex = value => {
    if (typeof value !== 'string') {
        return null;
    }

    const trimmed = value.trim();
    return HEX.test(trimmed) ? trimmed.toUpperCase() : null;
};

const channel = (hex, start) => {
    const value = parseInt(hex.substring(start, start + 2), 16) / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
};

const luminance = hex => (0.2126 * channel(hex, 1)) + (0.7152 * channel(hex, 3)) + (0.0722 * channel(hex, 5));

/** WCAG contrast ratio of two #RRGGBB colours, or null when one is not a colour. */
export const contrastRatio = (first, second) => {
    const a = normalizeHex(first);
    const b = normalizeHex(second);
    if (!a || !b) {
        return null;
    }

    const la = luminance(a);
    const lb = luminance(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
};

/** Two decimals, rounded down, so that a ratio just under 7 never reads as 7.00. */
export const formatRatio = ratio => (typeof ratio === 'number' ? (Math.floor(ratio * 100) / 100).toFixed(2) : '');
