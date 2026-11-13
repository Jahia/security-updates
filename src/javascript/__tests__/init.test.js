import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import registerRoute from '../SecurityUpdates/register';
import {startSecurityBanner} from '../Banner/securityBanner';
import init from '../init';

jest.mock('@jahia/ui-extender', () => ({registry: {add: jest.fn()}}));
jest.mock('i18next', () => ({loadNamespaces: jest.fn(), getFixedT: jest.fn(() => key => key), language: 'fr'}));
jest.mock('../SecurityUpdates/register', () => jest.fn());
jest.mock('../Banner/securityBanner', () => ({startSecurityBanner: jest.fn()}));

const callback = () => {
    init();
    return registry.add.mock.calls[0][2].callback;
};

describe('init', () => {
    let warn;

    beforeEach(() => {
        jest.clearAllMocks();
        warn = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    });

    afterEach(() => {
        warn.mockRestore();
    });

    test('loads the texts, registers the page and starts the banner, at jahiaApp-init:60', async () => {
        i18next.loadNamespaces.mockResolvedValue(undefined);
        init();
        expect(registry.add.mock.calls[0][0]).toBe('callback');
        expect(registry.add.mock.calls[0][2].targets).toEqual(['jahiaApp-init:60']);

        await registry.add.mock.calls[0][2].callback();

        expect(i18next.loadNamespaces).toHaveBeenCalledWith('security-updates');
        expect(registerRoute).toHaveBeenCalledTimes(1);
        expect(startSecurityBanner).toHaveBeenCalledTimes(1);
        expect(startSecurityBanner.mock.calls[0][0].language()).toBe('fr');
        expect(warn).not.toHaveBeenCalled();
    });

    test('texts that cannot be loaded do not stop the page or the banner', async () => {
        i18next.loadNamespaces.mockRejectedValue(new Error('404'));

        await callback()();

        expect(registerRoute).toHaveBeenCalledTimes(1);
        expect(startSecurityBanner).toHaveBeenCalledTimes(1);
        expect(warn).toHaveBeenCalledWith('[security-updates] loading the texts failed', expect.any(Error));
    });

    test('a page that cannot be registered does not stop the banner, and a failing banner is only reported', async () => {
        i18next.loadNamespaces.mockResolvedValue(undefined);
        registerRoute.mockImplementation(() => {
            throw new Error('registry');
        });
        startSecurityBanner.mockImplementation(() => {
            throw new Error('banner');
        });

        await expect(callback()()).resolves.toBeUndefined();

        expect(startSecurityBanner).toHaveBeenCalledTimes(1);
        expect(warn).toHaveBeenCalledTimes(2);
    });
});
