import {
    actionFor,
    checkOutcome,
    chipColor,
    countBySource,
    defaultFilter,
    errorTexts,
    hostOf,
    severitySummary,
    statusMessage,
    viewStatus
} from '../reportView';

const finding = (component, severity, source = 'ADVISORY') => ({component, severity, source});
const report = (overrides = {}) => ({status: 'OK', stale: false, inProgress: false, errors: [], findings: [], ...overrides});
const notPublished = {storeUrl: 'https://store.jahia.com', kind: 'NOT_PUBLISHED', detail: null, message: 'HTTP 404'};

describe('viewStatus', () => {
    test.each([
        ['an OK report without findings is up to date', report(), 'UP_TO_DATE'],
        ['findings need updates, even when the check is partial', report({status: 'PARTIAL', findings: [finding('m', 'LOW')]}), 'UPDATES_NEEDED'],
        ['a partial report without findings is partly checked', report({status: 'PARTIAL'}), 'PARTIAL'],
        ['a stale report without findings is partly checked', report({status: 'OK', stale: true}), 'PARTIAL'],
        ['unavailable only because the store does not publish yet', report({status: 'UNAVAILABLE', errors: [notPublished]}), 'NOT_AVAILABLE_YET'],
        ['unavailable for another reason', report({status: 'UNAVAILABLE', errors: [notPublished, {kind: 'UNAVAILABLE', detail: 'TIMEOUT'}]}), 'NO_DATA'],
        ['unavailable without errors', report({status: 'UNAVAILABLE'}), 'NO_DATA'],
        ['disabled', report({status: 'DISABLED', findings: [finding('m', 'LOW')]}), 'DISABLED'],
        ['not checked', report({status: 'NOT_CHECKED'}), 'NOT_CHECKED']
    ])('%s', (name, input, expected) => {
        expect(viewStatus(input)).toBe(expected);
    });
});

describe('chipColor', () => {
    test('uses only the Moonstone chip colours whose text reaches 7:1 (no success, no warning)', () => {
        ['NOT_CHECKED',
            'DISABLED',
            'UP_TO_DATE',
            'UPDATES_NEEDED',
            'PARTIAL',
            'NO_DATA',
            'NOT_AVAILABLE_YET',
            'CRITICAL',
            'HIGH',
            'MEDIUM',
            'LOW',
            'UNKNOWN',
            'other']
            .forEach(value => expect(['default', 'accent', 'danger']).toContain(chipColor(value)));
        expect(chipColor('UPDATES_NEEDED')).toBe('danger');
        expect(chipColor('CRITICAL')).toBe('danger');
        expect(chipColor('NOT_AVAILABLE_YET')).toBe('default');
    });
});

describe('statusMessage', () => {
    test('nothing for an OK report with findings', () => {
        expect(statusMessage(report({findings: [finding('m', 'HIGH')]}))).toBeNull();
    });

    test('the explicit empty state for an OK report without findings', () => {
        expect(statusMessage(report())).toEqual({key: 'page.empty', tone: 'info'});
    });

    test('a partial check with findings says the list may be incomplete', () => {
        expect(statusMessage(report({status: 'PARTIAL', findings: [finding('m', 'HIGH')]}))).toEqual({key: 'page.status.PARTIAL', tone: 'warning'});
    });

    test('a partial check without findings does not speak of a list', () => {
        expect(statusMessage(report({status: 'PARTIAL'}))).toEqual({key: 'page.status.PARTIAL_EMPTY', tone: 'warning'});
    });

    test('a stale report says since when', () => {
        expect(statusMessage(report({status: 'PARTIAL', stale: true}))).toEqual({key: 'page.status.STALE', tone: 'warning', isStale: true});
    });

    test('NOT_PUBLISHED only is neutral information, nothing to configure', () => {
        expect(statusMessage(report({status: 'UNAVAILABLE', errors: [notPublished]}))).toEqual({key: 'page.status.NOT_PUBLISHED', tone: 'info'});
        expect(statusMessage(report({status: 'UNAVAILABLE', errors: [{...notPublished, storeUrl: 'https://STORE.jahia.com/'}]})))
            .toEqual({key: 'page.status.NOT_PUBLISHED', tone: 'info'});
    });

    test('NOT_PUBLISHED from another store (or without address) does not name the Jahia Store', () => {
        const other = {...notPublished, storeUrl: 'https://store.partner.example'};
        expect(statusMessage(report({status: 'UNAVAILABLE', errors: [notPublished, other]}))).toEqual({key: 'page.status.NOT_PUBLISHED_OTHER', tone: 'info'});
        expect(statusMessage(report({status: 'UNAVAILABLE', errors: [{...notPublished, storeUrl: null}]}))).toEqual({key: 'page.status.NOT_PUBLISHED_OTHER', tone: 'info'});
    });

    test.each(['UNAVAILABLE', 'NOT_CHECKED'])('%s is a warning', status => {
        expect(statusMessage(report({status, errors: [{kind: 'UNAVAILABLE'}]}))).toEqual({key: `page.status.${status}`, tone: 'warning'});
    });

    test('DISABLED is information', () => {
        expect(statusMessage(report({status: 'DISABLED'}))).toEqual({key: 'page.status.DISABLED', tone: 'info'});
    });
});

describe('checkOutcome', () => {
    test('findings give the number of distinct components, even from a partial check', () => {
        expect(checkOutcome(report({status: 'PARTIAL', findings: [finding('a', 'HIGH'), finding('a', 'LOW', 'OWNER_REPORTED'), finding('b', 'LOW')]})))
            .toEqual({count: 2});
    });

    test('no finding gives the status line of the report', () => {
        expect(checkOutcome(report())).toEqual({key: 'page.empty', tone: 'info'});
        expect(checkOutcome(report({status: 'UNAVAILABLE', errors: [notPublished]}))).toEqual(statusMessage(report({status: 'UNAVAILABLE', errors: [notPublished]})));
    });
});

describe('errorTexts', () => {
    test('the kind gives the title; the detail, when known, gives the explanation', () => {
        expect(errorTexts({kind: 'UNAVAILABLE', detail: 'SSRF_BLOCKED', message: 'blocked'}))
            .toEqual({title: 'page.errorKind.UNAVAILABLE', help: 'page.errorDetail.SSRF_BLOCKED', isTechnicalShown: true});
    });

    test('without a detail the kind gives the explanation', () => {
        expect(errorTexts({kind: 'NO_STORE', detail: null, message: 'No store'}))
            .toEqual({title: 'page.errorKind.NO_STORE', help: 'page.errorHelp.NO_STORE', isTechnicalShown: false});
    });

    test('an unknown detail falls back to the kind', () => {
        expect(errorTexts({kind: 'UNAVAILABLE', detail: 'SOMETHING_NEW', message: 'x'}).help).toBe('page.errorHelp.UNAVAILABLE');
    });

    test('the technical text is not repeated for a store that does not publish yet, nor when empty', () => {
        expect(errorTexts(notPublished).isTechnicalShown).toBe(false);
        expect(errorTexts({kind: 'UNAVAILABLE', detail: 'TIMEOUT', message: ''}).isTechnicalShown).toBe(false);
        expect(errorTexts({kind: 'UNAVAILABLE', detail: 'HTTP_STATUS', message: 'HTTP 503'}).isTechnicalShown).toBe(true);
    });

    test('a schema the module cannot read asks to update the module', () => {
        expect(errorTexts({kind: 'NOT_UNDERSTOOD', detail: 'SCHEMA_UNSUPPORTED'}).help).toBe('page.errorDetail.SCHEMA_UNSUPPORTED');
    });
});

describe('filters and counts', () => {
    const findings = [finding('a', 'HIGH'), finding('b', 'LOW', 'OWNER_REPORTED'), finding('a', 'CRITICAL', 'OWNER_REPORTED')];

    test('counts the findings shown by each filter', () => {
        expect(countBySource(findings)).toEqual({advisories: 1, all: 3});
        expect(countBySource(undefined)).toEqual({advisories: 0, all: 0});
    });

    test('defaults to all when module owners report something, so that the page agrees with the banner', () => {
        expect(defaultFilter(findings, '')).toBe('all');
        expect(defaultFilter([finding('a', 'HIGH')], '')).toBe('advisories');
        expect(defaultFilter([], '')).toBe('advisories');
    });

    test('?filter=all or ?filter=advisories in the address wins', () => {
        expect(defaultFilter([finding('a', 'HIGH')], '?filter=all')).toBe('all');
        expect(defaultFilter(findings, '?filter=advisories')).toBe('advisories');
        expect(defaultFilter(findings, '?filter=bogus')).toBe('all');
    });

    test('summarises distinct components by their highest severity', () => {
        expect(severitySummary(findings)).toEqual({total: 2, bySeverity: [{severity: 'CRITICAL', n: 1}, {severity: 'LOW', n: 1}]});
        expect(severitySummary([])).toEqual({total: 0, bySeverity: []});
    });
});

describe('per-row action and links', () => {
    test('Jahia itself is upgraded, a module is updated in Manage modules', () => {
        expect(actionFor({component: 'jahia-core'})).toBe('core');
        expect(actionFor({component: 'jcontent'})).toBe('module');
    });

    test('hostOf gives the host name of an https link, or null', () => {
        expect(hostOf('https://github.com/owner/repo/security')).toBe('github.com');
        expect(hostOf('not a url')).toBeNull();
        expect(hostOf(null)).toBeNull();
    });
});
