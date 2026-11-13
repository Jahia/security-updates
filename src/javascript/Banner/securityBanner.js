import {adminUrl, graphqlUrl} from '../SecurityUpdates/securityUpdatesUtils';
import {normalizeHex} from '../Settings/colorUtils';
import {CHANGED_EVENT, DEFAULT_BANNER_BACKGROUND, DEFAULT_BANNER_TEXT, PAGE_EVENT, ROUTE_KEY, THRESHOLD_SEVERITIES} from '../common/constants';
import {pluralKey, values} from '../common/i18n';
import {hashUser, isDismissed, recordDismissal, SESSION_KEY} from './bannerDismissal';

export const BANNER_ID = 'security-updates-banner';
export const DISMISS_KEY = SESSION_KEY;
/** The banner asks the server again this often, besides after a check, a settings change or when the tab is shown. */
export const REFRESH_INTERVAL_MS = 15 * 60 * 1000;
/** Showing the browser tab again asks the server only when its last answer is older than this. */
export const VISIBILITY_MIN_INTERVAL_MS = 30 * 1000;
/** Polite status line, outside the banner, that says the banner was hidden and when it comes back. */
export const FEEDBACK_ID = 'security-updates-banner-feedback';
/** What the banner last announced in this browser tab (count and dismissal keys), so it is not announced again. */
export const ANNOUNCED_KEY = 'security-updates-banner-announced';
const STYLE_ID = 'security-updates-banner-style';
const DISMISS_SELECTOR = '[data-sel-role="security-updates-banner-dismiss"]';
const ANNOUNCE_DELAY_MS = 1000;
const FEEDBACK_DELAY_MS = 100;

// One request: the permission (users without adminTemplates get a denied banner field and nothing is shown) and the
// decision of the server, which validates the presentation: the client only renders.
const BANNER_QUERY = 'query SecurityUpdatesBanner { jcr { nodeByPath(path: "/") { hasPermission(permissionName: "adminTemplates") } } ' +
    'securityUpdates { banner { visible count minimumSeverity components dismissalKeys backgroundColor textColor ' +
    'dismissPolicy dismissHours username } } }';

// The colours come from the settings, validated server-side to a contrast of at least 7:1 (WCAG AAA). The text, the
// link, the button border and the focus ring all use the text colour, so each of them keeps that contrast against
// the background (the focus ring needs 3:1). The banner never relies on colour alone: the message says what it is.
// Sizes are in rem (they follow the user's text size); the link and the button are 44 by 44 pixel targets.
const CSS = `
#${BANNER_ID} { position: fixed; right: 1rem; bottom: 1rem; z-index: 9999;
  max-width: min(30rem, calc(100vw - 2rem)); display: flex; flex-wrap: wrap; gap: 0.5rem 1rem;
  align-items: center; padding: 0.5rem 1rem; border-radius: 4px;
  background: var(--security-updates-banner-bg, ${DEFAULT_BANNER_BACKGROUND}); color: var(--security-updates-banner-fg, ${DEFAULT_BANNER_TEXT});
  font-family: var(--moon-font-family, 'Nunito Sans', sans-serif); font-size: 0.875rem; line-height: 1.5;
  box-shadow: 0 4px 16px rgba(0, 0, 0, .35); }
#${BANNER_ID}[data-position="top"] { top: 1rem; bottom: auto; }
#${BANNER_ID} p { margin: 0; flex: 1 1 100%; }
#${BANNER_ID} a, #${BANNER_ID} button { display: inline-flex; align-items: center; justify-content: center;
  min-height: 2.75rem; min-width: 2.75rem; box-sizing: border-box; }
#${BANNER_ID} a { color: var(--security-updates-banner-fg, ${DEFAULT_BANNER_TEXT}); font-weight: 700; text-decoration: underline;
  padding: 0 0.25rem; }
#${BANNER_ID} button { background: transparent; color: var(--security-updates-banner-fg, ${DEFAULT_BANNER_TEXT});
  border: 1px solid var(--security-updates-banner-fg, ${DEFAULT_BANNER_TEXT}); border-radius: 4px;
  padding: 0.25rem 0.75rem; font: inherit; cursor: pointer; }
#${BANNER_ID} a:focus-visible, #${BANNER_ID} button:focus-visible {
  outline: 3px solid var(--security-updates-banner-fg, ${DEFAULT_BANNER_TEXT}); outline-offset: 2px; }
#${BANNER_ID} .security-updates-banner-sr, #${FEEDBACK_ID} { position: absolute; width: 1px; height: 1px; margin: -1px;
  padding: 0; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
`;

const debug = (message, error) => {
    // The banner is best effort: a failure only leaves it out, with a trace for whoever looks at the console.
    console.debug(`[security-updates] ${message}`, error);
};

const fetchBanner = async fetchImpl => {
    const response = await fetchImpl(graphqlUrl(), {
        method: 'POST',
        credentials: 'same-origin',
        headers: {'Content-Type': 'application/json', Accept: 'application/json'},
        body: JSON.stringify({query: BANNER_QUERY})
    });
    if (!response.ok) {
        throw new Error(`GraphQL request failed (HTTP ${response.status})`);
    }

    const payload = await response.json();
    // A user without adminTemplates gets an error on the banner field: the permission says it is not an error here.
    if (payload?.data?.jcr?.nodeByPath?.hasPermission !== true) {
        if (!payload?.data) {
            throw new Error('GraphQL error');
        }

        return null;
    }

    const banner = payload.data.securityUpdates?.banner;
    if (!banner || typeof banner !== 'object') {
        throw new Error('Malformed banner answer');
    }

    return banner;
};

// Reading window.localStorage itself throws when storage is blocked: such a storage is treated as absent.
const storage = name => {
    try {
        return window[name];
    } catch {
        return null;
    }
};

const ensureStyle = () => {
    if (!document.getElementById(STYLE_ID)) {
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = CSS;
        document.head.appendChild(style);
    }
};

const applyColors = (element, {backgroundColor, textColor}) => {
    const background = normalizeHex(backgroundColor);
    const text = normalizeHex(textColor);
    const isValid = background && text;
    element.style.setProperty('--security-updates-banner-bg', isValid ? background : DEFAULT_BANNER_BACKGROUND);
    element.style.setProperty('--security-updates-banner-fg', isValid ? text : DEFAULT_BANNER_TEXT);
};

const isOnPage = () => window.location.pathname.replace(/\/+$/, '').endsWith(`/administration/${ROUTE_KEY}`);

const overlaps = (a, b) => a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;

// The dismissal settings and texts of the banner shown, read when its button is used (the button is kept across
// updates, so its click handler must not keep those of its first render).
const dismissals = new WeakMap();
// The element that had the focus before it went into the banner: it gets the focus back when the banner is hidden.
let lastFocusOutside = null;
// The page's own scroll-padding values, restored when the banner goes.
let savedScrollPadding = null;
let frame = null;

const restoreScrollPadding = (style, side) => {
    const value = savedScrollPadding[side];
    if (value) {
        style.setProperty(`scroll-padding-${side}`, value);
    } else {
        style.removeProperty(`scroll-padding-${side}`);
    }
};

// Elements scrolled into view (by the keyboard, or a link to an anchor) stop clear of the banner: the document keeps
// a scroll padding as tall as the banner, on the edge where the banner is.
const updateScrollPadding = element => {
    const style = document.documentElement.style;
    if (!savedScrollPadding) {
        savedScrollPadding = {top: style.getPropertyValue('scroll-padding-top'), bottom: style.getPropertyValue('scroll-padding-bottom')};
    }

    const side = element.getAttribute('data-position') === 'top' ? 'top' : 'bottom';
    style.setProperty(`scroll-padding-${side}`, `calc(${Math.ceil(element.getBoundingClientRect().height)}px + 1rem)`);
    restoreScrollPadding(style, side === 'top' ? 'bottom' : 'top');
};

const clearScrollPadding = () => {
    if (savedScrollPadding) {
        const style = document.documentElement.style;
        restoreScrollPadding(style, 'top');
        restoreScrollPadding(style, 'bottom');
        savedScrollPadding = null;
    }
};

// The banner is fixed over the page: when the focused element would be under it, it moves to the other edge, so that
// it never hides the focus (WCAG 2.4.12).
const keepFocusVisible = target => {
    const element = document.getElementById(BANNER_ID);
    if (!element || !target || target === document.body || element.contains(target) || typeof target.getBoundingClientRect !== 'function') {
        return;
    }

    if (overlaps(target.getBoundingClientRect(), element.getBoundingClientRect())) {
        element.setAttribute('data-position', element.getAttribute('data-position') === 'top' ? 'bottom' : 'top');
        updateScrollPadding(element);
    }
};

// Once per animation frame at most: after the browser has scrolled the focused element into view, and on scroll and
// resize (the focused element or the banner moved).
const scheduleCheck = () => {
    if (frame !== null) {
        return;
    }

    frame = window.requestAnimationFrame(() => {
        frame = null;
        const element = document.getElementById(BANNER_ID);
        if (element) {
            updateScrollPadding(element);
            keepFocusVisible(document.activeElement);
        }
    });
};

const onFocusIn = event => {
    const element = document.getElementById(BANNER_ID);
    if (element && !element.contains(event.target)) {
        lastFocusOutside = event.target;
    }

    keepFocusVisible(event.target);
    scheduleCheck();
};

const listen = isOn => {
    const method = isOn ? 'addEventListener' : 'removeEventListener';
    document[method]('focusin', onFocusIn);
    // Capture: the scrolls of the Jahia UI happen in its own containers, whose scroll events do not bubble.
    document[method]('scroll', scheduleCheck, true);
    window[method]('resize', scheduleCheck);
};

export const removeBanner = () => {
    document.getElementById(BANNER_ID)?.remove();
    lastFocusOutside = null;
    listen(false);
    if (frame !== null) {
        window.cancelAnimationFrame(frame);
        frame = null;
    }

    clearScrollPadding();
};

const message = (t, banner, language) => {
    const severity = THRESHOLD_SEVERITIES.includes(banner.minimumSeverity) ? banner.minimumSeverity : 'HIGH';
    const count = Number(banner.count) || 0;
    // TextContent never interprets markup, so i18next's HTML escaping is turned off (values()).
    return t(`banner.message.${pluralKey(count, language)}`, values({n: count, threshold: t(`banner.threshold.${severity}`)}));
};

// Kept in the page once created (also after the banner is hidden), so that its later text is announced.
const feedbackElement = () => {
    let feedback = document.getElementById(FEEDBACK_ID);
    if (!feedback) {
        feedback = document.createElement('div');
        feedback.id = FEEDBACK_ID;
        feedback.setAttribute('role', 'status');
        document.body.append(feedback);
    }

    return feedback;
};

const createElement = t => {
    ensureStyle();
    const element = document.createElement('div');
    element.id = BANNER_ID;
    element.setAttribute('role', 'region');
    element.setAttribute('aria-label', t('banner.label'));
    element.setAttribute('data-sel-role', 'security-updates-banner');
    element.setAttribute('data-position', 'bottom');

    const text = document.createElement('p');
    // The region is not live; this hidden status line announces the banner, after it is in the page, when what it
    // counts has changed.
    const live = document.createElement('span');
    live.setAttribute('role', 'status');
    live.className = 'security-updates-banner-sr';

    const link = document.createElement('a');
    link.href = adminUrl(ROUTE_KEY);
    link.textContent = t('banner.link');
    link.setAttribute('data-sel-role', 'security-updates-banner-link');
    element.append(text, link, live);

    // First in the page, so that keyboard users reach it without going through the whole interface.
    document.body.prepend(element);
    feedbackElement().textContent = '';
    listen(true);
    return element;
};

const hiddenText = (t, {policy, hours}, language) => (policy === 'HOURS' ?
    t(`banner.hidden.HOURS.${pluralKey(hours, language)}`, values({n: hours})) :
    t(`banner.hidden.${policy}`));

/**
 * Hides the banner for the policy, says so politely, and gives the focus back to where it was before it went into the
 * banner. When there is no such element (the banner is the first stop of the page), the focus stays on the document:
 * the next Tab goes to what followed the banner. Jahia's own elements are not made focusable for this.
 */
const hide = element => {
    const {t, language, dismissal} = dismissals.get(element);
    recordDismissal({...dismissal, now: dismissal.clock()});
    const returnTo = lastFocusOutside;
    removeBanner();
    if (returnTo?.isConnected && typeof returnTo.focus === 'function') {
        returnTo.focus();
    }

    const feedback = feedbackElement();
    feedback.textContent = '';
    setTimeout(() => {
        feedback.textContent = hiddenText(t, dismissal, language);
    }, FEEDBACK_DELAY_MS);
};

// The button is created once and updated in place, so that a refresh never takes the keyboard focus away from it.
const renderDismiss = (t, element, dismissal, language) => {
    dismissals.set(element, {t, language, dismissal});
    let dismiss = element.querySelector(DISMISS_SELECTOR);
    if (dismissal.policy === 'NEVER') {
        dismiss?.remove();
        return;
    }

    if (!dismiss) {
        dismiss = document.createElement('button');
        dismiss.type = 'button';
        dismiss.setAttribute('data-sel-role', 'security-updates-banner-dismiss');
        dismiss.addEventListener('click', () => hide(element));
        element.append(dismiss);
    }

    const label = t('banner.dismiss');
    if (dismiss.textContent !== label) {
        dismiss.textContent = label;
    }
};

// Kept in memory too, for browsers whose session storage cannot be used.
let announcedInMemory = null;

const readAnnounced = session => {
    try {
        if (session) {
            return session.getItem(ANNOUNCED_KEY);
        }
    } catch {
        // Unusable storage: the memory of this page decides.
    }

    return announcedInMemory;
};

const writeAnnounced = (session, value) => {
    announcedInMemory = value;
    try {
        session?.setItem(ANNOUNCED_KEY, value);
    } catch (error) {
        debug('announcement not remembered', error);
    }
};

// Announced when the count or the dismissal keys differ from the last announcement in this tab, not each time the
// banner comes back (after the Security updates page, or a new page of the Jahia UI).
const announce = (element, text, banner, dismissal) => {
    const signature = `${Number(banner.count) || 0}|${[...dismissal.keys].sort().join(',')}`;
    if (readAnnounced(dismissal.session) === signature) {
        return;
    }

    writeAnnounced(dismissal.session, signature);
    const live = element.querySelector('[role="status"]');
    live.textContent = '';
    setTimeout(() => {
        live.textContent = text;
    }, ANNOUNCE_DELAY_MS);
};

/** Creates the banner, or updates the one shown (keeping the focus where it is). */
const render = (t, language, banner, dismissal) => {
    const element = document.getElementById(BANNER_ID) || createElement(t);
    if (language) {
        element.setAttribute('lang', language);
    }

    element.setAttribute('data-count', String(banner.count));
    applyColors(element, banner);
    const text = message(t, banner, language);
    const paragraph = element.querySelector('p');
    if (paragraph.textContent !== text) {
        paragraph.textContent = text;
    }

    renderDismiss(t, element, dismissal, language);
    updateScrollPadding(element);
    announce(element, text, banner, dismissal);
};

const userKeyOf = async (hash, username) => {
    try {
        return await hash(username || '');
    } catch (error) {
        debug('user name not hashed', error);
        return null;
    }
};

let generation = 0;

/**
 * Shows, updates or removes the banner shown to users with adminTemplates, as decided by the server
 * (Query.securityUpdates.banner), with the configured colours and dismissal policy. Never throws: on any error it
 * shows nothing.
 *
 * @param {object} options translation function, UI language (or a function giving it), whether the Security updates page is open, and (for
 *        tests) the fetch implementation, clock, storages and user name hash
 * @returns {Promise<boolean>} whether the banner is shown
 */
export const showSecurityBanner = async ({
    t,
    language,
    isPageOpen = false,
    fetchImpl = window.fetch && window.fetch.bind(window),
    now = Date.now,
    session = storage('sessionStorage'),
    local = storage('localStorage'),
    hash = hashUser,
    onFetched = () => undefined
}) => {
    generation += 1;
    const current = generation;
    try {
        if (!fetchImpl || isPageOpen || isOnPage()) {
            removeBanner();
            return false;
        }

        const banner = await fetchBanner(fetchImpl);
        onFetched();
        const keys = Array.isArray(banner?.dismissalKeys) && banner.dismissalKeys.length > 0 ? banner.dismissalKeys : banner?.components;
        const dismissal = banner && {
            policy: banner.dismissPolicy || 'SESSION',
            hours: banner.dismissHours || 24,
            keys: keys || [],
            userKey: await userKeyOf(hash, banner.username),
            session,
            local,
            clock: now
        };
        if (current !== generation) {
            // A newer request is under way: it decides.
            return false;
        }

        if (!banner || !banner.visible || isDismissed({...dismissal, now: now()})) {
            removeBanner();
            return false;
        }

        render(t, typeof language === 'function' ? language() : language, banner, dismissal);
        return true;
    } catch (error) {
        debug('banner not shown', error);
        if (current === generation) {
            removeBanner();
        }

        return false;
    }
};

/**
 * Shows the banner and keeps it current: asks the server again after a check or a settings change (CHANGED_EVENT),
 * every REFRESH_INTERVAL_MS and when the browser tab is shown again (unless its last answer is less than
 * VISIBILITY_MIN_INTERVAL_MS old); hides it while the Security updates page is open (PAGE_EVENT).
 *
 * @param {object} options as for showSecurityBanner
 * @returns {function} stops the updates and removes the banner
 */
export const startSecurityBanner = options => {
    const clock = options.now || Date.now;
    let isPageOpen = false;
    let fetchedAt = null;
    const onFetched = () => {
        fetchedAt = clock();
    };

    const refresh = () => showSecurityBanner({...options, isPageOpen, onFetched});
    const onPage = event => {
        isPageOpen = Boolean(event?.detail?.isOpen);
        if (isPageOpen) {
            generation += 1;
            removeBanner();
        } else {
            refresh();
        }
    };

    const onVisibility = () => {
        const isRecent = fetchedAt !== null && clock() - fetchedAt < VISIBILITY_MIN_INTERVAL_MS;
        if (document.visibilityState !== 'hidden' && !isRecent) {
            refresh();
        }
    };

    window.addEventListener(CHANGED_EVENT, refresh);
    window.addEventListener(PAGE_EVENT, onPage);
    document.addEventListener('visibilitychange', onVisibility);
    const timer = setInterval(refresh, REFRESH_INTERVAL_MS);
    refresh();

    return () => {
        window.removeEventListener(CHANGED_EVENT, refresh);
        window.removeEventListener(PAGE_EVENT, onPage);
        document.removeEventListener('visibilitychange', onVisibility);
        clearInterval(timer);
        generation += 1;
        removeBanner();
    };
};
