import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {OpenInNew, Typography} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';
import {NS} from '../common/constants';
import {values} from '../common/i18n';
import {safeHttpsUrl} from './securityUpdatesUtils';
import {hostOf, isJahiaStoreHost} from './reportView';
import {advisoryShape} from './propTypes';

const CVE = /^CVE-\d{4}-\d{4,}$/;

/**
 * A link opening in a new tab: a visible, decorative Moonstone icon, and the same said in words to screen readers.
 * Children are optional for react-i18next's Trans, which gives them.
 */
export const ExternalLink = ({href, className, children}) => {
    const {t} = useTranslation(NS);
    return (
        <Typography component="a" className={className || styles.link} href={href} target="_blank" rel="noopener noreferrer">
            {children}
            <OpenInNew aria-hidden="true" size="small" className={styles.newTabIcon} data-testid="security-updates-new-tab-icon"/>
            <span className={styles.srOnly}> {t('page.newTab')}</span>
        </Typography>
    );
};

ExternalLink.propTypes = {href: PropTypes.string.isRequired, className: PropTypes.string, children: PropTypes.node};

/**
 * Where an advisory comes from, when it is not the Jahia Store (whose advisories are the reference): the store host,
 * and the host of its link when that is another one. Empty for the Jahia Store, or a server that does not say.
 */
const sourceText = (t, advisory, url) => {
    const store = advisory.storeHost;
    if (!store || isJahiaStoreHost(store)) {
        return null;
    }

    const linkHost = url ? hostOf(url) : null;
    return linkHost && linkHost.toLowerCase() !== store.toLowerCase() ?
        t('page.advisoryFromLink', values({store, host: linkHost})) :
        t('page.advisoryFrom', values({store}));
};

// Feed texts (titles, owner notes) are untrusted and in English: React text only, marked lang="en".
const Advisory = ({advisory, isOwner}) => {
    const {t} = useTranslation(NS);
    const url = safeHttpsUrl(advisory.url);
    const title = advisory.title ? <span lang="en">{advisory.title}</span> : null;
    const cves = (advisory.cves || []).filter(cve => CVE.test(cve));
    let label;
    if (isOwner) {
        // An owner report without reference has a generated id (owner:<module>@<version>): it is not shown.
        const name = advisory.id.startsWith('owner:') ? t('page.ownerReport') : advisory.id;
        label = <>{name}{title && <>: {title}</>}</>;
    } else {
        const source = sourceText(t, advisory, url);
        label = title ? <>{advisory.id}: {title}</> : t('page.advisoryFallback', values({id: advisory.id}));
        label = source ? <>{label} <span data-testid="security-updates-advisory-source">{source}</span></> : label;
    }

    return (
        <li>
            {url && !isOwner ? <ExternalLink href={url}>{label}</ExternalLink> : <Typography component="span">{label}</Typography>}
            {url && isOwner && (
                <span className={styles.block}><ExternalLink href={url}>{t('page.ownerLink', values({host: hostOf(url)}))}</ExternalLink></span>
            )}
            {cves.map(cve => (
                <span key={cve} className={styles.block}>
                    <ExternalLink href={`https://www.cve.org/CVERecord?id=${cve}`}>{t('page.cveLink', values({id: cve}))}</ExternalLink>
                </span>
            ))}
        </li>
    );
};

Advisory.propTypes = {advisory: advisoryShape.isRequired, isOwner: PropTypes.bool.isRequired};

export const AdvisoryList = ({advisories, isOwner}) => (
    <ul className={styles.advisories}>
        {advisories.map((advisory, index) => (
            // Ids come from several stores and can repeat: the position keeps the keys unique.
            // eslint-disable-next-line react/no-array-index-key
            <Advisory key={`${index}:${advisory.id}`} advisory={advisory} isOwner={isOwner}/>
        ))}
    </ul>
);

AdvisoryList.propTypes = {advisories: PropTypes.arrayOf(advisoryShape).isRequired, isOwner: PropTypes.bool.isRequired};
