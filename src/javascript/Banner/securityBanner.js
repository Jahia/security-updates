import {adminUrl, graphqlUrl} from '../SecurityUpdates/securityUpdatesUtils';
import {normalizeHex} from '../Settings/colorUtils';
import {isDismissed, recordDismissal, SESSION_KEY} from './bannerDismissal';

export const BANNER_ID = 'security-updates-banner';
export const DISMISS_KEY = SESSION_KEY;
const STYLE_ID = 'security-updates-banner-style';
const DEFAULT_BACKGROUND = '#1B1B1B';
const DEFAULT_TEXT = '#FFFFFF';

const PERMISSION_QUERY = 'query SecurityUpdatesBannerPermission { jcr { nodeByPath(path: "/") { hasPermission(permissionName: "adminTemplates") } } }';
// The server decides (settings banner*) and validates the presentation: the client only renders.
const BANNER_QUERY = 'query SecurityUpdatesBanner { securityUpdates { banner { visible count minimumSeverity components ' +
    'backgroundColor textColor dismissPolicy dismissHours username } } }';
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

// The colours come from the settings, validated server-side to a contrast of at least 7:1 (WCAG AAA). The text, the
// link, the button border and the focus ring all use the text colour, so each of them keeps that contrast against
// the background (the focus ring needs 3:1). The banner never relies on colour alone: the message says what it is.
const CSS = `
#${BANNER_ID} { position: fixed; right: 16px; bottom: 16px; z-index: 9999; max-width: 420px; display: flex;
  flex-wrap: wrap; gap: 8px 16px; align-items: center; padding: 12px 16px; border-radius: 4px;
  background: var(--security-updates-banner-bg, ${DEFAULT_BACKGROUND}); color: var(--security-updates-banner-fg, ${DEFAULT_TEXT});
  font: 14px/1.5 'Nunito Sans', sans-serif; box-shadow: 0 4px 16px rgba(0, 0, 0, .35); }
#${BANNER_ID} p { margin: 0; flex: 1 1 100%; }
#${BANNER_ID} a { color: var(--security-updates-banner-fg, ${DEFAULT_TEXT}); font-weight: 700; text-decoration: underline; }
#${BANNER_ID} button { background: transparent; color: var(--security-updates-banner-fg, ${DEFAULT_TEXT});
  border: 1px solid var(--security-updates-banner-fg, ${DEFAULT_TEXT}); border-radius: 4px;
  padding: 4px 12px; font: inherit; cursor: pointer; min-height: 32px; }
#${BANNER_ID} a:focus-visible, #${BANNER_ID} button:focus-visible {
  outline: 3px solid var(--security-updates-banner-fg, ${DEFAULT_TEXT}); outline-offset: 2px; }
`;

const postGraphql = async (fetchImpl, query) => {
    const response = await fetchImpl(graphqlUrl(), {
        method: 'POST',
        credentials: 'same-origin',
        headers: {'Content-Type': 'application/json', Accept: 'application/json'},
        body: JSON.stringify({query})
    });
    if (!response.ok) {
        throw new Error('GraphQL request failed');
    }

    const payload = await response.json();
    if (!payload || payload.errors || !payload.data) {
        throw new Error('GraphQL error');
    }

    return payload.data;
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
    const valid = background && text;
    element.style.setProperty('--security-updates-banner-bg', valid ? background : DEFAULT_BACKGROUND);
    element.style.setProperty('--security-updates-banner-fg', valid ? text : DEFAULT_TEXT);
};

const render = (t, banner, dismissal) => {
    ensureStyle();
    const element = document.createElement('div');
    element.id = BANNER_ID;
    element.setAttribute('role', 'region');
    element.setAttribute('aria-label', t('banner.label'));
    element.setAttribute('data-sel-role', 'security-updates-banner');
    element.setAttribute('data-count', String(banner.count));
    applyColors(element, banner);

    const message = document.createElement('p');
    const severity = SEVERITIES.includes(banner.minimumSeverity) ? banner.minimumSeverity : 'HIGH';
    // TextContent never interprets markup, so i18next's HTML escaping is not wanted here
    message.textContent = t('banner.message', {
        count: banner.count,
        threshold: t(`banner.threshold.${severity}`),
        interpolation: {escapeValue: false}
    });

    const link = document.createElement('a');
    link.href = adminUrl('securityUpdates');
    link.textContent = t('banner.link');
    link.setAttribute('data-sel-role', 'security-updates-banner-link');
    element.append(message, link);

    if (dismissal.policy !== 'NEVER') {
        const dismiss = document.createElement('button');
        dismiss.type = 'button';
        dismiss.textContent = t('banner.dismiss');
        dismiss.setAttribute('data-sel-role', 'security-updates-banner-dismiss');
        const close = () => {
            recordDismissal({...dismissal, now: dismissal.clock()});
            element.remove();
        };

        dismiss.addEventListener('click', close);
        element.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                close();
            }
        });
        element.append(dismiss);
    }

    document.body.appendChild(element);
};

/**
 * Shows a notice to users with adminTemplates when the server says so (Query.securityUpdates.banner), with the
 * configured colours and dismissal policy. Never throws: on any error it shows nothing.
 *
 * @param {{t: function, fetchImpl?: function, now?: function, session?: Storage, local?: Storage}} options
 *        translation function, fetch implementation, clock and storages (injectable for tests)
 * @returns {Promise<boolean>} whether the notice was shown
 */
export const showSecurityBanner = async ({
    t,
    fetchImpl = window.fetch && window.fetch.bind(window),
    now = Date.now,
    session = storage('sessionStorage'),
    local = storage('localStorage')
}) => {
    try {
        if (!fetchImpl || document.getElementById(BANNER_ID)) {
            return false;
        }

        const permission = await postGraphql(fetchImpl, PERMISSION_QUERY);
        if (!permission.jcr?.nodeByPath?.hasPermission) {
            return false;
        }

        const data = await postGraphql(fetchImpl, BANNER_QUERY);
        const banner = data.securityUpdates.banner;
        const dismissal = {
            policy: banner.dismissPolicy || 'SESSION',
            hours: banner.dismissHours || 24,
            key: (banner.components || []).join('|'),
            username: banner.username || '',
            session,
            local,
            clock: now
        };
        if (!banner.visible || isDismissed({...dismissal, now: now()}) || document.getElementById(BANNER_ID)) {
            return false;
        }

        render(t, banner, dismissal);
        return true;
    } catch {
        return false;
    }
};
