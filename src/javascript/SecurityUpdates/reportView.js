// Pure decisions of the Security updates page: what the report means for the user (status chip, status line, store
// problems), and the filter, counts and actions of the findings. Rendering is in the components.

import {SEVERITIES} from '../common/constants';

/** Whether the only problems of a check are stores that do not publish security data yet (HTTP 404). */
export const isNotPublishedOnly = errors => (errors || []).length > 0 && errors.every(error => error.kind === 'NOT_PUBLISHED');

const JAHIA_STORE_HOST = 'store.jahia.com';

/** Whether a host name is the Jahia Store's (an advisory's storeHost). */
export const isJahiaStoreHost = host => (host || '').toLowerCase() === JAHIA_STORE_HOST;

// Same rule as StoreUrls.isJahiaStore: the host of the address is the Jahia Store's.
const isJahiaStore = url => isJahiaStoreHost(hostOf(url));

/**
 * What the report means, as a statusLabel key: DISABLED, NOT_CHECKED, UPDATES_NEEDED (findings, even from a partial
 * check), UP_TO_DATE (complete, recent and empty), PARTIAL, NOT_AVAILABLE_YET (the only problem is a store that does
 * not publish security data yet) or NO_DATA.
 */
export const viewStatus = report => {
    const {status} = report;
    if (status === 'DISABLED' || status === 'NOT_CHECKED') {
        return status;
    }

    if ((report.findings || []).length > 0) {
        return 'UPDATES_NEEDED';
    }

    if (status === 'OK' && !report.stale) {
        return 'UP_TO_DATE';
    }

    if (status === 'UNAVAILABLE') {
        return isNotPublishedOnly(report.errors) ? 'NOT_AVAILABLE_YET' : 'NO_DATA';
    }

    return 'PARTIAL';
};

// Moonstone chip colours whose text reaches 7:1 against their background (danger, accent, default); the success
// and warning chips do not, so they are not used. The text of the chip always says what the colour means.
const DANGER = ['UPDATES_NEEDED', 'NO_DATA', 'CRITICAL', 'HIGH'];
const ACCENT = ['UP_TO_DATE', 'MEDIUM'];

export const chipColor = value => {
    if (DANGER.includes(value)) {
        return 'danger';
    }

    return ACCENT.includes(value) ? 'accent' : 'default';
};

/**
 * The status line under the header, or null when the table says it all.
 *
 * @returns {{key: string, tone: string, isStale?: boolean}|null}
 */
export const statusMessage = report => {
    const hasFindings = (report.findings || []).length > 0;
    switch (report.status) {
        case 'DISABLED':
            return {key: 'page.status.DISABLED', tone: 'info'};
        case 'NOT_CHECKED':
            return {key: 'page.status.NOT_CHECKED', tone: 'warning'};
        case 'UNAVAILABLE':
            if (!isNotPublishedOnly(report.errors)) {
                return {key: 'page.status.UNAVAILABLE', tone: 'warning'};
            }

            // The Jahia Store is named only when it is the store that has no data yet: another store may simply have
            // a wrong address.
            return report.errors.every(error => isJahiaStore(error.storeUrl)) ?
                {key: 'page.status.NOT_PUBLISHED', tone: 'info'} :
                {key: 'page.status.NOT_PUBLISHED_OTHER', tone: 'info'};
        default:
            break;
    }

    if (report.stale) {
        return {key: 'page.status.STALE', tone: 'warning', isStale: true};
    }

    if (report.status === 'OK') {
        return hasFindings ? null : {key: 'page.empty', tone: 'info'};
    }

    return {key: hasFindings ? 'page.status.PARTIAL' : 'page.status.PARTIAL_EMPTY', tone: 'warning'};
};

const DETAILS = ['NOT_HTTPS',
    'SSRF_BLOCKED',
    'TIMEOUT',
    'CONNECTION',
    'REDIRECT',
    'AUTH',
    'HTTP_STATUS',
    'TOO_LARGE',
    'INVALID_JSON',
    'SCHEMA_UNSUPPORTED',
    'ENTRIES_SKIPPED',
    'FORGE_SETTINGS_UNREADABLE',
    'CHECK_FAILED'];
const WITHOUT_TECHNICAL_TEXT = ['NOT_PUBLISHED', 'NO_STORE', 'STALE'];

/**
 * Localised texts of a store problem: the kind gives the title, the detail (or else the kind) the explanation. The
 * server's short English message is only shown as technical details, when it adds something.
 */
export const errorTexts = error => ({
    title: `page.errorKind.${error.kind}`,
    help: DETAILS.includes(error.detail) ? `page.errorDetail.${error.detail}` : `page.errorHelp.${error.kind}`,
    isTechnicalShown: Boolean(error.message) && !WITHOUT_TECHNICAL_TEXT.includes(error.kind)
});

/** Number of findings shown by each filter. */
export const countBySource = findings => ({
    advisories: (findings || []).filter(finding => finding.source === 'ADVISORY').length,
    all: (findings || []).length
});

const FILTERS = ['advisories', 'all'];

/**
 * The first filter: from the address (?filter=all or ?filter=advisories, for a link to a given view; the banner link
 * has no query), else "all" when module owners report something (the banner counts those reports by default), else
 * Jahia advisories only.
 */
export const defaultFilter = (findings, search) => {
    let requested = null;
    try {
        requested = new URLSearchParams(search || '').get('filter');
    } catch {
        requested = null;
    }

    if (FILTERS.includes(requested)) {
        return requested;
    }

    return (findings || []).some(finding => finding.source !== 'ADVISORY') ? 'all' : 'advisories';
};

/**
 * What a finished check found, for its announcement: the number of distinct components to update when there are
 * findings (even from a partial check), otherwise the status line of the report.
 *
 * @returns {{count: number}|{key: string, tone: string, isStale?: boolean}}
 */
export const checkOutcome = report => {
    const {total} = severitySummary(report.findings);
    return total > 0 ? {count: total} : statusMessage(report);
};

/** Distinct components, each counted once at its highest severity, most severe first. */
export const severitySummary = findings => {
    const highest = new Map();
    (findings || []).forEach(({component, severity}) => {
        const rank = SEVERITIES.indexOf(severity);
        const known = highest.get(component);
        if (known === undefined || (rank !== -1 && (known === -1 || rank < known))) {
            highest.set(component, rank);
        }
    });
    const bySeverity = SEVERITIES
        .map((severity, rank) => ({severity, n: [...highest.values()].filter(value => value === rank).length}))
        .filter(entry => entry.n > 0);
    return {total: highest.size, bySeverity};
};

export const JAHIA_CORE = 'jahia-core';

/** What to do for a finding: upgrade Jahia itself, or update a module in Manage modules. */
export const actionFor = finding => (finding.component === JAHIA_CORE ? 'core' : 'module');

/** Host name of a URL, or null. */
export const hostOf = url => {
    try {
        return new URL(url).hostname || null;
    } catch {
        return null;
    }
};
