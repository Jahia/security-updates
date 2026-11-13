import {isDismissed, localKey, recordDismissal, SESSION_KEY} from '../bannerDismissal';

const HOUR = 3600 * 1000;

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
    key: 'jahia-core|security-updates',
    username: 'root',
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

    describe('SESSION', () => {
        test('dismissed for the same components in the session storage', () => {
            const ctx = context();
            recordDismissal(ctx);
            expect(ctx.session.data[SESSION_KEY]).toBe('jahia-core|security-updates');
            expect(isDismissed(ctx)).toBe(true);
        });

        test('back when the components change', () => {
            const ctx = context();
            recordDismissal(ctx);
            expect(isDismissed({...ctx, key: 'jahia-core'})).toBe(false);
        });
    });

    describe('UNTIL_CHANGE', () => {
        test('stored per user in the local storage, survives a new session', () => {
            const ctx = context({policy: 'UNTIL_CHANGE'});
            recordDismissal(ctx);

            expect(JSON.parse(ctx.local.data[localKey('root')]).key).toBe('jahia-core|security-updates');
            expect(isDismissed({...ctx, session: fakeStorage(), now: ctx.now + (1000 * HOUR)})).toBe(true);
        });

        test('back when the components change', () => {
            const ctx = context({policy: 'UNTIL_CHANGE'});
            recordDismissal(ctx);
            expect(isDismissed({...ctx, key: 'jahia-core'})).toBe(false);
        });

        test('another administrator in the same browser still gets the banner', () => {
            const ctx = context({policy: 'UNTIL_CHANGE'});
            recordDismissal(ctx);
            expect(isDismissed({...ctx, username: 'admin2'})).toBe(false);
        });
    });

    describe('HOURS', () => {
        test('hidden during the duration, back once it has elapsed', () => {
            const ctx = context({policy: 'HOURS', hours: 12});
            recordDismissal(ctx);

            expect(JSON.parse(ctx.local.data[localKey('root')]).at).toBe(ctx.now);
            expect(isDismissed({...ctx, now: ctx.now + (11 * HOUR)})).toBe(true);
            expect(isDismissed({...ctx, now: ctx.now + (12 * HOUR)})).toBe(false);
        });

        test('back when the components change, even within the duration', () => {
            const ctx = context({policy: 'HOURS'});
            recordDismissal(ctx);
            expect(isDismissed({...ctx, key: 'other'})).toBe(false);
        });

        test('a dismissal time in the future does not hide the banner for ever', () => {
            const ctx = context({policy: 'HOURS'});
            ctx.local.setItem(localKey('root'), JSON.stringify({key: ctx.key, at: ctx.now + (1000 * HOUR)}));
            expect(isDismissed(ctx)).toBe(false);
        });
    });

    describe('NEVER', () => {
        test('cannot be dismissed, even with something stored', () => {
            const ctx = context({policy: 'NEVER'});
            ctx.session.setItem(SESSION_KEY, ctx.key);
            ctx.local.setItem(localKey('root'), JSON.stringify({key: ctx.key, at: ctx.now}));

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

        test.each(['not json', '{"key": 3}', '{"at": 1}', 'null', '[]'])('a corrupt record (%s) shows the banner', value => {
            const ctx = context({policy: 'UNTIL_CHANGE'});
            ctx.local.setItem(localKey('root'), value);
            expect(isDismissed(ctx)).toBe(false);
        });
    });

    test('the local key names the user, so that it can be found and edited', () => {
        expect(localKey('jdoe')).toBe('security-updates-banner-dismissed:jdoe');
        expect(localKey('')).toBe('security-updates-banner-dismissed:');
    });
});
