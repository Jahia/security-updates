import {registry} from '@jahia/ui-extender';
import React from 'react';
import {SecurityUpdates} from './SecurityUpdates';
import {NS, ROUTE_KEY} from '../common/constants';

export {ROUTE_KEY};

export default () => {
    registry.add('adminRoute', ROUTE_KEY, {
        targets: ['administration-server-systemComponents:20'],
        requiredPermission: 'adminTemplates',
        label: `${NS}:label`,
        isSelectable: true,
        render: () => React.createElement(SecurityUpdates)
    });
};
