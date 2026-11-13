import PropTypes from 'prop-types';

export const advisoryShape = PropTypes.shape({
    id: PropTypes.string.isRequired,
    title: PropTypes.string,
    url: PropTypes.string,
    cves: PropTypes.arrayOf(PropTypes.string),
    storeHost: PropTypes.string
});

export const findingShape = PropTypes.shape({
    component: PropTypes.string.isRequired,
    groupId: PropTypes.string,
    installedVersion: PropTypes.string,
    recommendedVersion: PropTypes.string,
    compatibility: PropTypes.string,
    requiredJahiaVersion: PropTypes.string,
    severity: PropTypes.string.isRequired,
    source: PropTypes.string.isRequired,
    advisories: PropTypes.arrayOf(advisoryShape).isRequired
});

export const storeErrorShape = PropTypes.shape({
    storeUrl: PropTypes.string,
    kind: PropTypes.string.isRequired,
    detail: PropTypes.string,
    message: PropTypes.string
});

export const reportShape = PropTypes.shape({
    status: PropTypes.string.isRequired,
    inProgress: PropTypes.bool,
    stale: PropTypes.bool,
    checkedAt: PropTypes.string,
    lastSuccess: PropTypes.string,
    nextCheckAt: PropTypes.string,
    jahiaVersion: PropTypes.string,
    errors: PropTypes.arrayOf(storeErrorShape),
    findings: PropTypes.arrayOf(findingShape)
});

/** What the banner of the Jahia interface counts (Query.securityUpdates.banner). */
export const bannerShape = PropTypes.shape({
    visible: PropTypes.bool,
    count: PropTypes.number,
    minimumSeverity: PropTypes.string
});
