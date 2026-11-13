import {
    adminUrl,
    countUrgentAdvisories,
    filterFindings,
    formatDateTime,
    safeHttpsUrl,
    sortFindings
} from '../securityUpdatesUtils';

// Built at runtime so that the linter's no-script-url rule does not flag the test data.
const SCRIPT_URL = ['java', 'script:alert(1)'].join('');

const finding = (component, severity, source = 'ADVISORY') => ({component, severity, source, advisories: []});

describe('sortFindings', () => {
    test('sorts critical first, then high, medium, low, unknown, then by component', () => {
        const sorted = sortFindings([
            finding('b', 'LOW'),
            finding('z', 'CRITICAL'),
            finding('a', 'UNKNOWN'),
            finding('c', 'HIGH'),
            finding('a', 'HIGH'),
            finding('m', 'MEDIUM')
        ]);
        expect(sorted.map(f => `${f.severity}:${f.component}`)).toEqual([
            'CRITICAL:z', 'HIGH:a', 'HIGH:c', 'MEDIUM:m', 'LOW:b', 'UNKNOWN:a'
        ]);
    });

    test('does not mutate its input and tolerates a missing list', () => {
        const input = [finding('b', 'LOW'), finding('a', 'CRITICAL')];
        sortFindings(input);
        expect(input[0].component).toBe('b');
        expect(sortFindings(undefined)).toEqual([]);
    });
});

describe('filterFindings', () => {
    const findings = [finding('a', 'HIGH'), finding('b', 'LOW', 'OWNER_REPORTED')];

    test('keeps only Jahia advisories by default', () => {
        expect(filterFindings(findings, 'advisories').map(f => f.component)).toEqual(['a']);
    });

    test('keeps everything for "all"', () => {
        expect(filterFindings(findings, 'all')).toHaveLength(2);
    });
});

describe('safeHttpsUrl', () => {
    test.each([
        ['https://academy.jahia.com/jsa-1', 'https://academy.jahia.com/jsa-1'],
        ['http://academy.jahia.com/jsa-1', null],
        [SCRIPT_URL, null],
        ['HTTPS://academy.jahia.com', null],
        ['https://', null],
        ['https://exa mple.com', null],
        [null, null],
        [undefined, null],
        [42, null]
    ])('%s -> %s', (url, expected) => {
        expect(safeHttpsUrl(url)).toBe(expected);
    });
});

describe('countUrgentAdvisories', () => {
    test('counts high and critical findings from Jahia advisories only', () => {
        expect(countUrgentAdvisories([
            finding('a', 'CRITICAL'),
            finding('b', 'HIGH'),
            finding('c', 'MEDIUM'),
            finding('d', 'CRITICAL', 'OWNER_REPORTED')
        ])).toBe(2);
        expect(countUrgentAdvisories(null)).toBe(0);
    });
});

describe('formatDateTime', () => {
    test('formats an ISO date for the locale', () => {
        expect(formatDateTime('2026-11-13T10:00:00Z', 'en')).toMatch(/2026/);
    });

    test('returns null for a missing date and the raw text for an invalid one', () => {
        expect(formatDateTime(null, 'en')).toBeNull();
        expect(formatDateTime('not a date', 'en')).toBe('not a date');
    });
});

describe('adminUrl', () => {
    test('prefixes the context path', () => {
        window.contextJsParameters = {contextPath: '/ctx'};
        expect(adminUrl('manageModules')).toBe('/ctx/jahia/administration/manageModules');
        window.contextJsParameters = {contextPath: ''};
        expect(adminUrl('securityUpdates')).toBe('/jahia/administration/securityUpdates');
    });
});
