import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Chip, Table, TableBody, TableBodyCell, TableHead, TableHeadCell, TableRow, Typography, Warning} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';
import {JAHIA_UPGRADE_URL, NS} from '../common/constants';
import {values} from '../common/i18n';
import {adminUrl} from './securityUpdatesUtils';
import {actionFor, chipColor} from './reportView';
import {AdvisoryList, ExternalLink} from './AdvisoryList';
import {findingShape} from './propTypes';

const COLUMNS = ['component', 'severity', 'installed', 'recommended', 'compatibility', 'source', 'advisories', 'action'];

const SeverityChip = ({severity}) => {
    const {t} = useTranslation(NS);
    // The severity is written out; the colour and the icon only reinforce it.
    const isUrgent = severity === 'CRITICAL' || severity === 'HIGH';
    return (
        <Chip
            color={chipColor(severity)}
            label={t(`page.severity.${severity}`)}
            icon={isUrgent ? <Warning aria-hidden="true"/> : undefined}
        />
    );
};

SeverityChip.propTypes = {severity: PropTypes.string.isRequired};

const Action = ({finding}) => {
    const {t} = useTranslation(NS);
    if (actionFor(finding) === 'core') {
        return <ExternalLink href={JAHIA_UPGRADE_URL}>{t('page.action.core')}</ExternalLink>;
    }

    return (
        <Typography component="a" className={styles.link} href={adminUrl('manageModules')}>
            {t('page.action.module', values({component: finding.component}))}
        </Typography>
    );
};

Action.propTypes = {finding: findingShape.isRequired};

const Compatibility = ({finding}) => {
    const {t} = useTranslation(NS);
    return (
        <>
            <Typography component="span">{t(`page.compatibility.${finding.compatibility}`)}</Typography>
            {finding.requiredJahiaVersion && finding.compatibility !== 'JAHIA_UPGRADE' && (
                <Typography component="span" variant="caption" className={styles.block}>
                    {t('page.requires', values({version: finding.requiredJahiaVersion}))}
                </Typography>
            )}
        </>
    );
};

Compatibility.propTypes = {finding: findingShape.isRequired};

/**
 * The findings, in a table scrolling horizontally inside a focusable region named by the section heading. Moonstone
 * lays its table out with flex boxes (display: flex on the rows): the explicit table roles keep the table semantics
 * for assistive technologies whatever the CSS display, and the module's row class lets rows grow with their content.
 */
export const FindingsTable = ({findings, labelledBy}) => {
    const {t} = useTranslation(NS);
    return (
        // A scrolling region must be reachable with the keyboard to be scrolled.
        <div className={styles.tableWrapper} role="region" tabIndex={0} aria-labelledby={labelledBy}>
            <Table role="table" className={styles.table}>
                <caption className={styles.caption}>
                    <Typography component="span" variant="subheading" weight="bold">{t('page.table.caption')}</Typography>
                </caption>
                <TableHead role="rowgroup">
                    {/* Moonstone makes every row a tab stop; these rows have no action of their own, their links do. */}
                    <TableRow role="row" className={styles.row} tabIndex={-1}>
                        {COLUMNS.map(column => <TableHeadCell key={column} role="columnheader" scope="col">{t(`page.table.${column}`)}</TableHeadCell>)}
                    </TableRow>
                </TableHead>
                <TableBody role="rowgroup">
                    {findings.map(finding => (
                        <TableRow key={`${finding.groupId}:${finding.component}:${finding.source}:${finding.installedVersion}`} role="row" className={styles.row} tabIndex={-1}>
                            <TableBodyCell component="th" role="rowheader" scope="row" verticalAlign="top">
                                <Typography component="span" weight="bold">{finding.component}</Typography>
                                {finding.groupId && <Typography component="span" variant="caption" className={styles.block}>{finding.groupId}</Typography>}
                            </TableBodyCell>
                            <TableBodyCell role="cell" verticalAlign="top"><SeverityChip severity={finding.severity}/></TableBodyCell>
                            <TableBodyCell role="cell" verticalAlign="top">{finding.installedVersion}</TableBodyCell>
                            <TableBodyCell role="cell" verticalAlign="top">{finding.recommendedVersion}</TableBodyCell>
                            <TableBodyCell role="cell" verticalAlign="top"><Compatibility finding={finding}/></TableBodyCell>
                            <TableBodyCell role="cell" verticalAlign="top">
                                <Chip color={chipColor(finding.source === 'ADVISORY' ? 'MEDIUM' : 'other')} label={t(`page.source.${finding.source}`)}/>
                            </TableBodyCell>
                            <TableBodyCell role="cell" verticalAlign="top">
                                <AdvisoryList advisories={finding.advisories} isOwner={finding.source !== 'ADVISORY'}/>
                            </TableBodyCell>
                            <TableBodyCell role="cell" verticalAlign="top"><Action finding={finding}/></TableBodyCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </div>
    );
};

FindingsTable.propTypes = {findings: PropTypes.arrayOf(findingShape).isRequired, labelledBy: PropTypes.string.isRequired};
