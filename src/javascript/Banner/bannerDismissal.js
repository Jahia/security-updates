// How long a dismissed notice stays hidden (setting bannerDismissPolicy). Pure: the storages and the clock are
// passed in. Any storage problem fails open: the notice is shown.
//
// - SESSION:      sessionStorage[SESSION_KEY] = components key
// - HOURS:        localStorage[localKey(user)] = {"key": components key, "at": epoch ms}; hidden for `hours` hours
// - UNTIL_CHANGE: localStorage[localKey(user)] = {"key": components key, "at": epoch ms}
// - NEVER:        never hidden, nothing stored
// Except with NEVER, the notice comes back as soon as the components key changes.

export const SESSION_KEY = 'security-updates-banner-dismissed';

const HOUR_MS = 3600 * 1000;

export const localKey = username => `${SESSION_KEY}:${username || ''}`;

const readLocal = (local, username) => {
    const raw = local.getItem(localKey(username));
    if (!raw) {
        return null;
    }

    const record = JSON.parse(raw);
    const valid = record && typeof record === 'object' && typeof record.key === 'string' && typeof record.at === 'number';
    return valid ? record : null;
};

/**
 * @param {{policy: string, hours: number, key: string, username: string, now: number, session: Storage, local: Storage}} ctx
 * @returns {boolean} true when the notice was dismissed for these components and the dismissal still holds
 */
export const isDismissed = ({policy, hours, key, username, now, session, local}) => {
    try {
        if (policy === 'SESSION') {
            return Boolean(session) && session.getItem(SESSION_KEY) === key;
        }

        if (policy !== 'HOURS' && policy !== 'UNTIL_CHANGE') {
            return false;
        }

        const record = local ? readLocal(local, username) : null;
        if (!record || record.key !== key) {
            return false;
        }

        if (policy === 'UNTIL_CHANGE') {
            return true;
        }

        const elapsed = now - record.at;
        return elapsed >= 0 && elapsed < hours * HOUR_MS;
    } catch {
        return false;
    }
};

/** Records a dismissal for the policy; a storage failure is ignored (the notice will simply come back). */
export const recordDismissal = ({policy, key, username, now, session, local}) => {
    try {
        if (policy === 'SESSION') {
            session.setItem(SESSION_KEY, key);
        } else if (policy === 'HOURS' || policy === 'UNTIL_CHANGE') {
            local.setItem(localKey(username), JSON.stringify({key, at: now}));
        }
    } catch {
        // Fail open: nothing is remembered.
    }
};
