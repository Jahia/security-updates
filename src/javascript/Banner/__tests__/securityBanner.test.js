import {BANNER_ID, DISMISS_KEY, showSecurityBanner} from '../securityBanner';
import {localKey} from '../bannerDismissal';

// Echoes the key and the interpolated values, so that assertions can check what was asked for.
const t = (key, options) => (options && options.count !== undefined ? `${key}:${options.count}:${options.threshold}` : key);

const response = body => Promise.resolve({ok: true, json: () => Promise.resolve(body)});

const permission = allowed => ({data: {jcr: {nodeByPath: {hasPermission: allowed}}}});

const banner = (overrides = {}) => ({
    data: {
        securityUpdates: {
            banner: {
                visible: true,
                count: 3,
                minimumSeverity: 'HIGH',
                components: ['jahia-core', 'security-updates', 'security-updates'],
                ...overrides
            }
        }
    }
});

const fetchAnswering = (...bodies) => {
    const fetchImpl = jest.fn();
    bodies.forEach(body => fetchImpl.mockReturnValueOnce(response(body)));
    return fetchImpl;
};

const queryOf = (fetchImpl, call) => JSON.parse(fetchImpl.mock.calls[call][1].body).query;

describe('showSecurityBanner', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        window.sessionStorage.clear();
        window.contextJsParameters = {contextPath: '/ctx'};
    });

    test('shows the count and the threshold decided by the server, with a link to the page', async () => {
        const fetchImpl = fetchAnswering(permission(true), banner());

        const shown = await showSecurityBanner({t, fetchImpl});

        expect(shown).toBe(true);
        const element = document.getElementById(BANNER_ID);
        expect(element.getAttribute('role')).toBe('region');
        expect(element.getAttribute('aria-label')).toBe('banner.label');
        expect(element.getAttribute('data-count')).toBe('3');
        expect(element.textContent).toContain('banner.message:3:banner.threshold.HIGH');
        const link = element.querySelector('[data-sel-role="security-updates-banner-link"]');
        expect(link.getAttribute('href')).toBe('/ctx/jahia/administration/securityUpdates');
        expect(element.querySelector('[data-sel-role="security-updates-banner-dismiss"]').tagName).toBe('BUTTON');
        expect(fetchImpl.mock.calls[0][0]).toBe('/ctx/modules/graphql');
    });

    test('asks only for the banner decision, not for the findings', async () => {
        const fetchImpl = fetchAnswering(permission(true), banner());

        await showSecurityBanner({t, fetchImpl});

        expect(queryOf(fetchImpl, 1)).toContain('banner');
        expect(queryOf(fetchImpl, 1)).not.toContain('findings');
    });

    test.each(['CRITICAL', 'MEDIUM', 'LOW'])('states the %s threshold', async severity => {
        await showSecurityBanner({t, fetchImpl: fetchAnswering(permission(true), banner({minimumSeverity: severity}))});

        expect(document.getElementById(BANNER_ID).textContent).toContain(`banner.threshold.${severity}`);
    });

    test('shows nothing when the server says the banner is not visible', async () => {
        const fetchImpl = fetchAnswering(permission(true), banner({visible: false}));

        expect(await showSecurityBanner({t, fetchImpl})).toBe(false);
        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test('dismissing removes the notice and remembers it for the session and these components', async () => {
        await showSecurityBanner({t, fetchImpl: fetchAnswering(permission(true), banner())});

        document.querySelector('[data-sel-role="security-updates-banner-dismiss"]').click();

        expect(document.getElementById(BANNER_ID)).toBeNull();
        expect(window.sessionStorage.getItem(DISMISS_KEY)).toBe('jahia-core|security-updates|security-updates');
        expect(await showSecurityBanner({t, fetchImpl: fetchAnswering(permission(true), banner())})).toBe(false);
        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test('a dismissed notice comes back when other components are concerned', async () => {
        window.sessionStorage.setItem(DISMISS_KEY, 'jahia-core|security-updates|security-updates');

        const shown = await showSecurityBanner({
            t,
            fetchImpl: fetchAnswering(permission(true), banner({count: 1, components: ['jahia-core']}))
        });

        expect(shown).toBe(true);
    });

    test('Escape inside the notice dismisses it', async () => {
        await showSecurityBanner({t, fetchImpl: fetchAnswering(permission(true), banner())});

        document.getElementById(BANNER_ID).dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));

        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test('does not query the banner for a user without adminTemplates', async () => {
        const fetchImpl = fetchAnswering(permission(false));

        expect(await showSecurityBanner({t, fetchImpl})).toBe(false);
        expect(fetchImpl).toHaveBeenCalledTimes(1);
        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test.each([
        ['a network failure', () => Promise.reject(new Error('offline'))],
        ['an HTTP error', () => Promise.resolve({ok: false, json: () => Promise.resolve({})})],
        ['a GraphQL error', () => response({errors: [{message: 'Permission denied'}]})],
        ['a malformed answer', () => response({data: {jcr: {nodeByPath: {hasPermission: true}}, securityUpdates: null}})]
    ])('stays silent on %s', async (name, failing) => {
        const fetchImpl = jest.fn(failing);

        expect(await showSecurityBanner({t, fetchImpl})).toBe(false);
        expect(document.getElementById(BANNER_ID)).toBeNull();
    });

    test('is shown only once', async () => {
        const fetchImpl = fetchAnswering(permission(true), banner(), permission(true), banner());

        await showSecurityBanner({t, fetchImpl});
        await showSecurityBanner({t, fetchImpl});

        expect(document.querySelectorAll(`#${BANNER_ID}`)).toHaveLength(1);
    });

    test('asks for the presentation and the user name with the decision', async () => {
        const fetchImpl = fetchAnswering(permission(true), banner());

        await showSecurityBanner({t, fetchImpl});

        ['backgroundColor', 'textColor', 'dismissPolicy', 'dismissHours', 'username'].forEach(field => {
            expect(queryOf(fetchImpl, 1)).toContain(field);
        });
    });

    test('applies the configured colours, used by the text, the link, the button and the focus ring', async () => {
        await showSecurityBanner({
            t,
            fetchImpl: fetchAnswering(permission(true), banner({backgroundColor: '#0B3D91', textColor: '#FFFFFF'}))
        });

        const element = document.getElementById(BANNER_ID);
        expect(element.style.getPropertyValue('--security-updates-banner-bg')).toBe('#0B3D91');
        expect(element.style.getPropertyValue('--security-updates-banner-fg')).toBe('#FFFFFF');
        const css = document.getElementById('security-updates-banner-style').textContent;
        expect(css).toContain('background: var(--security-updates-banner-bg');
        expect(css).toMatch(/a \{[^}]*color: var\(--security-updates-banner-fg/);
        expect(css).toMatch(/button \{[^}]*color: var\(--security-updates-banner-fg/);
        expect(css).toMatch(/focus-visible[^{]*\{[^}]*outline: 3px solid var\(--security-updates-banner-fg/);
    });

    test('ignores colours that are not #RRGGBB and keeps the defaults', async () => {
        await showSecurityBanner({
            t,
            fetchImpl: fetchAnswering(permission(true), banner({backgroundColor: 'red;background:url(x)', textColor: '#FFFFFF'}))
        });

        const element = document.getElementById(BANNER_ID);
        expect(element.style.getPropertyValue('--security-updates-banner-bg')).toBe('#1B1B1B');
        expect(element.style.getPropertyValue('--security-updates-banner-fg')).toBe('#FFFFFF');
    });

    test('NEVER: no Dismiss button, Escape does nothing, a stored dismissal is ignored', async () => {
        window.sessionStorage.setItem(DISMISS_KEY, 'jahia-core|security-updates|security-updates');

        await showSecurityBanner({t, fetchImpl: fetchAnswering(permission(true), banner({dismissPolicy: 'NEVER'}))});

        const element = document.getElementById(BANNER_ID);
        expect(element).not.toBeNull();
        expect(element.querySelector('[data-sel-role="security-updates-banner-dismiss"]')).toBeNull();
        element.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
        expect(document.getElementById(BANNER_ID)).not.toBeNull();
    });

    test('HOURS: the dismissal is stored per user with its time, and expires', async () => {
        const local = window.localStorage;
        local.clear();
        let now = 1_000_000;
        const hoursBanner = () => banner({dismissPolicy: 'HOURS', dismissHours: 2, username: 'jdoe'});

        await showSecurityBanner({t, now: () => now, fetchImpl: fetchAnswering(permission(true), hoursBanner())});
        document.querySelector('[data-sel-role="security-updates-banner-dismiss"]').click();

        expect(JSON.parse(local.getItem(localKey('jdoe')))).toEqual({key: 'jahia-core|security-updates|security-updates', at: 1_000_000});
        now += 3600 * 1000;
        expect(await showSecurityBanner({t, now: () => now, fetchImpl: fetchAnswering(permission(true), hoursBanner())})).toBe(false);
        now += 3600 * 1000;
        expect(await showSecurityBanner({t, now: () => now, fetchImpl: fetchAnswering(permission(true), hoursBanner())})).toBe(true);
    });

    test('UNTIL_CHANGE: still dismissed in a new session, back when the components change', async () => {
        window.localStorage.clear();
        const untilChange = overrides => banner({dismissPolicy: 'UNTIL_CHANGE', username: 'jdoe', ...overrides});

        await showSecurityBanner({t, fetchImpl: fetchAnswering(permission(true), untilChange())});
        document.querySelector('[data-sel-role="security-updates-banner-dismiss"]').click();
        window.sessionStorage.clear();

        expect(await showSecurityBanner({t, fetchImpl: fetchAnswering(permission(true), untilChange())})).toBe(false);
        expect(await showSecurityBanner({
            t,
            fetchImpl: fetchAnswering(permission(true), untilChange({count: 2, components: ['jahia-core', 'security-updates']}))
        })).toBe(true);
    });

    test('an unusable storage fails open: the banner is shown and dismissing does not throw', async () => {
        const broken = {
            getItem: () => {
                throw new Error('SecurityError');
            },
            setItem: () => {
                throw new Error('SecurityError');
            }
        };

        const shown = await showSecurityBanner({
            t,
            session: broken,
            local: broken,
            fetchImpl: fetchAnswering(permission(true), banner({dismissPolicy: 'HOURS'}))
        });

        expect(shown).toBe(true);
        expect(() => document.querySelector('[data-sel-role="security-updates-banner-dismiss"]').click()).not.toThrow();
        expect(document.getElementById(BANNER_ID)).toBeNull();
    });
});
