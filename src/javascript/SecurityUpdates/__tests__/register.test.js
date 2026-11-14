import {registry} from '@jahia/ui-extender';
import registerRoute, {ROUTE_KEY} from '../register';
import {SecurityUpdates} from '../SecurityUpdates';

jest.mock('@jahia/ui-extender', () => ({registry: {add: jest.fn()}}));
jest.mock('../SecurityUpdates', () => ({SecurityUpdates: () => null}));

describe('register', () => {
    test('adds the admin route under System components, for users with adminTemplates', () => {
        registerRoute();

        expect(registry.add).toHaveBeenCalledTimes(1);
        const [type, key, route] = registry.add.mock.calls[0];
        expect(type).toBe('adminRoute');
        expect(key).toBe(ROUTE_KEY);
        expect(ROUTE_KEY).toBe('securityUpdates');
        expect(route).toEqual(expect.objectContaining({
            targets: ['administration-server-systemComponents:20'],
            requiredPermission: 'adminTemplates',
            label: 'security-updates:label',
            isSelectable: true
        }));
        expect(route.render().type).toBe(SecurityUpdates);
    });
});
