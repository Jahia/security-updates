// How long a hidden banner stays hidden (setting bannerDismissPolicy). Pure: the storages, the clock and the user key
// are passed in. Any storage problem fails open: the banner is shown.
//
// The identity of what was hidden is the set of dismissal keys sent by the server (one "<component>|<advisory>" per
// counted finding): the banner comes back as soon as a key is not in the stored set (a new advisory, even for a
// component already listed, or a new component), and stays hidden when keys only disappear (a fix).
//
// - SESSION:      sessionStorage[SESSION_KEY] = JSON array of hashed keys (sessionStorage is per browser tab)
// - HOURS:        localStorage[localKey(userKey)] = {"keys": [hashed keys], "at": epoch ms}; hidden for `hours` hours
// - UNTIL_CHANGE: localStorage[localKey(userKey)] = {"keys": [hashed keys], "at": epoch ms}
// - NEVER:        never hidden, nothing stored
// The user key is a hash of the user name, and each stored key is a hash of a dismissal key (hashKeys), so that the
// browser storage keeps neither user names nor the names of the vulnerable components in clear. This module compares
// the keys it is given: the caller hashes them first. Values stored in clear by an older version never match a hash,
// so they show the banner again (fail open).

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

// 32-bit FNV-1a, used only where SubtleCrypto is missing (it needs a secure context): it still keeps the user name
// out of the storage key.
const FNV_OFFSET = 0x811C9DC5;
const FNV_PRIME = 0x01000193;

const fnv1a = text => {
    let hash = FNV_OFFSET;
    for (const char of text) {
        hash ^= char.codePointAt(0);
        hash = Math.imul(hash, FNV_PRIME);
    }

    return (hash >>> 0).toString(16).padStart(8, '0');
};

/**
 * The hash of a text: its SHA-256 in hexadecimal, or a shorter non-cryptographic hash without SubtleCrypto.
 *
 * @param {string} value the text (a user name, a dismissal key)
 * @param {SubtleCrypto} [subtle] the SubtleCrypto to use (window.crypto.subtle by default)
 * @returns {Promise<string>} the hash
 */
export const hashText = async (value, subtle = window.crypto?.subtle) => {
    const text = String(value || '');
    if (subtle) {
        try {
            const digest = await subtle.digest('SHA-256', new TextEncoder().encode(text));
            return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
        } catch {
            // Falls back below.
        }
    }

    return fnv1a(text);
};

/** The user key of a user name (hashText). */
export const hashUser = hashText;

/**
 * The dismissal keys as stored: the hash of each key, in the same order.
 *
 * @param {string[]} keys the dismissal keys sent by the server
 * @param {function(string): Promise<string>} [hash] the hash function (hashText by default)
 * @returns {Promise<string[]>} the hashed keys
 */
export const hashKeys = (keys, hash = hashText) => Promise.all((keys || []).map(key => hash(key)));
