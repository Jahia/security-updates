import {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Chip, Header, LayoutContent, Loader, Typography} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';
import {GET_SECURITY_REPORT, REFRESH_SECURITY_REPORT} from './SecurityUpdates.gql';
import {SettingsForm} from '../Settings/SettingsForm';
import {NS, PAGE_EVENT, QUERY_FETCH_POLICY} from '../common/constants';
import {values} from '../common/i18n';
import {StatusMessage} from './StatusMessage';
import {StoreErrors} from './StoreErrors';
import {Findings} from './Findings';
import {ReportDates, ReportStatus} from './ReportOverview';
import {adminUrl, sortFindings} from './securityUpdatesUtils';
import {chipColor, viewStatus} from './reportView';
import {useCheck} from './useCheck';

// Built from Moonstone (LayoutContent, Header, Table, Chip, RadioGroup, Typography, Button) like the other Jahia
// administration pages. Feed text is untrusted: it is only rendered as React text, and links only for https URLs.

const useDocumentTitle = title => {
    useEffect(() => {
        const previousTitle = document.title;
        document.title = title;
        return () => {
            document.title = previousTitle;
        };
    }, [title]);
};

// Tells the banner that the page is open (the banner is hidden here: the page says more).
const usePageEvents = () => {
    useEffect(() => {
        window.dispatchEvent(new CustomEvent(PAGE_EVENT, {detail: {isOpen: true}}));
        return () => window.dispatchEvent(new CustomEvent(PAGE_EVENT, {detail: {isOpen: false}}));
    }, []);
};

// Live regions announce changes, not the content they are created with: the status region starts empty and is filled
// once the page is in the document.
const useMounted = () => {
    const [isMounted, setMounted] = useState(false);
    useEffect(() => setMounted(true), []);
    return isMounted;
};

/**
 * The content of the status region, its one visible indicator: the loading, then the running check (with a loader),
 * then the last announcement (the outcome of the check, or why it did not run).
 */
const PageStatus = ({isLoading, check}) => {
    const {t} = useTranslation(NS);
    if (isLoading) {
        return t('page.loading');
    }

    if (check.isChecking) {
        return (
            <span className={styles.checking} data-testid="security-updates-checking">
                <span aria-hidden="true"><Loader size="small"/></span>
                <Typography component="span">
                    {t(check.isUserCheck ? 'page.refreshing' : 'page.running')}
                    {check.isAlreadyRunning && <> {t('page.alreadyRunning')}</>}
                </Typography>
            </span>
        );
    }

    return check.announcement ? <StatusMessage tone={check.hasFailed ? 'warning' : 'info'}>{check.announcement}</StatusMessage> : null;
};

PageStatus.propTypes = {
    isLoading: PropTypes.bool,
    check: PropTypes.shape({
        isChecking: PropTypes.bool.isRequired,
        isUserCheck: PropTypes.bool.isRequired,
        isAlreadyRunning: PropTypes.bool.isRequired,
        hasFailed: PropTypes.bool.isRequired,
        announcement: PropTypes.string.isRequired
    }).isRequired
};

export const SecurityUpdates = () => {
    const {t, i18n} = useTranslation(NS);
    const {data, loading, error, refetch} = useQuery(GET_SECURITY_REPORT, {fetchPolicy: QUERY_FETCH_POLICY});
    const [refreshReport] = useMutation(REFRESH_SECURITY_REPORT);
    const report = data?.securityUpdates?.report;
    const check = useCheck({report, refetch, refreshReport, t, language: i18n?.language});
    const isMounted = useMounted();
    useDocumentTitle(t('page.documentTitle', values({title: t('label')})));
    usePageEvents();

    const findings = sortFindings(report?.findings);
    const isDisabled = report?.status === 'DISABLED';
    const status = report ? viewStatus(report) : null;

    const header = (
        <Header
            title={t('label')}
            mainActions={isDisabled ? null : (
                <Button
                    size="big"
                    color="accent"
                    className={styles.target}
                    label={check.isChecking ? t('page.checking') : t('page.refresh')}
                    aria-busy={check.isChecking ? 'true' : undefined}
                    onClick={check.onRefresh}
                />
            )}
            status={status ? <Chip color={chipColor(status)} label={t(`page.statusLabel.${status}`)}/> : null}
        />
    );

    const content = (
        <div className={styles.page}>
            <Typography className={styles.text}>{t('page.intro')}</Typography>

            {/* Always in the page, so that screen readers follow it from the start; empty until the page is mounted. */}
            <div role="status" aria-live="polite" aria-atomic="true" className={styles.announcement} data-testid="security-updates-status">
                {isMounted && <PageStatus isLoading={Boolean(loading)} check={check}/>}
            </div>

            {loading && <div className={styles.loader} aria-hidden="true"><Loader size="big"/></div>}

            {error && <StatusMessage tone="warning" role="alert">{t('page.error')}</StatusMessage>}

            {!loading && !error && report && (
                <>
                    <ReportDates report={report}/>
                    <Typography component="a" className={styles.link} href={adminUrl('manageModules')}>{t('page.manageModules')}</Typography>
                    <ReportStatus report={{...report, findings}}/>
                    <StoreErrors errors={report.errors}/>
                    {findings.length > 0 && !isDisabled && <Findings findings={findings}/>}
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
