import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import registerRoute from './SecurityUpdates/register';
import {startSecurityBanner} from './Banner/securityBanner';
import {NS} from './common/constants';

// Each step runs on its own: texts that cannot be loaded, or a banner that cannot start, leave the other steps alone.
const attempt = async (step, action) => {
    try {
        await action();
    } catch (error) {
        console.warn(`[security-updates] ${step} failed`, error);
    }
};

export default function () {
    registry.add('callback', NS, {
        targets: ['jahiaApp-init:60'],
        callback: async () => {
            await attempt('loading the texts', () => i18next.loadNamespaces(NS));
            await attempt('registering the page', registerRoute);
            // The banner is best effort (any failure leaves the UI as it was) and lives as long as the Jahia UI: it
            // is never stopped.
            await attempt('starting the banner', () => {
                startSecurityBanner({t: i18next.getFixedT(null, NS), language: () => i18next.language});
            });
        }
    });
}
