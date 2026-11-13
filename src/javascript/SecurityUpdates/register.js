import {registry} from '@jahia/ui-extender';
import React from 'react';
import {SecurityUpdates} from './SecurityUpdates';

export const ROUTE_KEY = 'securityUpdates';

export default () => {
    registry.add('adminRoute', ROUTE_KEY, {
        targets: ['administration-server-systemComponents:20'],
        requiredPermission: 'adminTemplates',
        label: 'security-updates:label',
        isSelectable: true,
        render: () => React.createElement(SecurityUpdates)
    });
};
