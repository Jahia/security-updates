// Values shared by the page, the settings form and the banner.

/** The i18n namespace of the module (locales in src/main/resources/javascript/locales). */
export const NS = 'security-updates';

/** Severities, most severe first (order of the findings table). */
export const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'];

/** Severities a banner threshold can take, lowest first (order of the settings select). */
export const THRESHOLD_SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** Banner dismissal policies (setting bannerDismissPolicy). */
export const DISMISS_POLICIES = ['SESSION', 'HOURS', 'UNTIL_CHANGE', 'NEVER'];

/** Default banner colours (same as the server defaults). */
export const DEFAULT_BANNER_BACKGROUND = '#1B1B1B';
export const DEFAULT_BANNER_TEXT = '#FFFFFF';

/** Bounds of the hour settings (refreshIntervalHours, bannerDismissHours), as validated by the server. */
export const MIN_HOURS = 1;
export const MAX_HOURS = 720;

/** Route key of the administration page. */
export const ROUTE_KEY = 'securityUpdates';

/**
 * Window events between the page and the banner: CHANGED_EVENT after a check or a settings change (the banner asks
 * the server again), PAGE_EVENT when the page opens or closes (detail.isOpen; the banner is hidden on the page).
 */
export const CHANGED_EVENT = 'security-updates:changed';
export const PAGE_EVENT = 'security-updates:page';

/** "Upgrading Jahia" in the Jahia Customer Center (Academy), linked from the jahia-core findings. */
export const JAHIA_UPGRADE_URL = 'https://academy.jahia.com/customer-center/jahia/howtoupgrade';
