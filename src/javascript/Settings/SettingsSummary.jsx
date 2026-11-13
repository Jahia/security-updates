import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Typography} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {NS} from '../common/constants';
import {BOOLEAN_FIELDS} from './settingsValues';
import {normalizeHex} from './colorUtils';
import {BannerPreview} from './BannerPreview';

/** The settings of each group, in the order of the form. */
export const GROUPS = {
    checks: ['enabled', 'refreshIntervalHours', 'useForgeSettings', 'defaultStoreUrl', 'extraFeedUrls'],
    banner: ['bannerEnabled',
        'bannerMinimumSeverity',
        'bannerIncludeOwnerReports',
        'bannerBackgroundColor',
        'bannerTextColor',
        'bannerDismissPolicy',
        'bannerDismissHours']
};

const COLOR_FIELDS = ['bannerBackgroundColor', 'bannerTextColor'];

/** A setting's value in words: on or off, the label of a choice, the text, or "not set". */
const valueText = (t, field, value) => {
    if (BOOLEAN_FIELDS.includes(field)) {
        return t(value ? 'settings.value.on' : 'settings.value.off');
    }

    if (String(value ?? '').trim() === '') {
        return t('settings.value.none');
    }

    if (field === 'bannerMinimumSeverity') {
        return t(`page.severity.${value}`);
    }

    if (field === 'bannerDismissPolicy') {
        return t(`settings.dismiss.${value}`);
    }

    return String(value);
};

const Value = ({field, value}) => {
    const {t} = useTranslation(NS);
    const hex = COLOR_FIELDS.includes(field) ? normalizeHex(value) : null;
    return (
        <Typography component="dd" className={styles.summaryValue}>
            {hex && <span aria-hidden="true" className={styles.swatch} style={{backgroundColor: hex}}/>}
            {valueText(t, field, value)}
        </Typography>
    );
};

Value.propTypes = {field: PropTypes.string.isRequired, value: PropTypes.oneOfType([PropTypes.string, PropTypes.bool])};

/**
 * The settings of a user who cannot change them, as text: a list of each setting and its value per group, at full
 * contrast (a disabled form would grey them out and take them out of the keyboard order), then the banner preview.
 */
export const SettingsSummary = ({form}) => {
    const {t} = useTranslation(NS);
    return (
        <div className={styles.summary} data-testid="security-updates-settings-summary">
            {Object.entries(GROUPS).map(([group, fields]) => (
                <div key={group} className={styles.group}>
                    <Typography component="h3" variant="subheading" weight="bold">{t(`settings.groups.${group}`)}</Typography>
                    <dl className={styles.summaryList}>
                        {fields.map(field => (
                            <div key={field} className={styles.summaryRow} data-testid={`security-updates-setting-value-${field}`}>
                                <Typography component="dt" weight="bold">{t(`settings.fields.${field}.label`)}</Typography>
                                <Value field={field} value={form[field]}/>
                            </div>
                        ))}
                    </dl>
                </div>
            ))}
            <BannerPreview isReadOnly form={form} onApply={() => undefined}/>
        </div>
    );
};

SettingsSummary.propTypes = {form: PropTypes.object.isRequired};
