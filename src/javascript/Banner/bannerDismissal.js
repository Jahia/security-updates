// How long a hidden banner stays hidden (setting bannerDismissPolicy). Pure: the storages, the clock and the user key
// are passed in. Any storage problem fails open: the banner is shown.
//
// The identity of what was hidden is the set of dismissal keys sent by the server (one "<component>|<advisory>" per
// counted finding): the banner comes back as soon as a key is not in the stored set (a new advisory, even for a
// component already listed, or a new component), and stays hidden when keys only disappear (a fix).
//
// - SESSION:      sessionStorage[SESSION_KEY] = JSON array of dismissal tokens (sessionStorage is per browser tab)
// - HOURS:        localStorage[localKey(userKey)] = {"keys": [dismissal tokens], "at": epoch ms}; hidden for `hours` hours
// - UNTIL_CHANGE: localStorage[localKey(userKey)] = {"keys": [dismissal tokens], "at": epoch ms}
// - NEVER:        never hidden, nothing stored
// The browser stores only the opaque tokens the server sends (banner.dismissalTokens, banner.userToken): a keyed hash
// under a secret of the server, so that the storage can be read back into neither the vulnerable components nor the
// user name, even with a list of candidates (SECUPD-SEC-002). A plain hash of the key, as an older version stored,
// could be. Values stored by an older version never match a token, so they show the banner again (fail open).

export const SESSION_KEY = 'security-updates-banner-dismissed';

const HOUR_MS = 3600 * 1000;

export const localKey = userKey => `${SESSION_KEY}:${userKey || ''}`;

const isKeyList = value => Array.isArray(value) && value.every(key => typeof key === 'string');

const readSession = session => {
    const keys = JSON.parse(session.getItem(SESSION_KEY));
    return isKeyList(keys) ? {keys} : null;
};

const readLocal = (local, userKey) => {
    const record = JSON.parse(local.getItem(localKey(userKey)));
    const isValid = record && typeof record === 'object' && isKeyList(record.keys) && typeof record.at === 'number';
    return isValid ? record : null;
};

const read = ({policy, userKey, session, local}) => {
    if (policy === 'SESSION') {
        return session ? readSession(session) : null;
    }

    if (policy === 'HOURS' || policy === 'UNTIL_CHANGE') {
        return local ? readLocal(local, userKey) : null;
    }

    return null;
};

/**
 * @param {{policy: string, hours: number, keys: string[], userKey: string, now: number, session: Storage, local: Storage}} ctx
 * @returns {boolean} true when every current key was hidden and the dismissal still holds
 */
export const isDismissed = ctx => {
    try {
        const record = read(ctx);
        if (!record || !isKeyList(ctx.keys) || ctx.keys.length === 0) {
            return false;
        }

        const stored = new Set(record.keys);
        if (!ctx.keys.every(key => stored.has(key))) {
            return false;
        }

        if (ctx.policy !== 'HOURS') {
            return true;
        }

        const elapsed = ctx.now - record.at;
        return elapsed >= 0 && elapsed < ctx.hours * HOUR_MS;
    } catch {
        return false;
    }
};

/** Records a dismissal for the policy; a storage failure is ignored (the banner will simply come back). */
export const recordDismissal = ({policy, keys, userKey, now, session, local}) => {
    try {
        if (policy === 'SESSION') {
            session.setItem(SESSION_KEY, JSON.stringify(keys));
        } else if (policy === 'HOURS' || policy === 'UNTIL_CHANGE') {
            local.setItem(localKey(userKey), JSON.stringify({keys, at: now}));
        }
    } catch {
        // Fail open: nothing is remembered.
    }
};
