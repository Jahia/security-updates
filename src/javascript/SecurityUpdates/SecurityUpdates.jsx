import React, {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';
import {GET_SECURITY_REPORT, REFRESH_SECURITY_REPORT} from './SecurityUpdates.gql';
import {SettingsForm} from '../Settings/SettingsForm';
import {adminUrl, filterFindings, formatDateTime, safeHttpsUrl, sortFindings} from './securityUpdatesUtils';

const NS = 'security-updates';

// Feed text is untrusted: everything below is rendered as React text (escaped); links only for https URLs.

const SeverityLabel = ({severity}) => {
    const {t} = useTranslation(NS);
    return (
        <span className={`${styles.severity} ${styles[`severity_${severity}`] || ''}`}>
            {t(`page.severity.${severity}`)}
        </span>
    );
};

SeverityLabel.propTypes = {severity: PropTypes.string.isRequired};

const AdvisoryList = ({advisories}) => {
    const {t} = useTranslation(NS);
    return (
        <ul className={styles.advisories}>
            {advisories.map(advisory => {
                const url = safeHttpsUrl(advisory.url);
                const label = advisory.title ? `${advisory.id}: ${advisory.title}` : advisory.id;
                return (
                    <li key={advisory.id}>
                        {url ? (
                            <a href={url} target="_blank" rel="noopener noreferrer">
                                {label}
                                <span className={styles.srOnly}> {t('page.newTab')}</span>
                            </a>
                        ) : <span>{label}</span>}
                        {advisory.cves && advisory.cves.length > 0 && (
                            <span className={styles.cves}> ({advisory.cves.join(', ')})</span>
                        )}
                    </li>
                );
            })}
        </ul>
    );
};

AdvisoryList.propTypes = {
    advisories: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string.isRequired,
        title: PropTypes.string,
        url: PropTypes.string,
        cves: PropTypes.arrayOf(PropTypes.string)
    })).isRequired
};

const FindingsTable = ({findings}) => {
    const {t} = useTranslation(NS);
    return (
        <div className={styles.tableWrapper}>
            <table className={styles.table}>
                <caption className={styles.caption}>{t('page.table.caption')}</caption>
                <thead>
                    <tr>
                        <th scope="col">{t('page.table.component')}</th>
                        <th scope="col">{t('page.table.installed')}</th>
                        <th scope="col">{t('page.table.recommended')}</th>
                        <th scope="col">{t('page.table.compatibility')}</th>
                        <th scope="col">{t('page.table.severity')}</th>
                        <th scope="col">{t('page.table.source')}</th>
                        <th scope="col">{t('page.table.advisories')}</th>
                    </tr>
                </thead>
                <tbody>
                    {findings.map(finding => (
                        <tr key={`${finding.groupId}:${finding.component}:${finding.source}`}>
                            <th scope="row">
                                <span className={styles.component}>{finding.component}</span>
                                {finding.groupId && <span className={styles.groupId}>{finding.groupId}</span>}
                            </th>
                            <td>{finding.installedVersion}</td>
                            <td>{finding.recommendedVersion}</td>
                            <td>
                                {t(`page.compatibility.${finding.compatibility}`)}
                                {finding.requiredJahiaVersion && finding.compatibility !== 'JAHIA_UPGRADE' && (
                                    <span className={styles.secondary}>
                                        {t('page.requires', {version: finding.requiredJahiaVersion})}
                                    </span>
                                )}
                            </td>
                            <td><SeverityLabel severity={finding.severity}/></td>
                            <td>{t(`page.source.${finding.source}`)}</td>
                            <td><AdvisoryList advisories={finding.advisories}/></td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
};

FindingsTable.propTypes = {findings: PropTypes.arrayOf(PropTypes.object).isRequired};

const StoreErrors = ({errors}) => {
    const {t} = useTranslation(NS);
    if (!errors || errors.length === 0) {
        return null;
    }

    return (
        <section className={styles.errors} aria-labelledby="security-updates-errors-title">
            <h3 id="security-updates-errors-title" className={styles.subtitle}>{t('page.errors.title')}</h3>
            <ul aria-labelledby="security-updates-errors-title">
                {errors.map(error => (
                    <li key={`${error.storeUrl}:${error.kind}:${error.message}`}>
                        <strong>{t(`page.errorKind.${error.kind}`)}</strong>
                        {error.storeUrl && <span> {error.storeUrl}</span>}
                        {error.message && <span> ({error.message})</span>}
                    </li>
                ))}
            </ul>
        </section>
    );
};

StoreErrors.propTypes = {errors: PropTypes.arrayOf(PropTypes.object)};

const StatusNotice = ({report, hasFindings}) => {
    const {t} = useTranslation(NS);
    if (report.status === 'OK' && !hasFindings) {
        return <p className={`${styles.notice} ${styles.notice_ok}`}>{t('page.empty')}</p>;
    }

    if (report.status === 'OK') {
        return null;
    }

    return <p className={`${styles.notice} ${styles.notice_warning}`}>{t(`page.status.${report.status}`)}</p>;
};

StatusNotice.propTypes = {report: PropTypes.object.isRequired, hasFindings: PropTypes.bool.isRequired};

export const SecurityUpdates = () => {
    const {t, i18n} = useTranslation(NS);
    const [filter, setFilter] = useState('advisories');
    const [refreshed, setRefreshed] = useState(null);
    const [announcement, setAnnouncement] = useState('');
    const [refreshError, setRefreshError] = useState(false);
    const inFlight = useRef(false);
    const {data, loading, error} = useQuery(GET_SECURITY_REPORT, {fetchPolicy: 'network-only'});
    const [refreshReport] = useMutation(REFRESH_SECURITY_REPORT);

    useEffect(() => {
        const previousTitle = document.title;
        document.title = `${t('label')} — Jahia Administration`;
        return () => {
            document.title = previousTitle;
        };
    }, [t]);

    const report = refreshed || data?.securityUpdates?.report;
    const allFindings = sortFindings(report?.findings);
    const shown = filterFindings(allFindings, filter);
    const language = i18n?.language;

    const onRefresh = () => {
        // The button stays enabled (disabling it would drop the keyboard focus); a click during a check is ignored.
        if (inFlight.current) {
            return;
        }

        inFlight.current = true;
        setRefreshError(false);
        setAnnouncement(t('page.refreshing'));
        const previousCheck = report?.checkedAt;
        refreshReport()
            .then(result => {
                const next = result?.data?.securityUpdates?.refresh;
                if (next) {
                    setRefreshed(next);
                }

                setAnnouncement(next && next.checkedAt !== previousCheck ? t('page.refreshed') : t('page.notRefreshed'));
            })
            .catch(() => {
                setRefreshError(true);
                setAnnouncement(t('page.refreshError'));
            })
            .finally(() => {
                inFlight.current = false;
            });
    };

    let statusText = announcement;
    if (loading) {
        statusText = t('page.loading');
    }

    return (
        <section className={styles.container} aria-labelledby="security-updates-title">
            <h2 id="security-updates-title" className={styles.title}>{t('label')}</h2>
            <p className={styles.intro}>{t('page.intro')}</p>

            <p role="status" aria-live="polite" aria-atomic="true" className={styles.announcement}>{statusText}</p>

            {loading && <div className={styles.loader} aria-hidden="true"><Loader size="big"/></div>}

            {error && <p role="alert" className={`${styles.notice} ${styles.notice_error}`}>{t('page.error')}</p>}

            {!loading && !error && report && (
                <>
                    <dl className={styles.meta}>
                        <dt>{t('page.jahiaVersion')}</dt>
                        <dd>{report.jahiaVersion || t('page.unknown')}</dd>
                        <dt>{t('page.checkedAt')}</dt>
                        <dd>{formatDateTime(report.checkedAt, language) || t('page.never')}</dd>
                        <dt>{t('page.lastSuccess')}</dt>
                        <dd>{formatDateTime(report.lastSuccess, language) || t('page.never')}</dd>
                    </dl>

                    <div className={styles.actions}>
                        <Button
                            label={t('page.refresh')}
                            variant="primary"
                            onClick={onRefresh}
                        />
                        <a className={styles.link} href={adminUrl('manageModules')}>{t('page.manageModules')}</a>
                    </div>

                    {refreshError && <p role="alert" className={`${styles.notice} ${styles.notice_error}`}>{t('page.refreshError')}</p>}
                    <StatusNotice report={report} hasFindings={allFindings.length > 0}/>
                    <StoreErrors errors={report.errors}/>

                    {allFindings.length > 0 && (
                        <>
                            <fieldset className={styles.filter}>
                                <legend>{t('page.filter.legend')}</legend>
                                <label>
                                    <input
                                        type="radio"
                                        name="security-updates-filter"
                                        value="advisories"
                                        checked={filter === 'advisories'}
                                        onChange={() => setFilter('advisories')}
                                    />
                                    {t('page.filter.advisories')}
                                </label>
                                <label>
                                    <input
                                        type="radio"
                                        name="security-updates-filter"
                                        value="all"
                                        checked={filter === 'all'}
                                        onChange={() => setFilter('all')}
                                    />
                                    {t('page.filter.all')}
                                </label>
                            </fieldset>
                            {shown.length > 0 ? <FindingsTable findings={shown}/> : <p className={styles.notice}>{t('page.filteredEmpty')}</p>}
                        </>
                    )}
                </>
            )}

            <SettingsForm/>
        </section>
    );
};

export default SecurityUpdates;
