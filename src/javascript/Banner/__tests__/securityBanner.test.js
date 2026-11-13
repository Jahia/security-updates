import {BANNER_ID, DISMISS_KEY, showSecurityBanner} from '../securityBanner';

const t = (key, options) => (options && options.count !== undefined ? `${key}:${options.count}` : key);

const response = body => Promise.resolve({ok: true, json: () => Promise.resolve(body)});

const permission = allowed => ({data: {jcr: {nodeByPath: {hasPermission: allowed}}}});

const report = findings => ({data: {securityUpdates: {report: {findings}}}});

const urgent = [
    {component: 'a', severity: 'CRITICAL', source: 'ADVISORY'},
    {component: 'b', severity: 'HIGH', source: 'ADVISORY'},
    {component: 'c', severity: 'CRITICAL', source: 'OWNER_REPORTED'},
    {component: 'd', severity: 'LOW', source: 'ADVISORY'}
];

describe('showSecurityBanner', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        window.sessionStorage.clear();
        window.contextJsParameters = {contextPath: '/ctx'};
    });

    test('shows a dismissible notice with the count of high and critical Jahia advisories', async () => {
        const fetchImpl = jest.fn()
            .mockReturnValueOnce(response(permission(true)))
            .mockReturnValueOnce(response(report(urgent)));

        const shown = await showSecurityBanner({t, fetchImpl});

        expect(shown).toBe(true);
        const banner = document.getElementById(BANNER_ID);
        expect(banner).not.toBeNull();
        expect(banner.getAttribute('role')).toBe('region');
        expect(banner.getAttribute('aria-label')).toBe('banner.label');
        expect(banner.textContent).toContain('banner.message:2');
        const link = banner.querySelector('a');
        expect(link.getAttribute('href')).toBe('/ctx/jahia/administration/securityUpdates');
        expect(fetchImpl.mock.calls[0][0]).toBe('/ctx/modules/graphql');
    });

    test('dismissing removes the notice and remembers it for the session and this count', async () => {
        const fetchImpl = jest.fn()
            .mockReturnValueOnce(response(permission(true)))
            .mockReturnValueOnce(response(report(urgent)));
        await showSecurityBanner({t, fetchImpl});

        document.getElementById(BANNER_ID).querySelector('button').click();

        expect(document.getElementById(BANNER_ID)).toBeNull();
        expect(window.sessionStorage.getItem(DISMISS_KEY)).toBe('a|b');

        const again = jest.fn()
            .mockReturnValueOnce(response(permission(true)))
            .mockReturnValueOnce(response(report(urgent)));
        expect(await showSecurityBanner({t, fetchImpl: again})).toBe(false);
        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test('Escape inside the notice dismisses it', async () => {
        const fetchImpl = jest.fn()
            .mockReturnValueOnce(response(permission(true)))
            .mockReturnValueOnce(response(report(urgent)));
        await showSecurityBanner({t, fetchImpl});

        document.getElementById(BANNER_ID).dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));

        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test('does not query the report for a user without adminTemplates', async () => {
        const fetchImpl = jest.fn().mockReturnValueOnce(response(permission(false)));

        expect(await showSecurityBanner({t, fetchImpl})).toBe(false);
        expect(fetchImpl).toHaveBeenCalledTimes(1);
        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test('shows nothing when no high or critical Jahia advisory applies', async () => {
        const fetchImpl = jest.fn()
            .mockReturnValueOnce(response(permission(true)))
            .mockReturnValueOnce(response(report([{severity: 'CRITICAL', source: 'OWNER_REPORTED'}])));

        expect(await showSecurityBanner({t, fetchImpl})).toBe(false);
        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test.each([
        ['a network failure', () => Promise.reject(new Error('offline'))],
        ['an HTTP error', () => Promise.resolve({ok: false, json: () => Promise.resolve({})})],
        ['a GraphQL error', () => response({errors: [{message: 'Permission denied'}]})]
    ])('stays silent on %s', async (name, failing) => {
        const fetchImpl = jest.fn(failing);

        expect(await showSecurityBanner({t, fetchImpl})).toBe(false);
        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test('is shown only once', async () => {
        const fetchImpl = () => Promise.resolve({ok: true, json: () => Promise.resolve({})});
        const twice = jest.fn()
            .mockReturnValueOnce(response(permission(true)))
            .mockReturnValueOnce(response(report(urgent)))
            .mockImplementation(fetchImpl);
        await showSecurityBanner({t, fetchImpl: twice});
        await showSecurityBanner({t, fetchImpl: twice});

        expect(document.querySelectorAll(`#${BANNER_ID}`)).toHaveLength(1);
    });

    test('a dismissed notice comes back when other advisories apply, even with the same count', async () => {
        window.sessionStorage.setItem(DISMISS_KEY, 'a|b');
        const fetchImpl = jest.fn()
            .mockReturnValueOnce(response(permission(true)))
            .mockReturnValueOnce(response(report([
                {component: 'a', severity: 'CRITICAL', source: 'ADVISORY'},
                {component: 'x', severity: 'HIGH', source: 'ADVISORY'}
            ])));

        expect(await showSecurityBanner({t, fetchImpl})).toBe(true);
    });
});
