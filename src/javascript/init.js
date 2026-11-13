import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import registerRoute from './SecurityUpdates/register';
import {showSecurityBanner} from './Banner/securityBanner';

export const NAMESPACE = 'security-updates';

export default function () {
    registry.add('callback', NAMESPACE, {
        targets: ['jahiaApp-init:60'],
        callback: async () => {
            await i18next.loadNamespaces(NAMESPACE);
            registerRoute();
            // The banner is best effort: any failure leaves the UI as it was.
            showSecurityBanner({t: i18next.getFixedT(null, NAMESPACE)}).catch(() => undefined);
        }
    });
}
