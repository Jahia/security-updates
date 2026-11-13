import {
    ANNOUNCED_KEY,
    BANNER_ID,
    DISMISS_KEY,
    FEEDBACK_ID,
    FLIP_HOLD_MS,
    READY_ATTRIBUTE,
    REFRESH_INTERVAL_MS,
    REQUEST_TIMEOUT_MS,
    RETRY_INTERVAL_MS,
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

const hash = text => Promise.resolve(`hash(${text})`);
const hashed = keys => keys.map(key => `hash(${key})`);
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

    test('comes last in the page, where it is drawn, so that the tab order follows the reading order', async () => {
        await show(fetchAnswering(answer()));

        expect(document.body.lastElementChild).toBe(banner());
        expect(document.body.firstElementChild).toBe(document.getElementById('content'));
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
        // The hashed keys: the session storage does not name the components either.
        expect(window.sessionStorage.getItem(ANNOUNCED_KEY)).toBe(`3|${hashed([...KEYS, 'jcontent|JSA-3']).sort().join(',')}`);
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
            expect(JSON.parse(window.sessionStorage.getItem(DISMISS_KEY))).toEqual(hashed(KEYS));
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
            expect(JSON.parse(window.localStorage.getItem(localKey('hash(jdoe)')))).toEqual({keys: hashed(KEYS), at: 1_000_000});
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
            // The same element moved under the banner right after a move: it waits for the end of the hold, then moves.
            expect(banner().getAttribute('data-position')).toBe('top');
            jest.advanceTimersByTime(FLIP_HOLD_MS);
            expect(banner().getAttribute('data-position')).toBe('bottom');
            frames.mockClear();

            removeBanner();
            document.querySelector('main').dispatchEvent(new Event('scroll'));
            expect(frames).not.toHaveBeenCalled();
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

describe('banner robustness', () => {
    const ready = () => document.documentElement.getAttribute(READY_ATTRIBUTE);
    let debug;

    beforeEach(() => {
        removeBanner();
        document.documentElement.style.cssText = '';
        document.documentElement.removeAttribute(READY_ATTRIBUTE);
        document.body.innerHTML = '<main id="content"><button type="button">Page button</button><button type="button">Other</button></main>';
        window.sessionStorage.clear();
        window.localStorage.clear();
        window.history.replaceState(null, '', '/ctx/jahia/jcontent');
        debug = jest.spyOn(console, 'debug').mockImplementation(() => undefined);
    });

    afterEach(() => {
        debug.mockRestore();
        jest.useRealTimers();
    });

    const flush = () => new Promise(resolve => {
        setTimeout(resolve, 0);
    });

    describe('test hook on <html>', () => {
        test('is pending while the request runs, and ready once the banner is shown', async () => {
            let release;
            const fetchImpl = jest.fn(() => new Promise(resolve => {
                release = () => resolve({ok: true, json: () => Promise.resolve(answer())});
            }));
            const shown = show(fetchImpl);
            expect(ready()).toBe('pending');
            expect(READY_ATTRIBUTE).toBe('data-security-updates-banner');

            release();
            await shown;
            expect(ready()).toBe('ready');
            expect(banner()).not.toBeNull();
        });

        test.each([
            ['hidden by the settings', () => show(fetchAnswering(answer({visible: false})))],
            ['not authorised', () => show(fetchAnswering({data: {jcr: {nodeByPath: {hasPermission: false}}, securityUpdates: null}}))],
            ['the page open', () => show(fetchAnswering(answer()), {isPageOpen: true})],
            ['a failed request', () => show(jest.fn(() => Promise.reject(new Error('offline'))))]
        ])('is ready once decided: %s', async (name, decide) => {
            await decide();

            expect(ready()).toBe('ready');
            expect(banner()).toBeNull();
        });

        test('is ready after Hide, and goes when the banner is stopped', async () => {
            const stop = startSecurityBanner({t, fetchImpl: jest.fn(() => response(answer())), hash});
            await flush();
            await flush();
            expect(ready()).toBe('ready');

            dismissButton().click();
            expect(ready()).toBe('ready');
            stop();
            expect(document.documentElement.hasAttribute(READY_ATTRIBUTE)).toBe(false);
        });

        test('is ready when the Security updates page opens', async () => {
            const stop = startSecurityBanner({t, fetchImpl: jest.fn(() => new Promise(() => undefined)), hash});
            expect(ready()).toBe('pending');

            window.dispatchEvent(new CustomEvent(PAGE_EVENT, {detail: {isOpen: true}}));
            expect(ready()).toBe('ready');
            stop();
        });
    });

    describe('failed requests', () => {
        test('a failed request keeps the banner shown, as it was', async () => {
            await show(fetchAnswering(answer()));
            const element = banner();

            expect(await show(jest.fn(() => Promise.reject(new Error('offline'))))).toBe(false);
            expect(await show(fetchAnswering({errors: [{message: 'Boom'}]}))).toBe(false);

            expect(banner()).toBe(element);
            expect(banner().getAttribute('data-count')).toBe('2');
        });

        test('a request without answer is abandoned after the timeout', async () => {
            jest.useFakeTimers();
            const fetchImpl = jest.fn((url, init) => new Promise((resolve, reject) => {
                init.signal.addEventListener('abort', () => reject(new Error('aborted')));
            }));
            const onFailed = jest.fn();
            const shown = show(fetchImpl, {onFailed});

            jest.advanceTimersByTime(REQUEST_TIMEOUT_MS - 1);
            await Promise.resolve();
            expect(onFailed).not.toHaveBeenCalled();
            jest.advanceTimersByTime(1);

            expect(await shown).toBe(false);
            expect(onFailed).toHaveBeenCalledTimes(1);
            expect(REQUEST_TIMEOUT_MS).toBe(20 * 1000);
        });

        test('a failed refresh keeps the banner of the previous answer', async () => {
            jest.useFakeTimers({doNotFake: ['setTimeout']});
            const fetchImpl = jest.fn()
                .mockImplementationOnce(() => response(answer()))
                .mockImplementationOnce(() => Promise.reject(new Error('restarting')))
                .mockImplementation(() => response(answer({count: 4})));
            const stop = startSecurityBanner({t, fetchImpl, hash});
            await flush();
            window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
            await flush();
            expect(fetchImpl).toHaveBeenCalledTimes(2);
            expect(banner().getAttribute('data-count')).toBe('2');
            stop();
            expect(RETRY_INTERVAL_MS).toBe(60 * 1000);
        });

        test('the retry runs once the delay has passed, and is cancelled by an answer', async () => {
            jest.useFakeTimers();
            const fetchImpl = jest.fn()
                .mockImplementationOnce(() => Promise.reject(new Error('restarting')))
                .mockImplementation(() => response(answer({count: 4})));
            const stop = startSecurityBanner({t, fetchImpl, hash});
            await jest.advanceTimersByTimeAsync(0);
            expect(fetchImpl).toHaveBeenCalledTimes(1);

            await jest.advanceTimersByTimeAsync(RETRY_INTERVAL_MS);
            expect(fetchImpl).toHaveBeenCalledTimes(2);
            expect(banner().getAttribute('data-count')).toBe('4');

            await jest.advanceTimersByTimeAsync(RETRY_INTERVAL_MS);
            expect(fetchImpl).toHaveBeenCalledTimes(2);
            stop();
        });

        test('stopping cancels a planned retry', async () => {
            jest.useFakeTimers();
            const fetchImpl = jest.fn(() => Promise.reject(new Error('offline')));
            const stop = startSecurityBanner({t, fetchImpl, hash});
            await jest.advanceTimersByTimeAsync(0);
            stop();

            await jest.advanceTimersByTimeAsync(RETRY_INTERVAL_MS);
            expect(fetchImpl).toHaveBeenCalledTimes(1);
        });
    });

    describe('timers', () => {
        test('a banner removed before its announcement leaves no pending timer', async () => {
            jest.useFakeTimers();
            await show(fetchAnswering(answer()));
            expect(jest.getTimerCount()).toBeGreaterThan(0);

            removeBanner();
            expect(jest.getTimerCount()).toBe(0);
        });

        test('stopping right after Hide cancels the pending feedback', async () => {
            jest.useFakeTimers();
            const stop = startSecurityBanner({t, fetchImpl: jest.fn(() => response(answer())), hash});
            await jest.advanceTimersByTimeAsync(0);
            dismissButton().click();
            stop();

            jest.advanceTimersByTime(1000);
            expect(feedback().textContent).toBe('');
        });

        test('a new banner cancels the feedback of the previous one', async () => {
            jest.useFakeTimers();
            await show(fetchAnswering(answer()));
            dismissButton().click();
            await show(fetchAnswering(answer({dismissalKeys: [...KEYS, 'jcontent|JSA-3']})));

            jest.advanceTimersByTime(1000);
            expect(feedback().textContent).toBe('');
        });
    });

    describe('focus after Hide', () => {
        test('goes back to the element focused when the banner appeared, when the focus came from outside the page', async () => {
            const other = document.querySelectorAll('main button')[1];
            other.focus();
            await show(fetchAnswering(answer()));
            // Shift+Tab from the browser's address bar: no focusin from the page before the banner.
            other.blur();
            dismissButton().focus();

            dismissButton().click();

            expect(document.activeElement).toBe(other);
        });
    });

    describe('dismissal keys', () => {
        test('are stored hashed, with the default hash, and still hide the banner', async () => {
            await show(fetchAnswering(answer({dismissPolicy: 'UNTIL_CHANGE'})), {hash: undefined});
            dismissButton().click();

            const stored = Object.keys(window.localStorage).map(key => window.localStorage.getItem(key)).join('');
            expect(stored).not.toContain('jahia-core');
            expect(stored).not.toContain('JSA-2026');
            expect(await show(fetchAnswering(answer({dismissPolicy: 'UNTIL_CHANGE'})), {hash: undefined})).toBe(false);
        });

        test('keys stored in clear by an older version show the banner again', async () => {
            window.sessionStorage.setItem(DISMISS_KEY, JSON.stringify(KEYS));

            expect(await show(fetchAnswering(answer()))).toBe(true);
        });

        test('keys that cannot be hashed fail open', async () => {
            const failing = text => (text.includes('|') ? Promise.reject(new Error('no crypto')) : hash(text));
            await show(fetchAnswering(answer({dismissPolicy: 'UNTIL_CHANGE'})), {hash: failing});
            dismissButton().click();

            expect(await show(fetchAnswering(answer({dismissPolicy: 'UNTIL_CHANGE'})), {hash: failing})).toBe(true);
        });
    });

    describe('position', () => {
        const place = (element, rect) => {
            element.getBoundingClientRect = () => ({height: rect.bottom - rect.top, ...rect});
        };

        test('stays when the focused element would be under it at the other edge too (a tall region)', async () => {
            await show(fetchAnswering(answer()));
            place(banner(), {top: 600, bottom: 700, left: 800, right: 1200});
            const button = document.querySelector('main button');
            place(button, {top: 0, bottom: 768, left: 0, right: 1024});

            button.focus();

            expect(banner().getAttribute('data-position')).toBe('bottom');
        });

        test('a newly focused element moves it at once, even during the hold', async () => {
            await show(fetchAnswering(answer()));
            place(banner(), {top: 600, bottom: 700, left: 800, right: 1200});
            const [first, second] = document.querySelectorAll('main button');
            place(first, {top: 650, bottom: 680, left: 900, right: 1000});
            first.focus();
            expect(banner().getAttribute('data-position')).toBe('top');

            place(banner(), {top: 68, bottom: 168, left: 800, right: 1200});
            place(second, {top: 100, bottom: 130, left: 900, right: 1000});
            second.focus();
            expect(banner().getAttribute('data-position')).toBe('bottom');
        });

        test('the document style is written only when the scroll padding changes', async () => {
            jest.useFakeTimers();
            await show(fetchAnswering(answer()));
            place(banner(), {top: 600, bottom: 700, left: 800, right: 1200});
            const write = jest.spyOn(document.documentElement.style, 'setProperty');

            window.dispatchEvent(new Event('resize'));
            jest.advanceTimersByTime(20);
            expect(write).toHaveBeenCalledTimes(1);
            window.dispatchEvent(new Event('resize'));
            jest.advanceTimersByTime(20);
            document.dispatchEvent(new Event('scroll'));
            jest.advanceTimersByTime(20);

            expect(write).toHaveBeenCalledTimes(1);
            write.mockRestore();
        });

        test('becomes a compact strip on a small viewport (400% zoom), never a box over most of the screen', async () => {
            await show(fetchAnswering(answer()));

            const css = document.getElementById('security-updates-banner-style').textContent;
            const compact = css.slice(css.indexOf('@media (max-width: 40rem), (max-height: 40rem)'));
            expect(compact).toMatch(/left: 0; right: 0; bottom: 0; max-width: none; max-height: 40vh; overflow-y: auto/);
            expect(compact).toMatch(/\[data-position="top"\] \{ top: 0; bottom: auto; \}/);
        });
    });
});
