import {hashUser, isDismissed, localKey, recordDismissal, SESSION_KEY} from '../bannerDismissal';

const HOUR = 3600 * 1000;
const KEYS = ['jahia-core|JSA-2026-0001', 'security-updates|JSA-2026-0002'];

const fakeStorage = () => {
    const data = {};
    return {
        data,
        getItem: key => (key in data ? data[key] : null),
        setItem: (key, value) => {
            data[key] = String(value);
        }
    };
};

const brokenStorage = () => ({
    getItem: () => {
        throw new Error('SecurityError');
    },
    setItem: () => {
        throw new Error('QuotaExceededError');
    }
});

const context = (overrides = {}) => ({
    policy: 'SESSION',
    hours: 24,
    keys: KEYS,
    userKey: 'hash-of-root',
    now: 1_000_000_000_000,
    session: fakeStorage(),
    local: fakeStorage(),
    ...overrides
});

describe('banner dismissal', () => {
    test('nothing stored: not dismissed, whatever the policy', () => {
        ['SESSION', 'HOURS', 'UNTIL_CHANGE', 'NEVER'].forEach(policy => {
            expect(isDismissed(context({policy}))).toBe(false);
        });
    });

    describe.each(['SESSION', 'HOURS', 'UNTIL_CHANGE'])('%s', policy => {
        test('dismissed for the same keys', () => {
            const ctx = context({policy});
            recordDismissal(ctx);
            expect(isDismissed(ctx)).toBe(true);
        });

        test('a new advisory for an already dismissed component shows the banner again', () => {
            const ctx = context({policy});
            recordDismissal(ctx);
            expect(isDismissed({...ctx, keys: [...KEYS, 'jahia-core|JSA-2026-0099']})).toBe(false);
        });

        test('a new component shows the banner again', () => {
            const ctx = context({policy});
            recordDismissal(ctx);
            expect(isDismissed({...ctx, keys: [...KEYS, 'jcontent|JSA-2026-0100']})).toBe(false);
        });

        test('keys that only disappear (a fix) keep the banner hidden', () => {
            const ctx = context({policy});
            recordDismissal(ctx);
            expect(isDismissed({...ctx, keys: [KEYS[0]]})).toBe(true);
        });

        test('the order of the keys does not matter', () => {
            const ctx = context({policy});
            recordDismissal(ctx);
            expect(isDismissed({...ctx, keys: [...KEYS].reverse()})).toBe(true);
        });
    });

    describe('SESSION', () => {
        test('stored in the session storage of the tab, as the list of keys', () => {
            const ctx = context();
            recordDismissal(ctx);
            expect(JSON.parse(ctx.session.data[SESSION_KEY])).toEqual(KEYS);
            expect(isDismissed({...ctx, session: fakeStorage()})).toBe(false);
        });
    });

    describe('UNTIL_CHANGE', () => {
        test('stored per user in the local storage, survives a new session', () => {
            const ctx = context({policy: 'UNTIL_CHANGE'});
            recordDismissal(ctx);

            expect(JSON.parse(ctx.local.data[localKey('hash-of-root')]).keys).toEqual(KEYS);
            expect(isDismissed({...ctx, session: fakeStorage(), now: ctx.now + (1000 * HOUR)})).toBe(true);
        });

        test('another administrator in the same browser still gets the banner', () => {
            const ctx = context({policy: 'UNTIL_CHANGE'});
            recordDismissal(ctx);
            expect(isDismissed({...ctx, userKey: 'hash-of-admin2'})).toBe(false);
        });
    });

    describe('HOURS', () => {
        test('hidden during the duration, back once it has elapsed', () => {
            const ctx = context({policy: 'HOURS', hours: 12});
            recordDismissal(ctx);

            expect(JSON.parse(ctx.local.data[localKey('hash-of-root')]).at).toBe(ctx.now);
            expect(isDismissed({...ctx, now: ctx.now + (11 * HOUR)})).toBe(true);
            expect(isDismissed({...ctx, now: ctx.now + (12 * HOUR)})).toBe(false);
        });

        test('a dismissal time in the future does not hide the banner for ever', () => {
            const ctx = context({policy: 'HOURS'});
            ctx.local.setItem(localKey('hash-of-root'), JSON.stringify({keys: KEYS, at: ctx.now + (1000 * HOUR)}));
            expect(isDismissed(ctx)).toBe(false);
        });
    });

    describe('NEVER', () => {
        test('cannot be dismissed, even with something stored', () => {
            const ctx = context({policy: 'NEVER'});
            ctx.session.setItem(SESSION_KEY, JSON.stringify(KEYS));
            ctx.local.setItem(localKey('hash-of-root'), JSON.stringify({keys: KEYS, at: ctx.now}));

            recordDismissal(ctx);

            expect(isDismissed(ctx)).toBe(false);
        });
    });

    describe('fails open', () => {
        test.each(['SESSION', 'HOURS', 'UNTIL_CHANGE'])('%s: unreadable storage shows the banner, writing does not throw', policy => {
            const ctx = context({policy, session: brokenStorage(), local: brokenStorage()});
            expect(() => recordDismissal(ctx)).not.toThrow();
            expect(isDismissed(ctx)).toBe(false);
        });

        test('missing storage shows the banner', () => {
            expect(isDismissed(context({policy: 'HOURS', local: null}))).toBe(false);
            expect(isDismissed(context({policy: 'SESSION', session: undefined}))).toBe(false);
        });

        test.each(['not json', '{"keys": 3}', '{"keys": [1], "at": 1}', '{"at": 1}', 'null', '[]', '{"key": "a|b", "at": 1}'])(
            'a corrupt or old record (%s) shows the banner', value => {
                const ctx = context({policy: 'UNTIL_CHANGE'});
                ctx.local.setItem(localKey('hash-of-root'), value);
                expect(isDismissed(ctx)).toBe(false);
            });

        test.each(['not json', '"jahia-core|security-updates"', '[1]', '{}'])('a corrupt or old session value (%s) shows the banner', value => {
            const ctx = context();
            ctx.session.setItem(SESSION_KEY, value);
            expect(isDismissed(ctx)).toBe(false);
        });

        test('no key at all is never hidden', () => {
            const ctx = context({policy: 'UNTIL_CHANGE'});
            recordDismissal(ctx);
            expect(isDismissed({...ctx, keys: []})).toBe(false);
        });
    });

    describe('user key', () => {
        test('the local key carries a hash, not the user name', () => {
            expect(localKey('abc')).toBe('security-updates-banner-dismissed:abc');
        });

        test('hashUser gives the SHA-256 of the user name, in hexadecimal', async () => {
            const {webcrypto} = await import('crypto');
            const {TextEncoder} = await import('util');
            // Jsdom has no TextEncoder (browsers do).
            global.TextEncoder = global.TextEncoder || TextEncoder;
            // Node's SubtleCrypto, given the bytes as a Node buffer (jsdom's typed arrays come from another realm).
            const subtle = {digest: (algorithm, data) => webcrypto.subtle.digest(algorithm, Buffer.from(data))};
            expect(await hashUser('root', subtle)).toBe('4813494d137e1631bba301d5acab6e7bb7aa74ce1185d456565ef51d737677b2');
        });

        test('without SubtleCrypto (plain http), a non-reversible fallback still hides the name', async () => {
            const hashed = await hashUser('jdoe', undefined);
            expect(hashed).toMatch(/^[0-9a-f]{8}$/);
            expect(hashed).not.toContain('jdoe');
            expect(await hashUser('jdoe', undefined)).toBe(hashed);
            expect(await hashUser('jdoe2', undefined)).not.toBe(hashed);
        });

        test('a failing SubtleCrypto falls back too', async () => {
            const failing = {digest: () => Promise.reject(new Error('NotSupported'))};
            expect(await hashUser('jdoe', failing)).toMatch(/^[0-9a-f]{8}$/);
        });
    });
});
