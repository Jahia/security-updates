// Pure helpers of the security updates page and banner. Feed text is untrusted: it is only ever rendered
// as React text, and links are only built from https URLs.

import {SEVERITIES} from '../common/constants';

const severityRank = severity => {
    const index = SEVERITIES.indexOf(severity);
    return index === -1 ? SEVERITIES.length : index;
};

export const sortFindings = findings => [...(findings || [])].sort((a, b) =>
    (severityRank(a.severity) - severityRank(b.severity)) ||
    String(a.component).localeCompare(String(b.component)) ||
    String(a.source).localeCompare(String(b.source)));

export const filterFindings = (findings, filter) => (findings || [])
    .filter(f => filter === 'all' || f.source === 'ADVISORY');

export const safeHttpsUrl = url => {
    if (typeof url !== 'string' || !url.startsWith('https://')) {
        return null;
    }

    try {
        const parsed = new URL(url);
        return parsed.protocol === 'https:' && parsed.hostname && !url.includes(' ') ? url : null;
    } catch {
        return null;
    }
};

export const formatDateTime = (iso, locale) => {
    if (!iso) {
        return null;
    }

    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) {
        return iso;
    }

    try {
        return new Intl.DateTimeFormat(locale || undefined, {dateStyle: 'medium', timeStyle: 'short'}).format(date);
    } catch {
        return date.toISOString();
    }
};

const contextPath = () => (window.contextJsParameters && window.contextJsParameters.contextPath) || '';

export const adminUrl = route => `${contextPath()}/jahia/administration/${route}`;

export const graphqlUrl = () => `${contextPath()}/modules/graphql`;
