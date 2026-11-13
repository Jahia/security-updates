import React, {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {
    Button,
    Chip,
    Header,
    LayoutContent,
    Loader,
    RadioGroup,
    RadioItem,
    Table,
    TableBody,
    TableBodyCell,
    TableHead,
    TableHeadCell,
    TableRow,
    Typography,
    Warning
} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';
import {GET_SECURITY_REPORT, REFRESH_SECURITY_REPORT} from './SecurityUpdates.gql';
import {SettingsForm} from '../Settings/SettingsForm';
import {StatusMessage} from './StatusMessage';
import {adminUrl, filterFindings, formatDateTime, safeHttpsUrl, sortFindings} from './securityUpdatesUtils';

const NS = 'security-updates';

// Built from Moonstone (LayoutContent, Header, Table, Chip, RadioGroup, Typography, Button) like the other Jahia
// administration pages. Feed text is untrusted: it is only rendered as React text, and links only for https URLs.

const SEVERITY_COLORS = {CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'accent', LOW: 'default', UNKNOWN: 'default'};

const SeverityChip = ({severity}) => {
    const {t} = useTranslation(NS);
    // The severity is written out; the colour only reinforces it.
    const urgent = severity === 'CRITICAL' || severity === 'HIGH';
    return (
        <Chip
            color={SEVERITY_COLORS[severity] || 'default'}
            label={t(`page.severity.${severity}`)}
            icon={urgent ? <Warning aria-hidden="true"/> : undefined}
        />
    );
};

SeverityChip.propTypes = {severity: PropTypes.string.isRequired};

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
                            <Typography component="a" className={styles.link} href={url} target="_blank" rel="noopener noreferrer">
                                {label}
                                <span className={styles.srOnly}> {t('page.newTab')}</span>
                            </Typography>
                        ) : <Typography component="span">{label}</Typography>}
                        {advisory.cves && advisory.cves.length > 0 && (
                            <Typography component="span" variant="caption"> ({advisory.cves.join(', ')})</Typography>
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

const COLUMNS = ['component', 'installed', 'recommended', 'compatibility', 'severity', 'source', 'advisories'];

const FindingsTable = ({findings}) => {
    const {t} = useTranslation(NS);
    return (
        <div className={styles.tableWrapper}>
            <Table>
                <caption className={styles.caption}>
                    <Typography component="span" variant="heading">{t('page.table.caption')}</Typography>
                </caption>
                <TableHead>
                    {/* Moonstone makes every row a tab stop; these rows have no action, so they are taken out of the tab order. */}
                    <TableRow tabIndex={-1}>
                        {COLUMNS.map(column => <TableHeadCell key={column} scope="col">{t(`page.table.${column}`)}</TableHeadCell>)}
                    </TableRow>
                </TableHead>
                <TableBody>
                    {findings.map(finding => (
                        <TableRow key={`${finding.groupId}:${finding.component}:${finding.source}`} tabIndex={-1}>
                            <TableBodyCell component="th" scope="row" verticalAlign="top">
                                <Typography component="span" weight="bold">{finding.component}</Typography>
                                {finding.groupId && <Typography component="span" variant="caption" className={styles.block}>{finding.groupId}</Typography>}
                            </TableBodyCell>
                            <TableBodyCell verticalAlign="top">{finding.installedVersion}</TableBodyCell>
                            <TableBodyCell verticalAlign="top">{finding.recommendedVersion}</TableBodyCell>
                            <TableBodyCell verticalAlign="top">
                                <Typography component="span">{t(`page.compatibility.${finding.compatibility}`)}</Typography>
                                {finding.requiredJahiaVersion && finding.compatibility !== 'JAHIA_UPGRADE' && (
                                    <Typography component="span" variant="caption" className={styles.block}>
                                        {t('page.requires', {version: finding.requiredJahiaVersion})}
                                    </Typography>
                                )}
                            </TableBodyCell>
                            <TableBodyCell verticalAlign="top"><SeverityChip severity={finding.severity}/></TableBodyCell>
                            <TableBodyCell verticalAlign="top">
                                <Chip
                                    color={finding.source === 'ADVISORY' ? 'accent' : 'default'}
                                    label={t(`page.source.${finding.source}`)}
                                />
                            </TableBodyCell>
                            <TableBodyCell verticalAlign="top"><AdvisoryList advisories={finding.advisories}/></TableBodyCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
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
        <section className={styles.section} aria-labelledby="security-updates-errors-title">
            <Typography component="h2" variant="heading" id="security-updates-errors-title">{t('page.errors.title')}</Typography>
            <ul className={styles.list} aria-labelledby="security-updates-errors-title">
                {errors.map(error => (
                    <li key={`${error.storeUrl}:${error.kind}:${error.message}`}>
                        <StatusMessage tone="warning">
                            <strong>{t(`page.errorKind.${error.kind}`)}</strong>
                            {error.storeUrl && <span> {error.storeUrl}</span>}
                            {error.message && <span> ({error.message})</span>}
                        </StatusMessage>
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
        return <StatusMessage tone="info">{t('page.empty')}</StatusMessage>;
    }

    if (report.status === 'OK') {
        return null;
    }

    return <StatusMessage tone="warning">{t(`page.status.${report.status}`)}</StatusMessage>;
};

StatusNotice.propTypes = {report: PropTypes.object.isRequired, hasFindings: PropTypes.bool.isRequired};

const STATUS_CHIP_COLORS = {OK: 'success', PARTIAL: 'warning', UNAVAILABLE: 'danger', DISABLED: 'default', NOT_CHECKED: 'default'};

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

    const statusText = loading ? t('page.loading') : announcement;

    const header = (
        <Header
            title={t('label')}
            mainActions={<Button size="big" color="accent" label={t('page.refresh')} onClick={onRefresh}/>}
            status={report ? (
                <Chip
                    color={STATUS_CHIP_COLORS[report.status] || 'default'}
                    label={t(`page.statusLabel.${report.status}`)}
                />
            ) : null}
        />
    );

    const content = (
        <div className={styles.page}>
            <Typography>{t('page.intro')}</Typography>

            <Typography role="status" aria-live="polite" aria-atomic="true" className={styles.announcement}>{statusText}</Typography>

            {loading && <div className={styles.loader} aria-hidden="true"><Loader size="big"/></div>}

            {error && <StatusMessage tone="warning" role="alert">{t('page.error')}</StatusMessage>}

            {!loading && !error && report && (
                <>
                    <dl className={styles.meta}>
                        <Typography component="dt" weight="bold">{t('page.jahiaVersion')}</Typography>
                        <Typography component="dd">{report.jahiaVersion || t('page.unknown')}</Typography>
                        <Typography component="dt" weight="bold">{t('page.checkedAt')}</Typography>
                        <Typography component="dd">{formatDateTime(report.checkedAt, language) || t('page.never')}</Typography>
                        <Typography component="dt" weight="bold">{t('page.lastSuccess')}</Typography>
                        <Typography component="dd">{formatDateTime(report.lastSuccess, language) || t('page.never')}</Typography>
                    </dl>

                    <Typography component="a" className={styles.link} href={adminUrl('manageModules')}>{t('page.manageModules')}</Typography>

                    {refreshError && <StatusMessage tone="warning" role="alert">{t('page.refreshError')}</StatusMessage>}
                    <StatusNotice report={report} hasFindings={allFindings.length > 0}/>
                    <StoreErrors errors={report.errors}/>

                    {allFindings.length > 0 && (
                        <>
                            <fieldset className={styles.filter}>
                                <Typography component="legend" weight="bold">{t('page.filter.legend')}</Typography>
                                <RadioGroup name="security-updates-filter" value={filter} onChange={(event, value) => setFilter(value)}>
                                    <RadioItem id="security-updates-filter-advisories" value="advisories" label={t('page.filter.advisories')}/>
                                    <RadioItem id="security-updates-filter-all" value="all" label={t('page.filter.all')}/>
                                </RadioGroup>
                            </fieldset>
                            {shown.length > 0 ? <FindingsTable findings={shown}/> : <StatusMessage tone="info">{t('page.filteredEmpty')}</StatusMessage>}
                        </>
                    )}
                </>
            )}

            <SettingsForm/>
        </div>
    );

    // The loader is rendered in the content rather than through LayoutContent's isLoading, which would also hide the
    // status region that announces the loading.
    return <LayoutContent hasPadding header={header} content={content}/>;
};

export default SecurityUpdates;
