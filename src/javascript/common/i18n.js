// I18n helpers. Texts are rendered as React text or DOM textContent, which never interpret markup: i18next's HTML
// escaping of interpolated values is therefore turned off, or "&" would show as "&amp;".

/** Interpolation options for t(): the values, without HTML escaping. */
export const values = vars => ({...vars, interpolation: {escapeValue: false}});

/**
 * The plural form of a count in a language, as a key suffix: "one" or "other". Plural keys are chosen here rather
 * than by i18next (whose suffixes depend on how the Jahia UI configures it), and the number is passed as `n`.
 */
export const pluralKey = (count, language) => {
    try {
        return new Intl.PluralRules(language || undefined).select(count) === 'one' ? 'one' : 'other';
    } catch {
        return count === 1 ? 'one' : 'other';
    }
};
