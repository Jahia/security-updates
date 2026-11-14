import {useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {RadioGroup, RadioItem, Typography} from '@jahia/moonstone';
import {Trans} from 'react-i18next';
import styles from './SecurityUpdates.scss';
import {JAHIA_UPGRADE_URL, NS, THRESHOLD_SEVERITIES} from '../common/constants';
import {pluralKey, values} from '../common/i18n';
import {adminUrl, filterFindings} from './securityUpdatesUtils';
import {countBySource, defaultFilter, severitySummary} from './reportView';
import {FindingsTable} from './FindingsTable';
import {ExternalLink} from './AdvisoryList';
import {StatusMessage} from './StatusMessage';
import {bannerShape, findingShape} from './propTypes';

const TITLE_ID = 'security-updates-findings-title';
const HINT_ID = 'security-updates-filter-hint';

const Summary = ({findings}) => {
    const {t, i18n} = useTranslation(NS);
    const {total, bySeverity} = severitySummary(findings);
    if (total === 0) {
        return null;
    }

    const list = bySeverity
        .map(({severity, n}) => t('page.summary.severityCount', values({severity: t(`page.severity.${severity}`), n})))
        .join(', ');
    return (
        <>
            <Typography>{t(`page.summary.${pluralKey(total, i18n?.language)}`, values({n: total}))}</Typography>
            <Typography>{t('page.summary.bySeverity', values({list}))}</Typography>
        </>
    );
};

Summary.propTypes = {findings: PropTypes.arrayOf(findingShape).isRequired};

/**
 * The banner counts the components at or above its threshold (and the module owner reports only when set to): when
 * that is not every component listed here, the page says what the banner counts, so that the two numbers agree.
 */
const BannerCount = ({banner, findings}) => {
    const {t, i18n} = useTranslation(NS);
    const count = Number(banner?.count) || 0;
    if (!banner?.visible || count === severitySummary(findings).total) {
        return null;
    }

    const severity = THRESHOLD_SEVERITIES.includes(banner.minimumSeverity) ? banner.minimumSeverity : 'HIGH';
    return (
        <Typography className={styles.text} data-testid="security-updates-banner-count">
            {t(`page.bannerCount.${pluralKey(count, i18n?.language)}`, values({n: count, threshold: t(`banner.threshold.${severity}`)}))}
        </Typography>
    );
};

BannerCount.propTypes = {banner: bannerShape, findings: PropTypes.arrayOf(findingShape).isRequired};

/** The "Components to update" section: summary, filter (Jahia advisories / all), table and what to do next. */
export const Findings = ({findings, banner}) => {
    const {t, i18n} = useTranslation(NS);
    const [filter, setFilter] = useState(() => defaultFilter(findings, window.location.search));
    // Said after a change of filter only (one short message per change), never when the page opens.
    const [filterStatus, setFilterStatus] = useState('');
    const onFilter = value => {
        setFilter(value);
        const {total} = severitySummary(filterFindings(findings, value));
        // An empty view says why it is empty and what to do, as the page does, not only "0 components".
        setFilterStatus(total === 0 ?
            t('page.filteredEmpty') :
            t(`page.filter.showing.${pluralKey(total, i18n?.language)}`, values({n: total})));
    };

    const shown = filterFindings(findings, filter);
    const counts = countBySource(findings);
    return (
        <div className={styles.section}>
            <Typography component="h2" variant="heading" id={TITLE_ID}>{t('page.findingsTitle')}</Typography>
            <Summary findings={shown}/>
            <BannerCount banner={banner} findings={findings}/>
            <fieldset className={styles.filter} aria-describedby={HINT_ID}>
                <Typography component="legend" weight="bold">{t('page.filter.legend')}</Typography>
                <Typography id={HINT_ID} className={styles.hint}>{t('page.filter.hint')}</Typography>
                <RadioGroup name="security-updates-filter" value={filter} onChange={(event, value) => onFilter(value)}>
                    <RadioItem
                        id="security-updates-filter-advisories"
                        className={styles.radio}
                        value="advisories"
                        label={t('page.filter.advisories', values({n: counts.advisories}))}
                    />
                    <RadioItem
                        id="security-updates-filter-all"
                        className={styles.radio}
                        value="all"
                        label={t('page.filter.all', values({n: counts.all}))}
                    />
                </RadioGroup>
            </fieldset>
            <p role="status" className={styles.srOnly} data-testid="security-updates-filter-status">{filterStatus}</p>
            {shown.length > 0 ?
                <FindingsTable findings={shown} labelledBy={TITLE_ID}/> :
                <StatusMessage tone="info">{t('page.filteredEmpty')}</StatusMessage>}
            {/* The places it names are links, to the same targets as the rows' actions. */}
            <Typography className={styles.text} data-testid="security-updates-after-table">
                <Trans
                    t={t}
                    i18nKey="page.afterTable"
                    components={{
                        manage: <a className={styles.inlineLink} href={adminUrl('manageModules')}/>,
                        upgrade: <ExternalLink href={JAHIA_UPGRADE_URL} className={styles.inlineLink}/>
                    }}
                />
            </Typography>
        </div>
    );
};

Findings.propTypes = {findings: PropTypes.arrayOf(findingShape).isRequired, banner: bannerShape};
