import {useTranslation} from 'react-i18next';
import {Typography} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';
import {NS} from '../common/constants';
import {values} from '../common/i18n';
import {formatDateTime} from './securityUpdatesUtils';
import {statusMessage} from './reportView';
import {StatusMessage} from './StatusMessage';
import {reportShape} from './propTypes';

/** When the report was made and when the next check runs. */
export const ReportDates = ({report}) => {
    const {t, i18n} = useTranslation(NS);
    const date = (iso, fallback) => formatDateTime(iso, i18n?.language) || t(fallback);
    const rows = [
        ['page.jahiaVersion', report.jahiaVersion || t('page.unknown')],
        ['page.checkedAt', date(report.checkedAt, 'page.never')],
        ['page.lastSuccess', date(report.lastSuccess, 'page.never')],
        ['page.nextCheckAt', date(report.nextCheckAt, 'page.notScheduled')]
    ];
    return (
        <dl className={styles.meta}>
            {rows.map(([label, value]) => (
                <div key={label} className={styles.metaRow}>
                    <Typography component="dt" weight="bold">{t(label)}</Typography>
                    <Typography component="dd">{value}</Typography>
                </div>
            ))}
        </dl>
    );
};

ReportDates.propTypes = {report: reportShape.isRequired};

/** The text of a status line of reportView.statusMessage (the stale one says since when). */
export const statusText = (message, report, t, language) => {
    const vars = message.isStale ? values({date: formatDateTime(report.lastSuccess, language) || t('page.never')}) : undefined;
    return t(message.key, vars);
};

/** What the report means, in one sentence (none when the table says it all). */
export const ReportStatus = ({report}) => {
    const {t, i18n} = useTranslation(NS);
    const message = statusMessage(report);
    if (!message) {
        return null;
    }

    return <StatusMessage tone={message.tone}>{statusText(message, report, t, i18n?.language)}</StatusMessage>;
};

ReportStatus.propTypes = {report: reportShape.isRequired};
