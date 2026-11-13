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
- Notice for `adminTemplates` users when high or critical Jahia advisories apply.
- OSGi configuration `org.jahia.community.securityupdates` (`enabled`, `refreshIntervalHours`,
  `useForgeSettings`, `extraFeedUrls`, `defaultStoreUrl`).
