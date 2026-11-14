package org.jahia.community.securityupdates.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Colour helpers: {@code #RRGGBB} parsing and the WCAG 2.x contrast ratio.
 */
public final class Colors {

    /** WCAG 2.2 AAA contrast for normal text. */
    public static final double MIN_CONTRAST = 7.0;

    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final double LUMINANCE_OFFSET = 0.05;
    private static final double SRGB_THRESHOLD = 0.04045;
    private static final double SRGB_DIVISOR = 12.92;
    private static final double SRGB_OFFSET = 0.055;
    private static final double SRGB_SCALE = 1.055;
    private static final double SRGB_EXPONENT = 2.4;
    private static final double RED = 0.2126;
    private static final double GREEN = 0.7152;
    private static final double BLUE = 0.0722;
    private static final double MAX_CHANNEL = 255.0;
    private static final int HEX_RADIX = 16;

    private Colors() {
    }

    /**
     * @return the colour as {@code #RRGGBB} in upper case, or empty when the value is not of that form
     */
    public static Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return HEX.matcher(trimmed).matches() ? Optional.of(trimmed.toUpperCase(Locale.ROOT)) : Optional.empty();
    }

    /**
     * @param first  a {@code #RRGGBB} colour
     * @param second a {@code #RRGGBB} colour
     * @return the WCAG contrast ratio, from 1 to 21, whatever the order of the colours
     */
    public static double contrastRatio(String first, String second) {
        double a = relativeLuminance(first);
        double b = relativeLuminance(second);
        return (Math.max(a, b) + LUMINANCE_OFFSET) / (Math.min(a, b) + LUMINANCE_OFFSET);
    }

    /**
     * @return true when the pair reaches {@link #MIN_CONTRAST}
     */
    public static boolean hasEnoughContrast(String first, String second) {
        return contrastRatio(first, second) >= MIN_CONTRAST;
    }

    private static double relativeLuminance(String color) {
        String hex = normalize(color).orElseThrow(() -> new IllegalArgumentException("Not a #RRGGBB colour"));
        return RED * channel(hex, 1) + GREEN * channel(hex, 3) + BLUE * channel(hex, 5);
    }

    private static double channel(String hex, int start) {
        double value = Integer.parseInt(hex.substring(start, start + 2), HEX_RADIX) / MAX_CHANNEL;
        return value <= SRGB_THRESHOLD ? value / SRGB_DIVISOR : Math.pow((value + SRGB_OFFSET) / SRGB_SCALE, SRGB_EXPONENT);
    }
}
