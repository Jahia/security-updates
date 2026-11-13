import {act, fireEvent, render, screen, within} from '@testing-library/react';
import {renderToStaticMarkup} from 'react-dom/server.node';
import {axe} from 'jest-axe';
import {useMutation, useQuery} from '@apollo/client';
import {SecurityUpdates} from '../SecurityUpdates';
import {CHANGED_EVENT, JAHIA_UPGRADE_URL, PAGE_EVENT} from '../../common/constants';

jest.mock('../../Settings/SettingsForm', () => ({SettingsForm: () => <section aria-label="settings"/>}));

const advisory = (id, url, extra = {}) => ({id, title: `Title ${id}`, url, cves: ['CVE-2026-1234'], severity: 'HIGH', ...extra});

const finding = (component, severity, source = 'ADVISORY', extra = {}) => ({
    component,
    groupId: 'org.jahia.modules',
    installedVersion: '3.4.0',
    recommendedVersion: '3.10.0',
    compatibility: 'COMPATIBLE',
    requiredJahiaVersion: '8.2.0.0',
    severity,
    source,
    advisories: [advisory(`JSA-${component}-${source}`, 'https://academy.jahia.com/jsa')],
    ...extra
});

const report = (overrides = {}) => ({
    status: 'OK',
    inProgress: false,
    stale: false,
    checkedAt: '2026-11-13T10:00:00Z',
    lastSuccess: '2026-11-13T10:00:00Z',
    nextCheckAt: '2026-11-14T10:00:00Z',
    jahiaVersion: '8.2.3.7',
    errors: [],
    findings: [],
    ...overrides
});

const answer = (r, banner) => ({data: {securityUpdates: {report: r, banner}}});

describe('SecurityUpdates page', () => {
    let refresh;
    let refetch;

    const mockReport = (r, banner) => useQuery.mockReturnValue({...answer(r, banner), loading: false, error: undefined, refetch});

    beforeEach(() => {
        refetch = jest.fn(() => Promise.resolve(answer(report())));
        refresh = jest.fn(() => Promise.resolve({data: {securityUpdates: {refresh: report({inProgress: true})}}}));
        useMutation.mockReturnValue([refresh, {loading: false}]);
        window.contextJsParameters = {contextPath: ''};
        window.history.replaceState(null, '', '/');
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    const status = () => screen.getByTestId('security-updates-status');

    describe('loading and errors', () => {
        test('shows a loader while loading, and says so in the status region', () => {
            useQuery.mockReturnValue({data: undefined, loading: true, error: undefined, refetch});
            render(<SecurityUpdates/>);
            expect(screen.getByTestId('loader')).toBeInTheDocument();
            expect(status()).toHaveTextContent('page.loading');
            expect(status()).toHaveAttribute('role', 'status');
        });

        test('reports a query error', () => {
            useQuery.mockReturnValue({data: undefined, loading: false, error: new Error('denied'), refetch});
            render(<SecurityUpdates/>);
            expect(screen.getByRole('alert')).toHaveTextContent('page.error');
        });

        test('the report is read outside the Apollo cache (see apolloCache.test.js)', () => {
            mockReport(report());
            render(<SecurityUpdates/>);
            expect(useQuery).toHaveBeenCalledWith(expect.anything(), {fetchPolicy: 'no-cache'});
        });

        test('the status region exists before any announcement', () => {
            mockReport(report());
            render(<SecurityUpdates/>);
            expect(status()).toBeEmptyDOMElement();
        });

        test('the status region is empty in the first render, and filled once mounted (first content is not announced)', () => {
            useQuery.mockReturnValue({data: undefined, loading: true, error: undefined, refetch});
            const html = renderToStaticMarkup(<SecurityUpdates/>);
            expect(html).toMatch(/<div role="status" aria-live="polite" aria-atomic="true" class="announcement" data-testid="security-updates-status"><\/div>/);

            render(<SecurityUpdates/>);
            expect(status()).toHaveTextContent('page.loading');
        });
    });

    describe('status', () => {
        test('an OK report without findings is up to date and says so', () => {
            mockReport(report());
            render(<SecurityUpdates/>);
            expect(screen.getByText('page.empty')).toBeInTheDocument();
            expect(screen.getByText('page.statusLabel.UP_TO_DATE')).toBeInTheDocument();
            expect(screen.queryByRole('table')).toBeNull();
        });

        test('findings are "Updates needed", in a danger chip, not "all stores checked"', () => {
            mockReport(report({findings: [finding('m', 'CRITICAL')]}));
            render(<SecurityUpdates/>);
            const chip = screen.getByText('page.statusLabel.UPDATES_NEEDED').closest('[data-moonstone="Chip"]');
            expect(chip).toHaveAttribute('data-color', 'danger');
        });

        test('a store that does not publish yet is neutral information, without the technical text', () => {
            mockReport(report({status: 'UNAVAILABLE', errors: [{storeUrl: 'https://store.jahia.com', kind: 'NOT_PUBLISHED', detail: null, message: 'HTTP 404'}]}));
            render(<SecurityUpdates/>);
            const chip = screen.getByText('page.statusLabel.NOT_AVAILABLE_YET').closest('[data-moonstone="Chip"]');
            expect(chip).toHaveAttribute('data-color', 'default');
            expect(screen.getByText('page.status.NOT_PUBLISHED').closest('[data-tone]')).toHaveAttribute('data-tone', 'info');
            expect(screen.queryByText(/HTTP 404/)).toBeNull();
            expect(screen.queryByText('page.empty')).toBeNull();
        });

        test('stores that do not publish yet are listed as neutral store details, not as problems', () => {
            mockReport(report({status: 'UNAVAILABLE', errors: [{storeUrl: 'https://store.jahia.com', kind: 'NOT_PUBLISHED', detail: null, message: 'HTTP 404'}]}));
            render(<SecurityUpdates/>);
            expect(screen.queryByRole('heading', {name: 'page.errors.title'})).toBeNull();
            expect(screen.queryByText('page.errorHelp.NOT_PUBLISHED')).toBeNull();
            const details = screen.getByRole('list', {name: 'page.errors.infoTitle'});
            const item = within(details).getByText('page.errorKind.NOT_PUBLISHED').closest('[data-tone]');
            expect(item).toHaveAttribute('data-tone', 'info');
            expect(item).toHaveTextContent('page.errors.store {url=https://store.jahia.com}');
            expect(screen.getByRole('heading', {level: 2, name: 'page.errors.infoTitle'})).toBeInTheDocument();
        });

        test('a store without address that does not publish yet is listed without address', () => {
            mockReport(report({status: 'UNAVAILABLE', errors: [{storeUrl: null, kind: 'NOT_PUBLISHED', detail: null, message: null}]}));
            render(<SecurityUpdates/>);
            expect(screen.getByText('page.status.NOT_PUBLISHED_OTHER')).toBeInTheDocument();
            expect(screen.queryByText(/page\.errors\.store/)).toBeNull();
        });

        test('a store problem next to a store that does not publish yet keeps the problems heading', () => {
            mockReport(report({status: 'PARTIAL', errors: [
                {storeUrl: 'https://store.jahia.com', kind: 'NOT_PUBLISHED', detail: null, message: 'HTTP 404'},
                {storeUrl: 'https://a.example', kind: 'UNAVAILABLE', detail: 'TIMEOUT', message: 'x'}
            ]}));
            render(<SecurityUpdates/>);
            expect(screen.getByRole('heading', {name: 'page.errors.title'})).toBeInTheDocument();
        });

        test.each(['UNAVAILABLE', 'NOT_CHECKED'])('a %s report is not shown as up to date', s => {
            mockReport(report({status: s, errors: [{storeUrl: 'https://store.jahia.com', kind: 'UNAVAILABLE', detail: 'TIMEOUT', message: 'Read timed out'}]}));
            render(<SecurityUpdates/>);
            expect(screen.queryByText('page.empty')).toBeNull();
            expect(screen.getByText(`page.status.${s}`)).toBeInTheDocument();
        });

        test('a partial check without findings does not speak of a list', () => {
            mockReport(report({status: 'PARTIAL', errors: [{storeUrl: 'https://a.example', kind: 'UNAVAILABLE', detail: 'TIMEOUT', message: 'x'}]}));
            render(<SecurityUpdates/>);
            expect(screen.getByText('page.status.PARTIAL_EMPTY')).toBeInTheDocument();
        });

        test('a stale report says since when, and the next check is shown', () => {
            mockReport(report({status: 'PARTIAL', stale: true, lastSuccess: '2026-10-01T10:00:00Z'}));
            render(<SecurityUpdates/>);
            expect(screen.getByText(/^page\.status\.STALE \{date=.*2026/)).toBeInTheDocument();
            expect(screen.getByText('page.nextCheckAt')).toBeInTheDocument();
        });

        test('no next check planned is said in words', () => {
            mockReport(report({nextCheckAt: null}));
            render(<SecurityUpdates/>);
            expect(screen.getByText('page.notScheduled')).toBeInTheDocument();
        });

        test('DISABLED: no Check now button, and the message points to the settings', () => {
            mockReport(report({status: 'DISABLED', nextCheckAt: null}));
            render(<SecurityUpdates/>);
            expect(screen.queryByRole('button', {name: 'page.refresh'})).toBeNull();
            expect(screen.getByText('page.status.DISABLED')).toBeInTheDocument();
            expect(screen.getByText('page.statusLabel.DISABLED')).toBeInTheDocument();
        });
    });

    describe('store problems', () => {
        test('are localised from the kind and the detail, with the store and the technical text in English', () => {
            mockReport(report({status: 'UNAVAILABLE', errors: [{storeUrl: 'https://internal.example', kind: 'UNAVAILABLE', detail: 'SSRF_BLOCKED', message: 'Address blocked'}]}));
            render(<SecurityUpdates/>);
            const errors = screen.getByRole('list', {name: 'page.errors.title'});
            expect(within(errors).getByText('page.errorKind.UNAVAILABLE')).toBeInTheDocument();
            expect(within(errors).getByText('page.errorDetail.SSRF_BLOCKED')).toBeInTheDocument();
            expect(within(errors).getByText('page.errors.store {url=https://internal.example}')).toBeInTheDocument();
            expect(within(errors).getByText('page.errors.technical {message=Address blocked}')).toHaveAttribute('lang', 'en');
        });

        test('the same problem twice is listed twice without key clashes', () => {
            const error = {storeUrl: 'https://a.example', kind: 'UNAVAILABLE', detail: 'TIMEOUT', message: 'x'};
            const spy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
            mockReport(report({status: 'UNAVAILABLE', errors: [error, error]}));
            render(<SecurityUpdates/>);
            expect(within(screen.getByRole('list', {name: 'page.errors.title'})).getAllByRole('listitem')).toHaveLength(2);
            expect(spy).not.toHaveBeenCalled();
            spy.mockRestore();
        });
    });

    describe('findings', () => {
        test('are under a "Components to update" heading, with a summary by severity', () => {
            mockReport(report({findings: [finding('a', 'CRITICAL'), finding('b', 'HIGH'), finding('a', 'LOW', 'OWNER_REPORTED')]}));
            render(<SecurityUpdates/>);
            expect(screen.getByRole('heading', {level: 2, name: 'page.findingsTitle'})).toBeInTheDocument();
            expect(screen.getByText('page.summary.other {n=2}')).toBeInTheDocument();
            expect(screen.getByText(/^page\.summary\.bySeverity \{list=page\.summary\.severityCount/)).toBeInTheDocument();
        });

        test('are in a real table, severity next to the component, most severe first', () => {
            mockReport(report({findings: [finding('low-module', 'LOW'), finding('critical-module', 'CRITICAL'), finding('jahia-core', 'HIGH', 'ADVISORY', {compatibility: 'JAHIA_UPGRADE', groupId: null})]}));
            render(<SecurityUpdates/>);

            const table = screen.getByRole('table');
            expect(within(table).getByText('page.table.caption')).toBeInTheDocument();
            const headers = within(table).getAllByRole('columnheader').map(header => header.textContent);
            expect(headers).toEqual(['component', 'severity', 'installed', 'recommended', 'compatibility', 'source', 'advisories', 'action']
                .map(column => `page.table.${column}`));
            const rows = within(table).getAllByRole('row').slice(1);
            expect(within(rows[0]).getByRole('rowheader')).toHaveTextContent('critical-module');
            expect(within(rows[0]).getByText('page.severity.CRITICAL')).toBeInTheDocument();
            expect(within(rows[1]).getByRole('rowheader')).toHaveTextContent('jahia-core');
            expect(within(rows[1]).getByText('page.compatibility.JAHIA_UPGRADE')).toBeInTheDocument();
            expect(within(rows[2]).getByRole('rowheader')).toHaveTextContent('low-module');
        });

        test('the table scrolls in a named, keyboard-focusable region', () => {
            mockReport(report({findings: [finding('m', 'HIGH')]}));
            render(<SecurityUpdates/>);
            const region = screen.getByRole('region', {name: 'page.findingsTitle'});
            expect(region).toHaveAttribute('tabindex', '0');
            expect(within(region).getByRole('table')).toBeInTheDocument();
        });

        test('rows carry the module class that lets them grow, and explicit table roles (Moonstone lays the table out with flex boxes)', () => {
            mockReport(report({findings: [finding('m', 'HIGH'), finding('n', 'LOW')]}));
            const {container} = render(<SecurityUpdates/>);

            const table = container.querySelector('table');
            expect(table).toHaveAttribute('role', 'table');
            expect(table).toHaveClass('table');
            container.querySelectorAll('thead, tbody').forEach(group => expect(group).toHaveAttribute('role', 'rowgroup'));
            const rows = container.querySelectorAll('tr');
            expect(rows).toHaveLength(3);
            rows.forEach(row => {
                expect(row).toHaveAttribute('role', 'row');
                expect(row).toHaveClass('row');
            });
            container.querySelectorAll('thead th').forEach(cell => expect(cell).toHaveAttribute('role', 'columnheader'));
            container.querySelectorAll('tbody th').forEach(cell => expect(cell).toHaveAttribute('role', 'rowheader'));
            expect(container.querySelectorAll('tbody td')).toHaveLength(14);
            container.querySelectorAll('tbody td').forEach(cell => expect(cell).toHaveAttribute('role', 'cell'));
        });

        test('each row says what to do: a module is updated in Manage modules, Jahia follows the upgrade documentation', () => {
            mockReport(report({findings: [finding('jcontent', 'HIGH'), finding('jahia-core', 'CRITICAL', 'ADVISORY', {groupId: null})]}));
            render(<SecurityUpdates/>);
            expect(screen.getByRole('link', {name: 'page.action.module {component=jcontent}'}))
                .toHaveAttribute('href', '/jahia/administration/manageModules');
            const docs = screen.getByRole('link', {name: /page\.action\.core/});
            expect(docs).toHaveAttribute('href', JAHIA_UPGRADE_URL);
            expect(docs).toHaveAttribute('target', '_blank');
            expect(screen.getByText('page.afterTable')).toBeInTheDocument();
        });

        test('advisory links open in a new tab without opener; non-https links are plain text', () => {
            mockReport(report({findings: [finding('m', 'HIGH', 'ADVISORY', {advisories: [
                advisory('JSA-SAFE', 'https://academy.jahia.com/jsa-safe'),
                advisory('JSA-UNSAFE', ['java', 'script:alert(1)'].join(''))
            ]})]}));
            render(<SecurityUpdates/>);

            const link = screen.getByRole('link', {name: /JSA-SAFE/});
            expect(link).toHaveAttribute('href', 'https://academy.jahia.com/jsa-safe');
            expect(link).toHaveAttribute('target', '_blank');
            expect(link.getAttribute('rel')).toContain('noopener');
            expect(screen.queryByRole('link', {name: /JSA-UNSAFE/})).toBeNull();
            expect(screen.getByText(/^JSA-UNSAFE/)).toBeInTheDocument();
        });

        test('an advisory without title is never named by its id alone', () => {
            mockReport(report({findings: [finding('m', 'HIGH', 'ADVISORY', {advisories: [advisory('JSA-1', 'https://academy.jahia.com/1', {title: null})]})]}));
            render(<SecurityUpdates/>);
            expect(screen.getByRole('link', {name: /page\.advisoryFallback \{id=JSA-1\}/})).toBeInTheDocument();
        });

        test('CVEs link to cve.org', () => {
            mockReport(report({findings: [finding('m', 'HIGH')]}));
            render(<SecurityUpdates/>);
            expect(screen.getByRole('link', {name: /page\.cveLink \{id=CVE-2026-1234\}/}))
                .toHaveAttribute('href', 'https://www.cve.org/CVERecord?id=CVE-2026-1234');
        });

        test('an owner report reads as text, and its link names the host', () => {
            mockReport(report({findings: [finding('owned', 'HIGH', 'OWNER_REPORTED', {advisories: [
                advisory('owner:owned@1.0.0', null, {title: 'Upgrade please', cves: []}),
                advisory('GHSA-1', 'https://github.com/owner/repo/security', {title: null, cves: []})
            ]})]}));
            render(<SecurityUpdates/>);
            expect(screen.queryByText(/owner:owned/)).toBeNull();
            expect(screen.getByText(/page\.ownerReport/)).toBeInTheDocument();
            expect(screen.getByText('Upgrade please')).toHaveAttribute('lang', 'en');
            expect(screen.getByRole('link', {name: /page\.ownerLink \{host=github\.com\}/})).toHaveAttribute('href', 'https://github.com/owner/repo/security');
        });
    });

    describe('what the banner counts', () => {
        const findings = [finding('a', 'CRITICAL'), finding('b', 'LOW'), finding('c', 'MEDIUM', 'OWNER_REPORTED')];

        test('when the banner counts fewer components than the page lists, the page says what it counts', () => {
            mockReport(report({findings}), {visible: true, count: 1, minimumSeverity: 'HIGH'});
            render(<SecurityUpdates/>);
            expect(screen.getByTestId('security-updates-banner-count'))
                .toHaveTextContent('page.bannerCount.one {n=1, threshold=banner.threshold.HIGH}');
        });

        test('an unknown threshold is the server default, HIGH, as in the banner', () => {
            mockReport(report({findings}), {visible: true, count: 2, minimumSeverity: 'BOGUS'});
            render(<SecurityUpdates/>);
            expect(screen.getByTestId('security-updates-banner-count'))
                .toHaveTextContent('page.bannerCount.other {n=2, threshold=banner.threshold.HIGH}');
        });

        test.each([
            ['the same count', {visible: true, count: 3, minimumSeverity: 'LOW'}],
            ['no banner shown', {visible: false, count: 0, minimumSeverity: 'HIGH'}],
            ['no banner answer', undefined]
        ])('says nothing with %s', (name, banner) => {
            mockReport(report({findings}), banner);
            render(<SecurityUpdates/>);
            expect(screen.queryByTestId('security-updates-banner-count')).toBeNull();
        });
    });

    describe('filter', () => {
        test('defaults to all when module owners report something, with counts and a hint', () => {
            mockReport(report({findings: [finding('advised', 'HIGH'), finding('owner-module', 'CRITICAL', 'OWNER_REPORTED')]}));
            render(<SecurityUpdates/>);

            expect(screen.getByText('owner-module')).toBeInTheDocument();
            expect(screen.getByLabelText('page.filter.all {n=2}')).toBeChecked();
            expect(screen.getByLabelText('page.filter.advisories {n=1}')).not.toBeChecked();
            expect(screen.getByRole('group', {name: 'page.filter.legend'})).toHaveAccessibleDescription('page.filter.hint');

            const filterStatus = screen.getByTestId('security-updates-filter-status');
            expect(filterStatus).toHaveAttribute('role', 'status');
            expect(filterStatus).toBeEmptyDOMElement();

            fireEvent.click(screen.getByLabelText('page.filter.advisories {n=1}'));
            expect(screen.queryByText('owner-module')).toBeNull();
            expect(filterStatus).toHaveTextContent('page.filter.showing.one {n=1}');

            fireEvent.click(screen.getByLabelText('page.filter.all {n=2}'));
            expect(filterStatus).toHaveTextContent('page.filter.showing.other {n=2}');
        });

        test('shows Jahia advisories only when there is no owner report', () => {
            mockReport(report({findings: [finding('advised', 'HIGH')]}));
            render(<SecurityUpdates/>);
            expect(screen.getByLabelText('page.filter.advisories {n=1}')).toBeChecked();
        });

        test('?filter=advisories in the address is followed, and says when it hides every finding', () => {
            window.history.replaceState(null, '', '/?filter=advisories');
            mockReport(report({findings: [finding('owner-module', 'CRITICAL', 'OWNER_REPORTED')]}));
            render(<SecurityUpdates/>);
            expect(screen.getByText('page.filteredEmpty')).toBeInTheDocument();
        });
    });

    test('links to Manage modules', () => {
        mockReport(report({findings: [finding('m', 'HIGH')]}));
        render(<SecurityUpdates/>);
        expect(screen.getByRole('link', {name: 'page.manageModules'})).toHaveAttribute('href', '/jahia/administration/manageModules');
    });

    describe('Check now', () => {
        const button = () => screen.getByRole('button', {name: /page\.(refresh|checking)/});

        test('starts a check, shows it is busy, polls the report until the check ends, then announces it', async () => {
            jest.useFakeTimers();
            const changed = jest.fn();
            window.addEventListener(CHANGED_EVENT, changed);
            refetch
                .mockImplementationOnce(() => Promise.resolve(answer(report({inProgress: true}))))
                .mockImplementationOnce(() => Promise.resolve(answer(report({checkedAt: '2026-11-13T11:00:00Z'}))));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });

            expect(refresh).toHaveBeenCalledTimes(1);
            expect(button()).toHaveTextContent('page.checking');
            expect(button()).toHaveAttribute('aria-busy', 'true');
            expect(button()).not.toBeDisabled();
            expect(status()).toHaveTextContent('page.refreshing');
            // One visible indicator: the running check is shown in the status region only.
            expect(within(status()).getByTestId('security-updates-checking')).toBeInTheDocument();
            expect(screen.getAllByText('page.refreshing')).toHaveLength(1);

            await act(() => jest.advanceTimersByTimeAsync(2000));
            expect(refetch).toHaveBeenCalledTimes(1);
            expect(status()).toHaveTextContent('page.refreshing');

            await act(() => jest.advanceTimersByTimeAsync(2000));
            expect(refetch).toHaveBeenCalledTimes(2);
            expect(status()).toHaveTextContent('page.refreshed {result=page.empty}');
            expect(screen.queryByTestId('security-updates-checking')).toBeNull();
            expect(button()).toHaveTextContent('page.refresh');
            expect(button()).not.toHaveAttribute('aria-busy');
            expect(changed).toHaveBeenCalledTimes(1);
            window.removeEventListener(CHANGED_EVENT, changed);
        });

        test('a second click during a check does not start another, and says once that a check is already running', async () => {
            refresh.mockImplementation(() => new Promise(() => {}));
            mockReport(report());
            render(<SecurityUpdates/>);

            fireEvent.click(button());
            expect(status()).not.toHaveTextContent('page.alreadyRunning');
            fireEvent.click(button());
            fireEvent.click(button());

            expect(refresh).toHaveBeenCalledTimes(1);
            expect(status()).toHaveTextContent('page.refreshing page.alreadyRunning');
            expect(screen.getAllByText(/page\.alreadyRunning/)).toHaveLength(1);
        });

        test('the outcome says how many components need an update', async () => {
            jest.useFakeTimers();
            refetch.mockImplementation(() => Promise.resolve(answer(report({findings: [finding('a', 'HIGH'), finding('b', 'LOW'), finding('a', 'LOW', 'OWNER_REPORTED')]}))));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });
            await act(() => jest.advanceTimersByTimeAsync(2000));

            expect(status()).toHaveTextContent('page.refreshed {result=page.summary.other {n=2}}');
        });

        test('the outcome of a check without findings that is not complete says so (stale here, with its date)', async () => {
            jest.useFakeTimers();
            refetch.mockImplementation(() => Promise.resolve(answer(report({status: 'PARTIAL', stale: true, lastSuccess: null}))));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });
            await act(() => jest.advanceTimersByTimeAsync(2000));

            expect(status()).toHaveTextContent('page.refreshed {result=page.status.STALE {date=page.never}}');
        });

        test('when no check was started (one ran a moment ago), says so', async () => {
            refresh.mockImplementation(() => Promise.resolve({data: {securityUpdates: {refresh: report()}}}));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });

            expect(status()).toHaveTextContent('page.notRefreshed');
            expect(button()).toHaveTextContent('page.refresh');
        });

        test('a check that already ended when the answer comes is announced as completed, and the report is read again', async () => {
            refresh.mockImplementation(() => Promise.resolve({data: {securityUpdates: {refresh: report({checkedAt: '2026-11-13T12:00:00Z'})}}}));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });

            expect(status()).toHaveTextContent('page.refreshed {result=page.empty}');
            expect(refetch).toHaveBeenCalledTimes(1);
        });

        test('a check that already ended is announced only once the page has the new report', async () => {
            let answerRefetch;
            refetch.mockImplementation(() => new Promise(resolve => {
                answerRefetch = resolve;
            }));
            refresh.mockImplementation(() => Promise.resolve({data: {securityUpdates: {refresh: report({checkedAt: '2026-11-13T12:00:00Z'})}}}));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });
            expect(status()).not.toHaveTextContent('page.refreshed');
            expect(button()).toHaveTextContent('page.checking');

            await act(async () => {
                answerRefetch(answer(report({checkedAt: '2026-11-13T12:00:00Z', findings: [finding('m', 'HIGH')]})));
            });
            expect(status()).toHaveTextContent('page.refreshed {result=page.summary.one {n=1}}');
        });

        test('a check that already ended, whose new report cannot be read, is reported as a failure', async () => {
            const changed = jest.fn();
            window.addEventListener(CHANGED_EVENT, changed);
            refetch.mockImplementation(() => Promise.reject(new Error('offline')));
            refresh.mockImplementation(() => Promise.resolve({data: {securityUpdates: {refresh: report({checkedAt: '2026-11-13T12:00:00Z'})}}}));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });

            expect(status()).toHaveTextContent('page.refreshError');
            expect(within(status()).getByText('page.refreshError').closest('[data-tone]')).toHaveAttribute('data-tone', 'warning');
            expect(changed).toHaveBeenCalledTimes(1);
            window.removeEventListener(CHANGED_EVENT, changed);
        });

        test('three failed polls in a row stop the check and say it failed, instead of running until "slow"', async () => {
            jest.useFakeTimers();
            refetch.mockImplementation(() => Promise.reject(new Error('server down')));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });
            await act(() => jest.advanceTimersByTimeAsync(4000));
            expect(button()).toHaveTextContent('page.checking');

            await act(() => jest.advanceTimersByTimeAsync(2000));
            expect(refetch).toHaveBeenCalledTimes(3);
            expect(status()).toHaveTextContent('page.refreshError');
            expect(button()).toHaveTextContent('page.refresh');

            await act(() => jest.advanceTimersByTimeAsync(10000));
            expect(refetch).toHaveBeenCalledTimes(3);
        });

        test('failures that are not in a row, or an answer without a report, do not stop the check', async () => {
            jest.useFakeTimers();
            const running = () => Promise.resolve(answer(report({inProgress: true})));
            const failing = () => Promise.reject(new Error('blip'));
            refetch
                .mockImplementationOnce(failing)
                .mockImplementationOnce(() => Promise.resolve({data: null}))
                .mockImplementationOnce(running)
                .mockImplementationOnce(failing)
                .mockImplementationOnce(failing)
                .mockImplementationOnce(() => Promise.resolve(answer(report())));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });
            await act(() => jest.advanceTimersByTimeAsync(12000));

            expect(refetch).toHaveBeenCalledTimes(6);
            expect(status()).toHaveTextContent('page.refreshed {result=page.empty}');
        });

        test('failed polls at the end of the polling time give the "slow" message', async () => {
            jest.useFakeTimers();
            refetch.mockImplementation(() => (refetch.mock.calls.length % 2 === 0 ?
                Promise.reject(new Error('blip')) :
                Promise.resolve(answer(report({inProgress: true})))));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });
            await act(() => jest.advanceTimersByTimeAsync(4 * 60 * 1000));

            expect(refetch).toHaveBeenCalledTimes(90);
            expect(status()).toHaveTextContent('page.refreshSlow');
        });

        test('a poll that fails is retried', async () => {
            jest.useFakeTimers();
            refetch
                .mockImplementationOnce(() => Promise.reject(new Error('offline')))
                .mockImplementationOnce(() => Promise.resolve(answer(report())));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });
            await act(() => jest.advanceTimersByTimeAsync(4000));

            expect(refetch).toHaveBeenCalledTimes(2);
            expect(status()).toHaveTextContent('page.refreshed {result=page.empty}');
        });

        test('closing the page while a check starts or fails changes nothing', async () => {
            let answerRefresh;
            let failRefresh;
            refresh
                .mockImplementationOnce(() => new Promise(resolve => {
                    answerRefresh = resolve;
                }))
                .mockImplementationOnce(() => new Promise((resolve, reject) => {
                    failRefresh = reject;
                }));
            mockReport(report());
            const first = render(<SecurityUpdates/>);
            fireEvent.click(button());
            first.unmount();
            const second = render(<SecurityUpdates/>);
            fireEvent.click(button());
            second.unmount();

            await act(async () => {
                answerRefresh({data: {securityUpdates: {refresh: report({inProgress: true})}}});
                failRefresh(new Error('late'));
            });

            expect(refetch).not.toHaveBeenCalled();
        });

        test('a failed start is announced and shown', async () => {
            refresh.mockImplementation(() => Promise.reject(new Error('boom')));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });

            expect(status()).toHaveTextContent('page.refreshError');
            // Shown once, as a warning, in the status region.
            expect(screen.getAllByText('page.refreshError')).toHaveLength(1);
            expect(within(status()).getByText('page.refreshError').closest('[data-tone]')).toHaveAttribute('data-tone', 'warning');
        });

        test('stops polling after about three minutes and says how to see the results', async () => {
            jest.useFakeTimers();
            refetch.mockImplementation(() => Promise.resolve(answer(report({inProgress: true}))));
            mockReport(report());
            render(<SecurityUpdates/>);

            await act(async () => {
                fireEvent.click(button());
            });
            await act(() => jest.advanceTimersByTimeAsync(4 * 60 * 1000));

            expect(refetch.mock.calls.length).toBeLessThanOrEqual(91);
            expect(status()).toHaveTextContent('page.refreshSlow');
            expect(button()).toHaveTextContent('page.refresh');
        });

        test('a check already running when the page opens is followed too', async () => {
            jest.useFakeTimers();
            mockReport(report({inProgress: true}));
            render(<SecurityUpdates/>);

            expect(screen.getByText('page.running')).toBeInTheDocument();
            await act(() => jest.advanceTimersByTimeAsync(2000));
            expect(refetch).toHaveBeenCalled();
        });

        test('polling stops when the page closes', async () => {
            jest.useFakeTimers();
            refetch.mockImplementation(() => Promise.resolve(answer(report({inProgress: true}))));
            mockReport(report({inProgress: true}));
            const {unmount} = render(<SecurityUpdates/>);
            unmount();
            await act(() => jest.advanceTimersByTimeAsync(10000));
            expect(refetch).not.toHaveBeenCalled();
        });
    });

    test('sets a localised document title and restores it', () => {
        document.title = 'before';
        mockReport(report());
        const {unmount} = render(<SecurityUpdates/>);
        expect(document.title).toBe('page.documentTitle {title=label}');
        unmount();
        expect(document.title).toBe('before');
    });

    test('tells the banner when the page opens and closes', () => {
        const listener = jest.fn();
        window.addEventListener(PAGE_EVENT, listener);
        mockReport(report());
        const {unmount} = render(<SecurityUpdates/>);
        expect(listener.mock.calls[0][0].detail).toEqual({isOpen: true});
        unmount();
        expect(listener.mock.calls[1][0].detail).toEqual({isOpen: false});
        window.removeEventListener(PAGE_EVENT, listener);
    });

    test('is built from Moonstone components: LayoutContent, Header (h1 title, Check now), Table, Chip, RadioGroup', () => {
        mockReport(report({findings: [finding('m', 'CRITICAL'), finding('o', 'HIGH', 'OWNER_REPORTED')]}));
        const {container} = render(<SecurityUpdates/>);
        const used = name => container.querySelectorAll(`[data-moonstone="${name}"]`);

        expect(used('LayoutContent')).toHaveLength(1);
        const header = used('Header')[0];
        expect(within(header).getByRole('heading', {level: 1})).toHaveTextContent('label');
        expect(within(header).getByRole('button', {name: 'page.refresh'})).toHaveAttribute('data-moonstone', 'Button');
        expect(within(header).getByText('page.statusLabel.UPDATES_NEEDED')).toBeInTheDocument();
        expect(used('Table')).toHaveLength(1);
        expect(used('TableHeadCell')).toHaveLength(8);
        expect(used('RadioGroup')).toHaveLength(1);
        expect(used('RadioItem')).toHaveLength(2);
    });

    test('severity and source are chips with a text label, in colours that reach 7:1, and rows are not tab stops', () => {
        mockReport(report({findings: [finding('m', 'CRITICAL'), finding('n', 'MEDIUM', 'OWNER_REPORTED')]}));
        const {container} = render(<SecurityUpdates/>);
        const chips = [...container.querySelectorAll('[data-moonstone="Chip"]')];

        expect(chips.map(chip => chip.textContent)).toEqual(expect.arrayContaining(['page.severity.CRITICAL', 'page.source.ADVISORY']));
        chips.forEach(chip => expect(['default', 'accent', 'danger']).toContain(chip.getAttribute('data-color')));
        container.querySelectorAll('tbody tr').forEach(row => expect(row).toHaveAttribute('tabindex', '-1'));
    });

    test('status messages carry a decorative Moonstone icon next to their text', () => {
        mockReport(report({status: 'UNAVAILABLE'}));
        render(<SecurityUpdates/>);

        const message = screen.getByText('page.status.UNAVAILABLE').closest('[data-tone]');
        expect(within(message).getByTestId('icon-warning')).toHaveAttribute('aria-hidden', 'true');
    });

    test('has no axe violation, with findings and store problems', async () => {
        mockReport(report({
            status: 'PARTIAL',
            errors: [{storeUrl: 'https://a.example', kind: 'UNAVAILABLE', detail: 'TIMEOUT', message: 'x'}],
            findings: [finding('m', 'CRITICAL'), finding('jahia-core', 'HIGH'), finding('o', 'LOW', 'OWNER_REPORTED')]
        }));
        const {container} = render(<SecurityUpdates/>);
        expect(await axe(container)).toHaveNoViolations();
    });
});
