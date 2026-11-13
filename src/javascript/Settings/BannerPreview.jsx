import {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Button, Typography} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {DEFAULT_BANNER_BACKGROUND, DEFAULT_BANNER_TEXT, NS} from '../common/constants';
import {values} from '../common/i18n';
import {contrastRatio, formatRatio, MIN_CONTRAST, normalizeHex} from './colorUtils';
import {suggestColor} from './contrastSuggestions';
import {StatusMessage} from '../SecurityUpdates/StatusMessage';

/** Pause after the last change before the verdict is announced, so that typing a colour is not read out key by key. */
export const ANNOUNCE_DELAY_MS = 800;

/**
 * Announces the contrast verdict only when it changes between passing and failing, once the user pauses; the
 * visible verdict is not a live region.
 */
const useVerdictAnnouncement = verdict => {
    const {t} = useTranslation(NS);
    const [announcement, setAnnouncement] = useState('');
    const announced = useRef(verdict);
    useEffect(() => {
        if (verdict === 'invalid' || verdict === announced.current) {
            return undefined;
        }

        const timer = setTimeout(() => {
            announced.current = verdict;
            setAnnouncement(t(verdict === 'pass' ? 'settings.preview.announcePass' : 'settings.preview.announceFail'));
        }, ANNOUNCE_DELAY_MS);
        return () => clearTimeout(timer);
    }, [verdict, t]);
    return announcement;
};

/** One suggested colour: a decorative swatch, the value and ratio as text, and a button naming the value. */
const Suggestion = ({kind, suggestion, onUse}) => {
    const {t} = useTranslation(NS);
    return (
        <li className={styles.suggestion} data-testid={`security-updates-suggestion-${kind}`}>
            <span aria-hidden="true" className={styles.swatch} style={{backgroundColor: suggestion.value}}/>
            <Typography component="span">{t(`settings.suggest.${kind}`, values({value: suggestion.value, ratio: suggestion.ratio}))}</Typography>
            <Button
                variant="outlined"
                className={styles.target}
                label={t(`settings.suggest.use_${kind}`, values({value: suggestion.value}))}
                onClick={() => onUse(suggestion.value)}
            />
        </li>
    );
};

Suggestion.propTypes = {
    kind: PropTypes.oneOf(['text', 'background']).isRequired,
    suggestion: PropTypes.shape({value: PropTypes.string.isRequired, ratio: PropTypes.string.isRequired}).isRequired,
    onUse: PropTypes.func.isRequired
};

const Suggestions = ({background, text, onApply}) => {
    const {t} = useTranslation(NS);
    const textSuggestion = suggestColor(background, text);
    const backgroundSuggestion = suggestColor(text, background);
    return (
        <div className={styles.suggestions} data-testid="security-updates-suggestions">
            {(textSuggestion || backgroundSuggestion) ? (
                <>
                    <Typography>{t('settings.suggest.intro')}</Typography>
                    <ul className={styles.suggestionList}>
                        {textSuggestion && <Suggestion kind="text" suggestion={textSuggestion} onUse={value => onApply('bannerTextColor', value)}/>}
                        {backgroundSuggestion && (
                            <Suggestion kind="background" suggestion={backgroundSuggestion} onUse={value => onApply('bannerBackgroundColor', value)}/>
                        )}
                    </ul>
                </>
            ) : (
                <>
                    <Typography>{t('settings.suggest.none')}</Typography>
                    <Button
                        variant="outlined"
                        className={styles.target}
                        label={t('settings.suggest.useDefaults', values({background: DEFAULT_BANNER_BACKGROUND, text: DEFAULT_BANNER_TEXT}))}
                        onClick={() => onApply(null)}
                    />
                </>
            )}
        </div>
    );
};

Suggestions.propTypes = {background: PropTypes.string.isRequired, text: PropTypes.string.isRequired, onApply: PropTypes.func.isRequired};

/**
 * Preview of the banner with the edited colours, even when they fail (then labelled as colours that cannot be
 * saved); the default pair is only used while a code is not a colour. The sample is decorative for screen readers:
 * the verdict says what matters.
 */
export const BannerPreview = ({form, onApply, isReadOnly}) => {
    const {t, i18n} = useTranslation(NS);
    const background = normalizeHex(form.bannerBackgroundColor);
    const text = normalizeHex(form.bannerTextColor);
    const ratio = contrastRatio(background, text);
    let verdict = 'invalid';
    if (ratio !== null) {
        verdict = ratio >= MIN_CONTRAST ? 'pass' : 'fail';
    }

    const announcement = useVerdictAnnouncement(verdict);
    const threshold = t(`banner.threshold.${form.bannerMinimumSeverity}`);
    const isValid = ratio !== null;
    return (
        <div className={styles.preview}>
            <Typography component="h3" variant="subheading" weight="bold">{t('settings.preview.title')}</Typography>
            {verdict !== 'pass' && <Typography weight="bold">{t('settings.preview.failLabel')}</Typography>}
            <div
                aria-hidden="true"
                lang={i18n?.language}
                data-testid="security-updates-banner-preview"
                className={styles.sample}
                style={{backgroundColor: isValid ? background : DEFAULT_BANNER_BACKGROUND, color: isValid ? text : DEFAULT_BANNER_TEXT}}
            >
                <p>{t('banner.message.other', values({n: 3, threshold}))}</p>
                <span className={styles.sampleLink}>{t('banner.link')}</span>
            </div>
            <StatusMessage tone={verdict === 'pass' ? 'info' : 'warning'} data-testid="security-updates-contrast">
                {isValid && <>{t('settings.preview.ratio')} <strong>{formatRatio(ratio)}:1</strong> </>}
                {t(`settings.preview.${verdict}`)}
            </StatusMessage>
            <p role="status" className={styles.srOnly} data-testid="security-updates-contrast-status">{announcement}</p>
            {verdict === 'fail' && !isReadOnly && <Suggestions background={background} text={text} onApply={onApply}/>}
        </div>
    );
};

BannerPreview.propTypes = {form: PropTypes.object.isRequired, onApply: PropTypes.func.isRequired, isReadOnly: PropTypes.bool};
