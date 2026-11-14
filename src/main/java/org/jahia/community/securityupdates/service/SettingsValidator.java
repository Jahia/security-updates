package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.feed.StoreUrls;
import org.jahia.community.securityupdates.model.DismissPolicy;
import org.jahia.community.securityupdates.model.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Strict validation of a full set of settings, as submitted by the settings UI. Unlike the configuration
 * fallbacks of {@link Settings#fromProperties}, an invalid value is refused with a {@link FieldError}; the result then
 * holds no value, so nothing can be written partially. Rules:
 * <ul>
 *     <li>every key of {@link SettingsKeys#ALL} is present;</li>
 *     <li>booleans are {@code true} or {@code false};</li>
 *     <li>{@code refreshIntervalHours} and {@code bannerDismissHours} are integers from 1 to 720;</li>
 *     <li>{@code defaultStoreUrl} (may be empty) and every URL of {@code extraFeedUrls} pass {@link StoreUrls}
 *     ({@code NOT_HTTPS} for a plain http URL, {@code INVALID_URL} otherwise); at most
 *     {@value Settings#MAX_EXTRA_FEED_URLS} extra feed URLs ({@code TOO_MANY}), separated by commas or white space;</li>
 *     <li>{@code bannerMinimumSeverity} is LOW, MEDIUM, HIGH or CRITICAL; {@code bannerDismissPolicy} a
 *     {@link DismissPolicy};</li>
 *     <li>colours are {@code #RRGGBB} and their contrast ratio is at least 7:1.</li>
 * </ul>
 */
public final class SettingsValidator {

    static final int MIN_HOURS = 1;
    static final int MAX_HOURS = 720;
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String INVALID_URL = "INVALID_URL";
    private static final String INVALID_URL_MESSAGE = "Must be an absolute https URL without credentials, query or fragment";
    private static final String NOT_HTTPS = "NOT_HTTPS";
    private static final String NOT_HTTPS_MESSAGE = "Must be an https URL: plain http is refused";

    private SettingsValidator() {
    }

    public static ValidationResult validate(Map<String, String> input) {
        Map<String, String> values = new LinkedHashMap<>();
        List<FieldError> errors = new ArrayList<>();
        for (String key : SettingsKeys.ALL) {
            if (input.get(key) == null) {
                errors.add(new FieldError(key, "MISSING", "A value is required"));
            }
        }
        if (!errors.isEmpty()) {
            return new ValidationResult(values, errors);
        }
        bool(input, SettingsKeys.ENABLED, values, errors);
        bool(input, SettingsKeys.USE_FORGE_SETTINGS, values, errors);
        bool(input, SettingsKeys.BANNER_ENABLED, values, errors);
        bool(input, SettingsKeys.BANNER_INCLUDE_OWNER_REPORTS, values, errors);
        hours(input, SettingsKeys.REFRESH_INTERVAL_HOURS, values, errors);
        hours(input, SettingsKeys.BANNER_DISMISS_HOURS, values, errors);
        defaultStoreUrl(input, values, errors);
        extraFeedUrls(input, values, errors);
        minimumSeverity(input, values, errors);
        dismissPolicy(input, values, errors);
        colors(input, values, errors);
        return new ValidationResult(values, errors);
    }

    private static void bool(Map<String, String> input, String key, Map<String, String> values, List<FieldError> errors) {
        String value = input.get(key).trim().toLowerCase(Locale.ROOT);
        if (TRUE.equals(value) || FALSE.equals(value)) {
            values.put(key, value);
        } else {
            errors.add(new FieldError(key, "INVALID_BOOLEAN", "Must be true or false"));
        }
    }

    private static void hours(Map<String, String> input, String key, Map<String, String> values, List<FieldError> errors) {
        int hours;
        try {
            hours = Integer.parseInt(input.get(key).trim());
        } catch (NumberFormatException e) {
            errors.add(new FieldError(key, "INVALID_NUMBER", "Must be a whole number of hours"));
            return;
        }
        if (hours < MIN_HOURS || hours > MAX_HOURS) {
            errors.add(new FieldError(key, "OUT_OF_RANGE", "Must be from " + MIN_HOURS + " to " + MAX_HOURS + " hours"));
        } else {
            values.put(key, String.valueOf(hours));
        }
    }

    private static void defaultStoreUrl(Map<String, String> input, Map<String, String> values, List<FieldError> errors) {
        String url = input.get(SettingsKeys.DEFAULT_STORE_URL).trim();
        if (url.isEmpty()) {
            values.put(SettingsKeys.DEFAULT_STORE_URL, "");
            return;
        }
        Optional<String> normalized = StoreUrls.normalize(url);
        if (normalized.isPresent()) {
            values.put(SettingsKeys.DEFAULT_STORE_URL, normalized.get());
        } else {
            errors.add(urlError(SettingsKeys.DEFAULT_STORE_URL, url));
        }
    }

    private static FieldError urlError(String key, String url) {
        return StoreUrls.isPlainHttp(url)
                ? new FieldError(key, NOT_HTTPS, NOT_HTTPS_MESSAGE)
                : new FieldError(key, INVALID_URL, INVALID_URL_MESSAGE);
    }

    private static void extraFeedUrls(Map<String, String> input, Map<String, String> values, List<FieldError> errors) {
        List<String> urls = new ArrayList<>();
        for (String part : input.get(SettingsKeys.EXTRA_FEED_URLS).split("[,\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            Optional<String> normalized = StoreUrls.normalize(part);
            if (normalized.isEmpty()) {
                errors.add(urlError(SettingsKeys.EXTRA_FEED_URLS, part));
                return;
            }
            urls.add(normalized.get());
        }
        if (urls.size() > Settings.MAX_EXTRA_FEED_URLS) {
            errors.add(new FieldError(SettingsKeys.EXTRA_FEED_URLS, "TOO_MANY",
                    "At most " + Settings.MAX_EXTRA_FEED_URLS + " extra feed URLs"));
            return;
        }
        values.put(SettingsKeys.EXTRA_FEED_URLS, String.join(", ", urls));
    }

    private static void minimumSeverity(Map<String, String> input, Map<String, String> values, List<FieldError> errors) {
        Severity severity = Severity.parse(input.get(SettingsKeys.BANNER_MINIMUM_SEVERITY));
        if (severity == Severity.UNKNOWN) {
            errors.add(new FieldError(SettingsKeys.BANNER_MINIMUM_SEVERITY, "INVALID_VALUE", "Must be LOW, MEDIUM, HIGH or CRITICAL"));
        } else {
            values.put(SettingsKeys.BANNER_MINIMUM_SEVERITY, severity.name());
        }
    }

    private static void dismissPolicy(Map<String, String> input, Map<String, String> values, List<FieldError> errors) {
        try {
            DismissPolicy policy = DismissPolicy.valueOf(input.get(SettingsKeys.BANNER_DISMISS_POLICY).trim().toUpperCase(Locale.ROOT));
            values.put(SettingsKeys.BANNER_DISMISS_POLICY, policy.name());
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError(SettingsKeys.BANNER_DISMISS_POLICY, "INVALID_VALUE",
                    "Must be SESSION, HOURS, UNTIL_CHANGE or NEVER"));
        }
    }

    private static void colors(Map<String, String> input, Map<String, String> values, List<FieldError> errors) {
        Optional<String> background = Colors.normalize(input.get(SettingsKeys.BANNER_BACKGROUND_COLOR));
        Optional<String> text = Colors.normalize(input.get(SettingsKeys.BANNER_TEXT_COLOR));
        if (background.isEmpty()) {
            errors.add(new FieldError(SettingsKeys.BANNER_BACKGROUND_COLOR, "INVALID_COLOR", "Must be a #RRGGBB colour"));
        }
        if (text.isEmpty()) {
            errors.add(new FieldError(SettingsKeys.BANNER_TEXT_COLOR, "INVALID_COLOR", "Must be a #RRGGBB colour"));
        }
        if (background.isEmpty() || text.isEmpty()) {
            return;
        }
        double ratio = Colors.contrastRatio(background.get(), text.get());
        if (ratio < Colors.MIN_CONTRAST) {
            List<FieldError.SuggestedValue> suggestions = new ArrayList<>();
            ContrastSuggestions.suggest(background.get(), text.get()).ifPresent(s -> suggestions.add(
                    new FieldError.SuggestedValue(SettingsKeys.BANNER_TEXT_COLOR, s.getValue(), s.getRatio())));
            ContrastSuggestions.suggest(text.get(), background.get()).ifPresent(s -> suggestions.add(
                    new FieldError.SuggestedValue(SettingsKeys.BANNER_BACKGROUND_COLOR, s.getValue(), s.getRatio())));
            String message = String.format(Locale.ROOT, "The contrast ratio is %.2f:1; at least %.0f:1 is required. %s", ratio,
                    Colors.MIN_CONTRAST, suggestionText(suggestions));
            errors.add(new FieldError(SettingsKeys.BANNER_BACKGROUND_COLOR, "LOW_CONTRAST", message, suggestions));
            errors.add(new FieldError(SettingsKeys.BANNER_TEXT_COLOR, "LOW_CONTRAST", message, suggestions));
            return;
        }
        values.put(SettingsKeys.BANNER_BACKGROUND_COLOR, background.get());
        values.put(SettingsKeys.BANNER_TEXT_COLOR, text.get());
    }

    private static String suggestionText(List<FieldError.SuggestedValue> suggestions) {
        if (suggestions.isEmpty()) {
            return "No single colour change reaches 7:1; for example the default pair " + BannerSettings.DEFAULT_BACKGROUND_COLOR
                    + " (background) / " + BannerSettings.DEFAULT_TEXT_COLOR + " (text) does.";
        }
        List<String> parts = new ArrayList<>();
        for (FieldError.SuggestedValue suggestion : suggestions) {
            String which = SettingsKeys.BANNER_TEXT_COLOR.equals(suggestion.getField()) ? "text" : "background";
            parts.add(which + " " + suggestion.getValue() + " (" + suggestion.getRatio() + ":1)");
        }
        return "Suggested: " + String.join(" or ", parts) + ".";
    }
}
