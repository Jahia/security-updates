import {adminUrl, graphqlUrl} from '../SecurityUpdates/securityUpdatesUtils';

export const BANNER_ID = 'security-updates-banner';
export const DISMISS_KEY = 'security-updates-banner-dismissed';
const STYLE_ID = 'security-updates-banner-style';

const PERMISSION_QUERY = 'query SecurityUpdatesBannerPermission { jcr { nodeByPath(path: "/") { hasPermission(permissionName: "adminTemplates") } } }';
// The server decides (settings bannerEnabled, bannerMinimumSeverity, bannerIncludeOwnerReports): the client only renders.
const BANNER_QUERY = 'query SecurityUpdatesBanner { securityUpdates { banner { visible count minimumSeverity components } } }';
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

// Colours: white on #1b1b1b is 17:1; the focus ring is #ffd400 on #1b1b1b (12:1). The banner never relies on
// colour alone: the message states what it is about.
const CSS = `
#${BANNER_ID} { position: fixed; right: 16px; bottom: 16px; z-index: 9999; max-width: 420px; display: flex;
  flex-wrap: wrap; gap: 8px 16px; align-items: center; padding: 12px 16px; border-radius: 4px;
  background: #1b1b1b; color: #fff; font: 14px/1.5 'Nunito Sans', sans-serif;
  box-shadow: 0 4px 16px rgba(0, 0, 0, .35); }
#${BANNER_ID} p { margin: 0; flex: 1 1 100%; }
#${BANNER_ID} a { color: #fff; font-weight: 700; text-decoration: underline; }
#${BANNER_ID} button { background: transparent; color: #fff; border: 1px solid #fff; border-radius: 4px;
  padding: 4px 12px; font: inherit; cursor: pointer; min-height: 32px; }
#${BANNER_ID} a:focus-visible, #${BANNER_ID} button:focus-visible { outline: 3px solid #ffd400; outline-offset: 2px; }
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

const readDismissed = () => {
    try {
        return window.sessionStorage.getItem(DISMISS_KEY);
    } catch {
        return null;
    }
};

const writeDismissed = value => {
    try {
        window.sessionStorage.setItem(DISMISS_KEY, value);
    } catch {
        // Storage unavailable: the notice comes back on the next load, which is acceptable.
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

const render = (t, {count, minimumSeverity}, key) => {
    ensureStyle();
    const banner = document.createElement('div');
    banner.id = BANNER_ID;
    banner.setAttribute('role', 'region');
    banner.setAttribute('aria-label', t('banner.label'));
    banner.setAttribute('data-sel-role', 'security-updates-banner');
    banner.setAttribute('data-count', String(count));

    const message = document.createElement('p');
    const severity = SEVERITIES.includes(minimumSeverity) ? minimumSeverity : 'HIGH';
    // TextContent never interprets markup, so i18next's HTML escaping is not wanted here
    message.textContent = t('banner.message', {
        count,
        threshold: t(`banner.threshold.${severity}`),
        interpolation: {escapeValue: false}
    });

    const link = document.createElement('a');
    link.href = adminUrl('securityUpdates');
    link.textContent = t('banner.link');
    link.setAttribute('data-sel-role', 'security-updates-banner-link');

    const dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.textContent = t('banner.dismiss');
    dismiss.setAttribute('data-sel-role', 'security-updates-banner-dismiss');

    const close = () => {
        writeDismissed(key);
        banner.remove();
    };

    dismiss.addEventListener('click', close);
    banner.addEventListener('keydown', event => {
        if (event.key === 'Escape') {
            close();
        }
    });

    banner.append(message, link, dismiss);
    document.body.appendChild(banner);
};

/**
 * Shows a notice to users with adminTemplates when the server says so (Query.securityUpdates.banner).
 * Never throws: on any error it shows nothing.
 *
 * @param {{t: function, fetchImpl?: function}} options translation function and fetch implementation
 * @returns {Promise<boolean>} whether the notice was shown
 */
export const showSecurityBanner = async ({t, fetchImpl = window.fetch && window.fetch.bind(window)}) => {
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
        const key = (banner.components || []).join('|');
        if (!banner.visible || readDismissed() === key || document.getElementById(BANNER_ID)) {
            return false;
        }

        render(t, banner, key);
        return true;
    } catch {
        return false;
    }
};
