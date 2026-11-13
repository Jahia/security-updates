import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import registerRoute from './SecurityUpdates/register';
import {startSecurityBanner} from './Banner/securityBanner';
import {NS} from './common/constants';

export default function () {
    registry.add('callback', NS, {
        targets: ['jahiaApp-init:60'],
        callback: async () => {
            await i18next.loadNamespaces(NS);
            registerRoute();
            // The banner is best effort (any failure leaves the UI as it was) and lives as long as the Jahia UI: it
            // is never stopped.
            startSecurityBanner({t: i18next.getFixedT(null, NS), language: () => i18next.language});
        }
    });
}
