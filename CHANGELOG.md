# Changelog

All notable changes to this module are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] - 1.0.0-SNAPSHOT

### Added

- Security check of the installed Jahia core and modules against the store security feed
  (`modules-repository.securityUpdates.json`, schema version 1), scheduled and on demand.
- Compatibility of each recommended module version, from the store module catalogue
  (`modules-repository.moduleList.json`).
- Owner-flagged versions, reported separately from Jahia advisories.
- GraphQL API: `Query.securityUpdates.report` and `Mutation.securityUpdates.refresh`
  (permission `adminTemplates`).
- Admin page **Server settings > System components > Security updates** (English, French).
- Notice for `adminTemplates` users in the Jahia UI, decided server-side
  (`Query.securityUpdates.banner`): counts the findings at or above a configurable severity, from Jahia
  advisories and, optionally, owner reports; dismissible per browser session.
- Notice colours (`#RRGGBB`, contrast of at least 7:1) and dismissal policies (`SESSION`, `HOURS`,
  `UNTIL_CHANGE`, `NEVER`), the stored dismissals keyed per Jahia user.
- Settings section on the Security updates page, backed by `Query.securityUpdates.settings`,
  `Mutation.securityUpdates.updateSettings` (full server-side validation, nothing written on error) and
  `resetSettings`; saved through ConfigurationAdmin into the `karaf/etc` file.
- OSGi configuration `org.jahia.community.securityupdates` (`enabled`, `refreshIntervalHours`,
  `useForgeSettings`, `extraFeedUrls`, `defaultStoreUrl`, `bannerEnabled`, `bannerMinimumSeverity`,
  `bannerIncludeOwnerReports`, `bannerBackgroundColor`, `bannerTextColor`, `bannerDismissPolicy`,
  `bannerDismissHours`).
