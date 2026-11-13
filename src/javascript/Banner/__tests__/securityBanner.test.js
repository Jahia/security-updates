import {
    ANNOUNCED_KEY,
    BANNER_ID,
    DISMISS_KEY,
    FEEDBACK_ID,
    REFRESH_INTERVAL_MS,
    removeBanner,
    showSecurityBanner,
    startSecurityBanner,
    VISIBILITY_MIN_INTERVAL_MS
} from '../securityBanner';
import {localKey} from '../bannerDismissal';
import {translate as t} from '../../__mocks__/reactI18next';
import {CHANGED_EVENT, PAGE_EVENT} from '../../common/constants';

const KEYS = ['jahia-core|JSA-2026-0001', 'security-updates|JSA-2026-0002'];

const response = body => Promise.resolve({ok: true, json: () => Promise.resolve(body)});

const answer = (overrides = {}, hasPermission = true) => ({
    data: {
        jcr: {nodeByPath: {hasPermission}},
        securityUpdates: {
            banner: {
                visible: true,
                count: 2,
                minimumSeverity: 'HIGH',
                components: ['jahia-core', 'security-updates'],
                dismissalKeys: KEYS,
                backgroundColor: '#1B1B1B',
                textColor: '#FFFFFF',
                dismissPolicy: 'SESSION',
                dismissHours: 24,
                username: 'jdoe',
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

const hash = username => Promise.resolve(`hash(${username})`);
const show = (fetchImpl, options = {}) => showSecurityBanner({t, language: 'fr', fetchImpl, hash, ...options});
const banner = () => document.getElementById(BANNER_ID);
const dismissButton = () => document.querySelector('[data-sel-role="security-updates-banner-dismiss"]');
const queryOf = (fetchImpl, call) => JSON.parse(fetchImpl.mock.calls[call][1].body).query;

const live = () => banner().querySelector('[role="status"]');
const feedback = () => document.getElementById(FEEDBACK_ID);

describe('showSecurityBanner', () => {
    beforeEach(() => {
        removeBanner();
        document.documentElement.style.cssText = '';
        document.body.innerHTML = '<main id="content"><button type="button">Page button</button></main>';
        window.sessionStorage.clear();
        window.localStorage.clear();
        window.contextJsParameters = {contextPath: '/ctx'};
        window.history.replaceState(null, '', '/ctx/jahia/jcontent');
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    test('one request asks for the permission and the banner decision, with the dismissal keys, not the findings', async () => {
        const fetchImpl = fetchAnswering(answer());

        await show(fetchImpl);

        expect(fetchImpl).toHaveBeenCalledTimes(1);
        expect(fetchImpl.mock.calls[0][0]).toBe('/ctx/modules/graphql');
        const query = queryOf(fetchImpl, 0);
        ['hasPermission', 'banner', 'dismissalKeys', 'backgroundColor', 'textColor', 'dismissPolicy', 'dismissHours', 'username']
            .forEach(field => expect(query).toContain(field));
        expect(query).not.toContain('findings');
    });

    test('shows the count of components with the plural form and the threshold, and links to the page', async () => {
        expect(await show(fetchAnswering(answer()))).toBe(true);

        const element = banner();
        expect(element.getAttribute('role')).toBe('region');
        expect(element.getAttribute('aria-label')).toBe('banner.label');
        expect(element.getAttribute('lang')).toBe('fr');
        expect(element.getAttribute('data-count')).toBe('2');
        expect(element.querySelector('p').textContent).toBe('banner.message.other {n=2, threshold=banner.threshold.HIGH}');
        const link = element.querySelector('[data-sel-role="security-updates-banner-link"]');
        expect(link.getAttribute('href')).toBe('/ctx/jahia/administration/securityUpdates');
        expect(dismissButton().tagName).toBe('BUTTON');
        expect(dismissButton().textContent).toBe('banner.dismiss');
    });

    test('one component uses the singular form', async () => {
        await show(fetchAnswering(answer({count: 1, components: ['jahia-core'], dismissalKeys: [KEYS[0]]})), {language: 'en'});

        expect(banner().querySelector('p').textContent).toBe('banner.message.one {n=1, threshold=banner.threshold.HIGH}');
    });

    test('comes first in the page, so that keyboard users reach it early', async () => {
        await show(fetchAnswering(answer()));

        expect(document.body.firstElementChild).toBe(banner());
    });

    test('announces itself politely once, a moment after it appears', async () => {
        jest.useFakeTimers();
        await show(fetchAnswering(answer()));

        expect(live().textContent).toBe('');
        jest.advanceTimersByTime(1500);
        expect(live().textContent).toBe('banner.message.other {n=2, threshold=banner.threshold.HIGH}');
    });

    test('is not announced again when it comes back with the same count and keys, only when they change', async () => {
        jest.useFakeTimers();
        await show(fetchAnswering(answer()));
        jest.advanceTimersByTime(1500);
        removeBanner();

        await show(fetchAnswering(answer()));
        jest.advanceTimersByTime(1500);
        expect(live().textContent).toBe('');
        await show(fetchAnswering(answer()));
        jest.advanceTimersByTime(1500);
        expect(live().textContent).toBe('');

        await show(fetchAnswering(answer({count: 3, dismissalKeys: [...KEYS, 'jcontent|JSA-3']})));
        jest.advanceTimersByTime(1500);
        expect(live().textContent).toBe('banner.message.other {n=3, threshold=banner.threshold.HIGH}');
        expect(window.sessionStorage.getItem(ANNOUNCED_KEY)).toBe(`3|${[...KEYS, 'jcontent|JSA-3'].sort().join(',')}`);
    });

    test('without a usable session storage, the page remembers what it announced', async () => {
        jest.useFakeTimers();
        const debug = jest.spyOn(console, 'debug').mockImplementation(() => undefined);
        const broken = {
            getItem: () => {
                throw new Error('SecurityError');
            },
            setItem: () => {
                throw new Error('SecurityError');
            }
        };
        await show(fetchAnswering(answer({count: 7})), {session: broken});
        jest.advanceTimersByTime(1500);
        expect(live().textContent).toContain('n=7');
        removeBanner();

        await show(fetchAnswering(answer({count: 7})), {session: broken});
        jest.advanceTimersByTime(1500);
        expect(live().textContent).toBe('');
        await show(fetchAnswering(answer({count: 7})), {session: null});
        jest.advanceTimersByTime(1500);
        expect(live().textContent).toBe('');
        debug.mockRestore();
    });

    test.each(['CRITICAL', 'MEDIUM', 'LOW'])('states the %s threshold', async severity => {
        await show(fetchAnswering(answer({minimumSeverity: severity})));

        expect(banner().textContent).toContain(`banner.threshold.${severity}`);
    });

    test('shows nothing, and removes a banner shown before, when the server says it is not visible', async () => {
        await show(fetchAnswering(answer()));

        expect(await show(fetchAnswering(answer({visible: false})))).toBe(false);
        expect(banner()).toBeNull();
    });

    test('a user without adminTemplates gets nothing, even if the banner field is denied', async () => {
        const denied = {data: {jcr: {nodeByPath: {hasPermission: false}}, securityUpdates: null}, errors: [{message: 'Permission denied'}]};

        expect(await show(fetchAnswering(denied))).toBe(false);
        expect(banner()).toBeNull();
    });

    test.each([
        ['a network failure', () => Promise.reject(new Error('offline'))],
        ['an HTTP error', () => Promise.resolve({ok: false, json: () => Promise.resolve({})})],
        ['a GraphQL error without data', () => response({errors: [{message: 'Boom'}]})],
        ['a malformed answer', () => response({data: {jcr: {nodeByPath: {hasPermission: true}}, securityUpdates: null}})],
        ['an answer that is not JSON', () => Promise.resolve({ok: true, json: () => Promise.reject(new SyntaxError('x'))})]
    ])('stays silent on %s', async (name, failing) => {
        const debug = jest.spyOn(console, 'debug').mockImplementation(() => undefined);

        expect(await show(jest.fn(failing))).toBe(false);
        expect(banner()).toBeNull();
        expect(debug).toHaveBeenCalled();
        debug.mockRestore();
    });

    test('a second answer updates the banner in place', async () => {
        await show(fetchAnswering(answer()));
        const first = banner();

        await show(fetchAnswering(answer({count: 3, dismissalKeys: [...KEYS, 'jcontent|JSA-3']})));

        expect(document.querySelectorAll(`#${BANNER_ID}`)).toHaveLength(1);
        expect(banner()).toBe(first);
        expect(banner().getAttribute('data-count')).toBe('3');
    });

    test('is not shown on the Security updates page itself', async () => {
        window.history.replaceState(null, '', '/ctx/jahia/administration/securityUpdates');

        expect(await show(fetchAnswering(answer()))).toBe(false);
        expect(banner()).toBeNull();
    });

    test('is not shown while the page says it is open', async () => {
        const fetchImpl = fetchAnswering(answer());

        expect(await show(fetchImpl, {isPageOpen: true})).toBe(false);
        expect(fetchImpl).not.toHaveBeenCalled();
    });

    describe('hiding', () => {
        test('hiding removes the banner, gives the focus back to where it was, and is remembered for the tab', async () => {
            await show(fetchAnswering(answer()));
            const pageButton = document.querySelector('main button');
            pageButton.focus();
            dismissButton().focus();

            dismissButton().click();

            expect(banner()).toBeNull();
            expect(document.activeElement).toBe(pageButton);
            expect(document.getElementById('content')).not.toHaveAttribute('tabindex');
            expect(JSON.parse(window.sessionStorage.getItem(DISMISS_KEY))).toEqual(KEYS);
            expect(await show(fetchAnswering(answer()))).toBe(false);
        });

        test('when the focus came from nowhere, it stays on the document: Jahia\'s main element is not made focusable', async () => {
            await show(fetchAnswering(answer()));
            dismissButton().focus();

            dismissButton().click();

            expect(document.activeElement).toBe(document.body);
            expect(document.getElementById('content')).not.toHaveAttribute('tabindex');
        });

        test('an element that left the page does not get the focus back', async () => {
            await show(fetchAnswering(answer()));
            document.querySelector('main button').focus();
            document.querySelector('main').innerHTML = '';

            dismissButton().click();

            expect(document.activeElement).toBe(document.body);
        });

        test.each([
            ['SESSION', {}, 'banner.hidden.SESSION'],
            ['HOURS', {dismissHours: 2}, 'banner.hidden.HOURS.other {n=2}'],
            ['HOURS', {dismissHours: 1}, 'banner.hidden.HOURS.one {n=1}'],
            ['UNTIL_CHANGE', {}, 'banner.hidden.UNTIL_CHANGE']
        ])('hiding with the %s policy says so politely, and when the banner comes back', async (policy, extra, text) => {
            jest.useFakeTimers();
            await show(fetchAnswering(answer({dismissPolicy: policy, ...extra})), {language: 'en'});
            expect(feedback()).toHaveAttribute('role', 'status');
            expect(feedback().textContent).toBe('');

            dismissButton().click();
            jest.advanceTimersByTime(200);

            expect(feedback().textContent).toBe(text);
            expect(banner()).toBeNull();
        });

        test('the hide button is kept across updates, so that it keeps the focus, and acts with the latest settings', async () => {
            await show(fetchAnswering(answer()));
            const button = dismissButton();
            button.focus();

            await show(fetchAnswering(answer({dismissPolicy: 'HOURS', dismissHours: 3, count: 3, dismissalKeys: [...KEYS, 'jcontent|JSA-3']})));

            expect(dismissButton()).toBe(button);
            expect(document.activeElement).toBe(button);
            expect(document.querySelectorAll('[data-sel-role="security-updates-banner-dismiss"]')).toHaveLength(1);
            button.click();
            expect(window.sessionStorage.getItem(DISMISS_KEY)).toBeNull();
            expect(JSON.parse(window.localStorage.getItem(localKey('hash(jdoe)'))).keys).toHaveLength(3);
        });

        test('the hide button goes when the policy becomes NEVER, and comes back after', async () => {
            await show(fetchAnswering(answer()));
            await show(fetchAnswering(answer({dismissPolicy: 'NEVER'})));
            expect(dismissButton()).toBeNull();

            await show(fetchAnswering(answer()));
            expect(dismissButton().textContent).toBe('banner.dismiss');
        });

        test('a new advisory for a component already hidden shows the banner again', async () => {
            await show(fetchAnswering(answer({dismissPolicy: 'UNTIL_CHANGE'})));
            dismissButton().click();

            expect(await show(fetchAnswering(answer({dismissPolicy: 'UNTIL_CHANGE', dismissalKeys: [...KEYS, 'jahia-core|JSA-2026-0099']})))).toBe(true);
        });

        test('fewer keys (a fix) keep the banner hidden', async () => {
            await show(fetchAnswering(answer({dismissPolicy: 'UNTIL_CHANGE'})));
            dismissButton().click();

            expect(await show(fetchAnswering(answer({dismissPolicy: 'UNTIL_CHANGE', count: 1, dismissalKeys: [KEYS[1]]})))).toBe(false);
        });

        test('HOURS: stored under a hash of the user name, with its time, and expires', async () => {
            let now = 1_000_000;
            const hours = () => answer({dismissPolicy: 'HOURS', dismissHours: 2});

            await show(fetchAnswering(hours()), {now: () => now});
            dismissButton().click();

            expect(window.localStorage.getItem(localKey('jdoe'))).toBeNull();
            expect(JSON.parse(window.localStorage.getItem(localKey('hash(jdoe)')))).toEqual({keys: KEYS, at: 1_000_000});
            now += 3600 * 1000;
            expect(await show(fetchAnswering(hours()), {now: () => now})).toBe(false);
            now += 3600 * 1000;
            expect(await show(fetchAnswering(hours()), {now: () => now})).toBe(true);
        });

        test('NEVER: no hide button, a stored dismissal is ignored', async () => {
            window.sessionStorage.setItem(DISMISS_KEY, JSON.stringify(KEYS));

            await show(fetchAnswering(answer({dismissPolicy: 'NEVER'})));

            expect(banner()).not.toBeNull();
            expect(dismissButton()).toBeNull();
        });

        test('Escape does not hide the banner (the button does)', async () => {
            await show(fetchAnswering(answer()));

            banner().dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));

            expect(banner()).not.toBeNull();
        });

        test('an unusable storage fails open: the banner is shown and hiding does not throw', async () => {
            const broken = {
                getItem: () => {
                    throw new Error('SecurityError');
                },
                setItem: () => {
                    throw new Error('SecurityError');
                }
            };

            expect(await show(fetchAnswering(answer({dismissPolicy: 'HOURS'})), {session: broken, local: broken})).toBe(true);
            expect(() => dismissButton().click()).not.toThrow();
            expect(banner()).toBeNull();
        });

        test('a failing hash fails open', async () => {
            const failing = () => Promise.reject(new Error('no crypto'));

            expect(await show(fetchAnswering(answer({dismissPolicy: 'UNTIL_CHANGE'})), {hash: failing})).toBe(true);
        });
    });

    describe('presentation', () => {
        test('applies the configured colours, used by the text, the link, the button and the focus ring', async () => {
            await show(fetchAnswering(answer({backgroundColor: '#0B3D91', textColor: '#FFFFFF'})));

            const element = banner();
            expect(element.style.getPropertyValue('--security-updates-banner-bg')).toBe('#0B3D91');
            expect(element.style.getPropertyValue('--security-updates-banner-fg')).toBe('#FFFFFF');
            const css = document.getElementById('security-updates-banner-style').textContent;
            expect(css).toContain('background: var(--security-updates-banner-bg');
            expect(css).toContain('font-family: var(--moon-font-family');
            expect(css).toMatch(/a \{[^}]*color: var\(--security-updates-banner-fg/);
            expect(css).toMatch(/button \{[^}]*color: var\(--security-updates-banner-fg/);
            expect(css).toMatch(/focus-visible[^{]*\{[^}]*outline: 3px solid var\(--security-updates-banner-fg/);
        });

        test('sizes text in rem and gives the link and the button 44 by 44 pixel targets', async () => {
            await show(fetchAnswering(answer()));

            const css = document.getElementById('security-updates-banner-style').textContent;
            expect(css).toMatch(/font-size: [\d.]+rem/);
            expect(css).not.toMatch(/font-size: \d+px/);
            expect(css).toMatch(/a, #security-updates-banner button \{[^}]*min-height: 2\.75rem[^}]*min-width: 2\.75rem/);
        });

        test('ignores colours that are not #RRGGBB and keeps the defaults', async () => {
            await show(fetchAnswering(answer({backgroundColor: 'red;background:url(x)', textColor: '#FFFFFF'})));

            expect(banner().style.getPropertyValue('--security-updates-banner-bg')).toBe('#1B1B1B');
            expect(banner().style.getPropertyValue('--security-updates-banner-fg')).toBe('#FFFFFF');
        });

        test('keeps a scroll padding as tall as the banner on its edge, and restores the page\'s own when it goes', async () => {
            jest.useFakeTimers();
            document.documentElement.style.setProperty('scroll-padding-top', '5px');
            await show(fetchAnswering(answer()));
            const style = document.documentElement.style;
            expect(style.getPropertyValue('scroll-padding-bottom')).toBe('calc(0px + 1rem)');

            banner().getBoundingClientRect = () => ({top: 600, bottom: 700, left: 800, right: 1200, height: 100});
            window.dispatchEvent(new Event('resize'));
            jest.advanceTimersByTime(20);
            expect(style.getPropertyValue('scroll-padding-bottom')).toBe('calc(100px + 1rem)');

            const button = document.querySelector('main button');
            button.getBoundingClientRect = () => ({top: 650, bottom: 680, left: 900, right: 1000});
            button.focus();
            expect(banner().getAttribute('data-position')).toBe('top');
            expect(style.getPropertyValue('scroll-padding-top')).toBe('calc(100px + 1rem)');
            expect(style.getPropertyValue('scroll-padding-bottom')).toBe('');

            removeBanner();
            expect(style.getPropertyValue('scroll-padding-top')).toBe('5px');
            expect(style.getPropertyValue('scroll-padding-bottom')).toBe('');
        });

        test('checks the focused element again after the browser scrolled it, and on scroll and resize, once per frame', async () => {
            jest.useFakeTimers();
            await show(fetchAnswering(answer()));
            const frames = jest.spyOn(window, 'requestAnimationFrame');
            const button = document.querySelector('main button');
            banner().getBoundingClientRect = () => ({top: 600, bottom: 700, left: 800, right: 1200, height: 100});
            button.getBoundingClientRect = () => ({top: 10, bottom: 40, left: 10, right: 100});
            button.focus();
            expect(banner().getAttribute('data-position')).toBe('bottom');

            // Scrolled into view under the banner, inside a container of the page (its scroll event does not bubble).
            button.getBoundingClientRect = () => ({top: 650, bottom: 680, left: 900, right: 1000});
            jest.advanceTimersByTime(20);
            expect(banner().getAttribute('data-position')).toBe('top');

            banner().getBoundingClientRect = () => ({top: 0, bottom: 100, left: 800, right: 1200, height: 100});
            button.getBoundingClientRect = () => ({top: 50, bottom: 80, left: 900, right: 1000});
            frames.mockClear();
            document.querySelector('main').dispatchEvent(new Event('scroll'));
            document.querySelector('main').dispatchEvent(new Event('scroll'));
            window.dispatchEvent(new Event('resize'));
            expect(frames).toHaveBeenCalledTimes(1);
            jest.advanceTimersByTime(20);
            expect(banner().getAttribute('data-position')).toBe('bottom');

            removeBanner();
            document.querySelector('main').dispatchEvent(new Event('scroll'));
            expect(frames).toHaveBeenCalledTimes(1);
            frames.mockRestore();
        });

        test('a frame still pending when the banner goes does nothing', async () => {
            jest.useFakeTimers();
            await show(fetchAnswering(answer()));
            window.dispatchEvent(new Event('resize'));
            const element = banner();
            element.remove();

            expect(() => jest.advanceTimersByTime(20)).not.toThrow();
            removeBanner();
        });

        test('moves to the other edge when the focused element would be under it', async () => {
            await show(fetchAnswering(answer()));
            const button = document.querySelector('main button');
            banner().getBoundingClientRect = () => ({top: 600, bottom: 700, left: 800, right: 1200});
            button.getBoundingClientRect = () => ({top: 650, bottom: 680, left: 900, right: 1000});

            button.focus();
            expect(banner().getAttribute('data-position')).toBe('top');

            button.getBoundingClientRect = () => ({top: 10, bottom: 40, left: 10, right: 100});
            button.blur();
            button.focus();
            expect(banner().getAttribute('data-position')).toBe('top');
        });
    });
});

describe('startSecurityBanner', () => {
    beforeEach(() => {
        removeBanner();
        document.body.innerHTML = '<main id="content"></main>';
        window.sessionStorage.clear();
        window.history.replaceState(null, '', '/ctx/jahia/jcontent');
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    const flush = () => new Promise(resolve => {
        setTimeout(resolve, 0);
    });

    test('asks the server again after a check or a settings change, every 15 minutes, and when the tab is shown again', async () => {
        jest.useFakeTimers({doNotFake: ['setTimeout']});
        const fetchImpl = jest.fn(() => response(answer()));
        const stop = startSecurityBanner({t, fetchImpl, hash});
        await flush();
        expect(fetchImpl).toHaveBeenCalledTimes(1);
        expect(banner()).not.toBeNull();

        window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
        await flush();
        expect(fetchImpl).toHaveBeenCalledTimes(2);

        jest.advanceTimersByTime(REFRESH_INTERVAL_MS);
        await flush();
        expect(fetchImpl).toHaveBeenCalledTimes(3);
        expect(REFRESH_INTERVAL_MS).toBe(15 * 60 * 1000);

        // Shown again right after an answer: the server is not asked again.
        document.dispatchEvent(new Event('visibilitychange'));
        await flush();
        expect(fetchImpl).toHaveBeenCalledTimes(3);
        expect(VISIBILITY_MIN_INTERVAL_MS).toBe(30 * 1000);

        jest.advanceTimersByTime(VISIBILITY_MIN_INTERVAL_MS);
        document.dispatchEvent(new Event('visibilitychange'));
        await flush();
        expect(fetchImpl).toHaveBeenCalledTimes(4);

        stop();
        expect(banner()).toBeNull();
        window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
        jest.advanceTimersByTime(REFRESH_INTERVAL_MS);
        await flush();
        expect(fetchImpl).toHaveBeenCalledTimes(4);
    });

    test('the tab shown again asks the server when the last request failed', async () => {
        const debug = jest.spyOn(console, 'debug').mockImplementation(() => undefined);
        const fetchImpl = jest.fn(() => Promise.reject(new Error('offline')));
        const stop = startSecurityBanner({t, fetchImpl, hash});
        await flush();

        document.dispatchEvent(new Event('visibilitychange'));
        await flush();

        expect(fetchImpl).toHaveBeenCalledTimes(2);
        stop();
        debug.mockRestore();
    });

    test('is hidden while the Security updates page is open, and asked again when it closes', async () => {
        const fetchImpl = jest.fn(() => response(answer()));
        const stop = startSecurityBanner({t, fetchImpl, hash});
        await flush();
        expect(banner()).not.toBeNull();

        window.dispatchEvent(new CustomEvent(PAGE_EVENT, {detail: {isOpen: true}}));
        expect(banner()).toBeNull();
        window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
        await flush();
        expect(banner()).toBeNull();

        window.dispatchEvent(new CustomEvent(PAGE_EVENT, {detail: {isOpen: false}}));
        await flush();
        expect(banner()).not.toBeNull();
        stop();
    });

    test('an older answer arriving last does not override a newer one', async () => {
        let releaseFirst;
        const fetchImpl = jest.fn()
            .mockImplementationOnce(() => new Promise(resolve => {
                releaseFirst = () => resolve({ok: true, json: () => Promise.resolve(answer({count: 1}))});
            }))
            .mockImplementationOnce(() => response(answer({count: 5})));
        const stop = startSecurityBanner({t, fetchImpl, hash});
        window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
        await flush();
        releaseFirst();
        await flush();

        expect(banner().getAttribute('data-count')).toBe('5');
        stop();
    });
});
