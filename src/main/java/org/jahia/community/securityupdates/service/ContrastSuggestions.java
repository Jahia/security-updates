package org.jahia.community.securityupdates.service;

import java.util.Locale;
import java.util.Optional;

/**
 * Suggests a colour that reaches the 7:1 contrast ratio against a fixed colour, keeping the hue and saturation of
 * the colour to change and moving only its HSL lightness, by the smallest step that passes.
 *
 * <p>The lightness is searched in thousandths, both towards black and towards white at the same distance, and each
 * candidate is checked after rounding to {@code #RRGGBB}, so a suggestion always passes as written. When both
 * directions pass at the same distance, the higher ratio wins. HSL lightness 0 and 1 are black and white whatever
 * the hue, so when no lightness passes, neither does black nor white: there is then no suggestion (a mid grey such
 * as {@code #777777} has no colour reaching 7:1 against it).
 *
 * <p>The same algorithm runs in the browser ({@code src/javascript/Settings/contrastSuggestions.js}); a shared table
 * of cases ({@code src/test/resources/contrast-suggestions.json}) checks that both give the same results.
 */
public final class ContrastSuggestions {

    private static final int STEPS = 1000;
    private static final double MAX_CHANNEL = 255.0;
    private static final double SIXTH = 1.0 / 6;
    private static final double THIRD = 1.0 / 3;
    private static final double HALF = 0.5;
    private static final double TWO_THIRDS = 2.0 / 3;
    private static final int HUE_SECTORS = 6;
    private static final int PERCENT = 100;

    private ContrastSuggestions() {
    }

    /** A suggested colour and its contrast ratio against the fixed colour, rounded down to two decimals. */
    public static final class Suggestion {
        private final String value;
        private final double exactRatio;

        Suggestion(String value, double exactRatio) {
            this.value = value;
            this.exactRatio = exactRatio;
        }

        public String getValue() {
            return value;
        }

        public String getRatio() {
            return formatRatio(exactRatio);
        }
    }

    /**
     * @param fixed  the colour that is kept, {@code #RRGGBB}
     * @param moving the colour to change, {@code #RRGGBB}
     * @return a colour close to {@code moving} with a ratio of at least 7:1 against {@code fixed}; empty when the pair
     * already passes or when no lightness of that hue passes
     */
    public static Optional<Suggestion> suggest(String fixed, String moving) {
        if (Colors.hasEnoughContrast(fixed, moving)) {
            return Optional.empty();
        }
        double[] hsl = toHsl(Colors.normalize(moving).orElseThrow(() -> new IllegalArgumentException("Not a #RRGGBB colour")));
        int start = (int) Math.floor(hsl[2] * STEPS + HALF);
        for (int distance = 1; distance <= STEPS; distance++) {
            Suggestion best = null;
            for (int candidate : new int[]{start - distance, start + distance}) {
                if (candidate < 0 || candidate > STEPS) {
                    continue;
                }
                String hex = fromHsl(hsl[0], hsl[1], (double) candidate / STEPS);
                double ratio = Colors.contrastRatio(fixed, hex);
                if (ratio >= Colors.MIN_CONTRAST && (best == null || ratio > best.exactRatio)) {
                    best = new Suggestion(hex, ratio);
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    /** A contrast ratio rounded down to two decimals, so that 6.999 never reads as 7.00. */
    public static String formatRatio(double ratio) {
        return String.format(Locale.ROOT, "%.2f", Math.floor(ratio * PERCENT) / PERCENT);
    }

    private static double[] toHsl(String hex) {
        double r = channel(hex, 1);
        double g = channel(hex, 3);
        double b = channel(hex, 5);
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        if (max == min) {
            return new double[]{0, 0, l};
        }
        double d = max - min;
        double s = l > HALF ? d / (2 - max - min) : d / (max + min);
        double h;
        if (max == r) {
            h = (g - b) / d + (g < b ? HUE_SECTORS : 0);
        } else if (max == g) {
            h = (b - r) / d + 2;
        } else {
            h = (r - g) / d + 4;
        }
        return new double[]{h / HUE_SECTORS, s, l};
    }

    private static String fromHsl(double h, double s, double l) {
        double r;
        double g;
        double b;
        if (s == 0) {
            r = l;
            g = l;
            b = l;
        } else {
            double q = l < HALF ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hueToRgb(p, q, h + THIRD);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - THIRD);
        }
        return String.format(Locale.ROOT, "#%02X%02X%02X", toByte(r), toByte(g), toByte(b));
    }

    private static double hueToRgb(double p, double q, double hue) {
        double t = hue;
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < SIXTH) {
            return p + (q - p) * HUE_SECTORS * t;
        }
        if (t < HALF) {
            return q;
        }
        if (t < TWO_THIRDS) {
            return p + (q - p) * (TWO_THIRDS - t) * HUE_SECTORS;
        }
        return p;
    }

    private static int toByte(double value) {
        return (int) Math.floor(value * MAX_CHANNEL + HALF);
    }

    private static double channel(String hex, int start) {
        return Integer.parseInt(hex.substring(start, start + 2), 16) / MAX_CHANNEL;
    }
}
