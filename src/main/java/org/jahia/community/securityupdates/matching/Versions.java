package org.jahia.community.securityupdates.matching;

import org.jahia.utils.comparator.VersionComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Version helpers. Ordering is the one Jahia uses for modules ({@link VersionComparator}, Maven
 * {@code ComparableVersion} rules); the "line" of a version is computed from its leading numbers.
 */
public final class Versions {

    /** Longest version string accepted; longer strings come from a broken or hostile feed. */
    static final int MAX_LENGTH = 64;
    /** Most digits in one numeric segment, so that every segment fits an int. */
    private static final int MAX_SEGMENT_DIGITS = 9;
    private static final int CORE_LINE_SEGMENTS = 3;
    /**
     * A version starts with a number; dots separate non-empty segments; a qualifier may follow after
     * {@code -}, {@code .} or {@code _}.
     */
    private static final Pattern WELL_FORMED = Pattern.compile("\\d+(\\.\\d+)*([.\\-_+][0-9A-Za-z][0-9A-Za-z.\\-_+]*)?");

    private Versions() {
    }

    /**
     * @return true when {@code version} can be compared and its line computed
     */
    public static boolean isWellFormed(String version) {
        if (version == null || version.isEmpty() || version.length() > MAX_LENGTH
                || !WELL_FORMED.matcher(version).matches()) {
            return false;
        }
        for (String segment : leadingSegments(version)) {
            if (segment.length() > MAX_SEGMENT_DIGITS) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two well-formed versions with the rules Jahia uses for module versions.
     *
     * @return a negative number, zero or a positive number as {@code left} is lower than, equal to or
     * greater than {@code right}
     */
    public static int compare(String left, String right) {
        return VersionComparator.compare(left, right);
    }

    /**
     * The maintenance line of a version: its major for a module, {@code major.minor.servicePack} for the
     * Jahia platform (missing numbers count as 0).
     *
     * @throws IllegalArgumentException when the version is not well formed
     */
    public static String line(String version, boolean core) {
        List<Integer> numbers = leadingNumbers(version);
        if (!core) {
            return String.valueOf(numbers.get(0));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CORE_LINE_SEGMENTS; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(i < numbers.size() ? numbers.get(i) : 0);
        }
        return sb.toString();
    }

    /**
     * @return the first number of a well-formed version
     * @throws IllegalArgumentException when the version is not well formed
     */
    public static int major(String version) {
        return leadingNumbers(version).get(0);
    }

    private static List<Integer> leadingNumbers(String version) {
        if (!isWellFormed(version)) {
            throw new IllegalArgumentException("Malformed version");
        }
        List<Integer> numbers = new ArrayList<>();
        for (String segment : leadingSegments(version)) {
            numbers.add(Integer.parseInt(segment));
        }
        return numbers;
    }

    /** The dot-separated numeric segments at the start of the version, before any qualifier. */
    private static List<String> leadingSegments(String version) {
        int end = 0;
        while (end < version.length() && (Character.isDigit(version.charAt(end)) || version.charAt(end) == '.')) {
            end++;
        }
        String numeric = version.substring(0, end);
        if (numeric.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : numeric.split("\\.")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }
}
