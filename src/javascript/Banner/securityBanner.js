import {adminUrl, graphqlUrl} from '../SecurityUpdates/securityUpdatesUtils';
import {normalizeHex} from '../Settings/colorUtils';
import {CHANGED_EVENT, DEFAULT_BANNER_BACKGROUND, DEFAULT_BANNER_TEXT, PAGE_EVENT, ROUTE_KEY, THRESHOLD_SEVERITIES} from '../common/constants';
import {pluralKey, values} from '../common/i18n';
import {hashKeys, hashUser, isDismissed, recordDismissal, SESSION_KEY} from './bannerDismissal';

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
/** A failed request is tried again after this delay; the banner shown, if any, stays meanwhile. */
export const RETRY_INTERVAL_MS = 60 * 1000;
/** A request that has not answered after this delay is abandoned (and counts as failed). */
export const REQUEST_TIMEOUT_MS = 20 * 1000;
/** After the banner moved to the other edge, it does not move again for the same focused element before this delay. */
export const FLIP_HOLD_MS = 2000;
/**
 * Stable test hook, read by the end-to-end tests: the attribute data-security-updates-banner on <html> is "pending"
 * while a banner request runs, and "ready" once the banner decision is applied (shown, hidden by the settings or a
 * dismissal, not authorised, page open, or request failed). It has no other use.
 */
export const READY_ATTRIBUTE = 'data-security-updates-banner';
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
// On a small viewport (a phone, or a 400% zoom: 40rem wide or high at most), the box becomes a compact strip across
// the edge, at most 40% of the height (scrolling inside beyond that), so that it never covers most of the screen, even
// when it cannot be hidden. It stays fixed rather than in the flow: the Jahia interface fills the viewport and scrolls
// inside its own containers, so an element after it in the body would be out of view.
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
@media (max-width: 40rem), (max-height: 40rem) {
  #${BANNER_ID} { left: 0; right: 0; bottom: 0; max-width: none; max-height: 40vh; overflow-y: auto; border-radius: 0;
    gap: 0.25rem 0.75rem; padding: 0.25rem 0.75rem; box-shadow: 0 -2px 8px rgba(0, 0, 0, .35); }
  #${BANNER_ID}[data-position="top"] { top: 0; bottom: auto; }
}
`;

const debug = (message, error) => {
    // The banner is best effort: a failure only leaves it out, with a trace for whoever looks at the console.
    console.debug(`[security-updates] ${message}`, error);
};

const setReady = state => {
    document.documentElement.setAttribute(READY_ATTRIBUTE, state);
};

// The request is abandoned after REQUEST_TIMEOUT_MS: a server that never answers does not leave it pending for ever.
const post = async fetchImpl => {
    const controller = typeof AbortController === 'function' ? new AbortController() : null;
    const timeout = controller ? setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS) : null;
    try {
        const response = await fetchImpl(graphqlUrl(), {
            method: 'POST',
            credentials: 'same-origin',
            headers: {'Content-Type': 'application/json', Accept: 'application/json'},
            body: JSON.stringify({query: BANNER_QUERY}),
            signal: controller?.signal
        });
        if (!response.ok) {
            throw new Error(`GraphQL request failed (HTTP ${response.status})`);
        }

        return await response.json();
    } finally {
        clearTimeout(timeout);
    }
};

const fetchBanner = async fetchImpl => {
    const payload = await post(fetchImpl);
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
// The element that had the focus when the banner appeared: the fallback when the focus went to the banner from
// outside the page (Shift+Tab from the browser's address bar, a click).
let focusAtInsert = null;
// The page's own scroll-padding values, restored when the banner goes, and the last values written (not written again).
let savedScrollPadding = null;
let writtenScrollPadding = null;
let frame = null;
// The last move to the other edge (time and focused element), and the check planned for the end of its hold.
let lastFlip = null;
let holdTimer = null;
// The pending texts of the live regions: the announcement of the banner, the feedback after Hide.
let announceTimer = null;
let feedbackTimer = null;

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
    const value = `calc(${Math.ceil(element.getBoundingClientRect().height)}px + 1rem)`;
    // Called on every scroll and resize frame: the document's style is only written when the value changes.
    if (writtenScrollPadding?.side === side && writtenScrollPadding.value === value) {
        return;
    }

    style.setProperty(`scroll-padding-${side}`, value);
    restoreScrollPadding(style, side === 'top' ? 'bottom' : 'top');
    writtenScrollPadding = {side, value};
};

const clearScrollPadding = () => {
    if (savedScrollPadding) {
        const style = document.documentElement.style;
        restoreScrollPadding(style, 'top');
        restoreScrollPadding(style, 'bottom');
        savedScrollPadding = null;
    }

    writtenScrollPadding = null;
};

// Where the banner would be at the other edge: the same box, mirrored across the middle of the viewport (it keeps the
// same distance to its edge).
const mirrored = rect => {
    const height = window.innerHeight || document.documentElement.clientHeight;
    return {left: rect.left, right: rect.right, top: height - rect.bottom, bottom: height - rect.top};
};

const scheduleCheckAfterHold = delay => {
    clearTimeout(holdTimer);
    holdTimer = setTimeout(scheduleCheck, delay);
};

/**
 * The banner is fixed over the page: when the focused element would be under it, it moves to the other edge, so that
 * it never hides the focus (WCAG 2.4.11 and 2.4.12). It moves only when the element is clear of the banner at the
 * other edge (a focused element taller than the space left, such as a scrolling region or a frame, would make it
 * move on every frame), and, for the same focused element, not again during FLIP_HOLD_MS (checked again at the end).
 * A newly focused element may always make it move.
 */
const keepFocusVisible = (target, isNewFocus) => {
    const element = document.getElementById(BANNER_ID);
    if (!element || !target || target === document.body || element.contains(target) || typeof target.getBoundingClientRect !== 'function') {
        return;
    }

    const targetRect = target.getBoundingClientRect();
    const bannerRect = element.getBoundingClientRect();
    if (!overlaps(targetRect, bannerRect) || overlaps(targetRect, mirrored(bannerRect))) {
        return;
    }

    const now = Date.now();
    const held = lastFlip && lastFlip.target === target && !isNewFocus ? FLIP_HOLD_MS - (now - lastFlip.at) : 0;
    if (held > 0) {
        scheduleCheckAfterHold(held);
        return;
    }

    element.setAttribute('data-position', element.getAttribute('data-position') === 'top' ? 'bottom' : 'top');
    lastFlip = {target, at: now};
    updateScrollPadding(element);
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
            keepFocusVisible(document.activeElement, false);
        }
    });
};

const onFocusIn = event => {
    const element = document.getElementById(BANNER_ID);
    if (element && !element.contains(event.target)) {
        lastFocusOutside = event.target;
    }

    keepFocusVisible(event.target, true);
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
    focusAtInsert = null;
    lastFlip = null;
    listen(false);
    if (frame !== null) {
        window.cancelAnimationFrame(frame);
        frame = null;
    }

    clearTimeout(holdTimer);
    clearTimeout(announceTimer);
    holdTimer = null;
    announceTimer = null;
    clearScrollPadding();
};

const clearFeedbackTimer = () => {
    clearTimeout(feedbackTimer);
    feedbackTimer = null;
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

    // Last in the page, where it is drawn (at an edge, after the content): the tab order follows the reading order
    // (WCAG 2.4.3). Its status line announces it, so users of screen readers learn of it wherever they are.
    const active = document.activeElement;
    focusAtInsert = active && active !== document.body ? active : null;
    clearFeedbackTimer();
    feedbackElement().textContent = '';
    document.body.append(element);
    listen(true);
    return element;
};

const hiddenText = (t, {policy, hours}, language) => (policy === 'HOURS' ?
    t(`banner.hidden.HOURS.${pluralKey(hours, language)}`, values({n: hours})) :
    t(`banner.hidden.${policy}`));

const canFocus = element => Boolean(element?.isConnected) && typeof element.focus === 'function';

/**
 * Hides the banner for the policy, says so politely, and gives the focus back to where it was before it went into the
 * banner, else to the element that had it when the banner appeared. When there is neither (the focus came from outside
 * the page), it stays on the document: the banner was the last stop, so the next Tab starts the page again. Jahia's
 * own elements are not made focusable for this, and the feedback line is not focused (it is visually hidden: the focus
 * would be invisible).
 */
const hide = element => {
    const {t, language, dismissal} = dismissals.get(element);
    recordDismissal({...dismissal, now: dismissal.clock()});
    const returnTo = [lastFocusOutside, focusAtInsert].find(canFocus);
    removeBanner();
    returnTo?.focus();
    setReady('ready');

    const feedback = feedbackElement();
    feedback.textContent = '';
    clearFeedbackTimer();
    feedbackTimer = setTimeout(() => {
        feedbackTimer = null;
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
    clearTimeout(announceTimer);
    announceTimer = setTimeout(() => {
        announceTimer = null;
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

// Keys that cannot be hashed are not stored nor compared: the banner cannot be hidden then (fail open).
const storedKeysOf = async (hash, keys) => {
    try {
        return await hashKeys(keys || [], hash);
    } catch (error) {
        debug('dismissal keys not hashed', error);
        return [];
    }
};

let generation = 0;

/**
 * Shows, updates or removes the banner shown to users with adminTemplates, as decided by the server
 * (Query.securityUpdates.banner), with the configured colours and dismissal policy. Never throws: a failed request
 * (network, timeout, HTTP or GraphQL error, malformed answer) keeps the banner as it was and calls onFailed.
 *
 * @param {object} options translation function, UI language (or a function giving it), whether the Security updates
 *        page is open, onFetched and onFailed callbacks, and (for tests) the fetch implementation, clock, storages and
 *        the hash of the user name and of the dismissal keys
 * @returns {Promise<boolean>} whether a new banner decision shows the banner
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
    onFetched = () => undefined,
    onFailed = () => undefined
}) => {
    generation += 1;
    const current = generation;
    try {
        if (!fetchImpl || isPageOpen || isOnPage()) {
            removeBanner();
            setReady('ready');
            return false;
        }

        setReady('pending');
        const banner = await fetchBanner(fetchImpl);
        onFetched();
        const keys = Array.isArray(banner?.dismissalKeys) && banner.dismissalKeys.length > 0 ? banner.dismissalKeys : banner?.components;
        const dismissal = banner && {
            policy: banner.dismissPolicy || 'SESSION',
            hours: banner.dismissHours || 24,
            keys: await storedKeysOf(hash, keys),
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
            setReady('ready');
            return false;
        }

        render(t, typeof language === 'function' ? language() : language, banner, dismissal);
        setReady('ready');
        return true;
    } catch (error) {
        // Most failures are transient (a restart, the network): the banner shown stays, and the request is tried again.
        debug('banner not updated', error);
        if (current === generation) {
            setReady('ready');
            onFailed(error);
        }

        return false;
    }
};

/**
 * Shows the banner and keeps it current: asks the server again after a check or a settings change (CHANGED_EVENT),
 * every REFRESH_INTERVAL_MS, RETRY_INTERVAL_MS after a failed request, and when the browser tab is shown again
 * (unless its last answer is less than VISIBILITY_MIN_INTERVAL_MS old); hides it while the Security updates page is
 * open (PAGE_EVENT).
 *
 * @param {object} options as for showSecurityBanner
 * @returns {function} stops the updates and removes the banner
 */
export const startSecurityBanner = options => {
    const clock = options.now || Date.now;
    let isPageOpen = false;
    let fetchedAt = null;
    let retry = null;
    const cancelRetry = () => {
        clearTimeout(retry);
        retry = null;
    };

    const onFetched = () => {
        fetchedAt = clock();
        cancelRetry();
    };

    let refresh = null;
    const onFailed = () => {
        cancelRetry();
        retry = setTimeout(refresh, RETRY_INTERVAL_MS);
    };

    refresh = () => showSecurityBanner({...options, isPageOpen, onFetched, onFailed});
    const onPage = event => {
        isPageOpen = Boolean(event?.detail?.isOpen);
        if (isPageOpen) {
            generation += 1;
            cancelRetry();
            removeBanner();
            setReady('ready');
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
        cancelRetry();
        generation += 1;
        removeBanner();
        clearFeedbackTimer();
        document.documentElement.removeAttribute(READY_ATTRIBUTE);
    };
};
