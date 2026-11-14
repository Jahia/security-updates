# Changelog

All notable changes to this module are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the module follows
[Semantic Versioning](https://semver.org/).

## [Unreleased]

First release (1.0.0). It requires Jahia 8.2.3.7 or later.

### Added

- Security check of the installed Jahia core and modules against the store security feed
  (`modules-repository.securityUpdates.json`, schema version 1), on a schedule and on demand. On a standard
  Jahia, the module reads the store of the module manager.
- Optional `fixes[].introducedVersion` in the feed: an installed version below it is not affected. A malformed
  value is ignored, the fix is kept, and the value is counted as skipped. A malformed fixed version is
  dropped and counted too, so the report is `PARTIAL`, never `OK`.
- Compatibility of each recommended module version, from the store module catalogue
  (`modules-repository.moduleList.json`).
- Owner-flagged versions, reported separately from Jahia advisories. A reference that looks like a CVE or
  JSA id goes to the CVE list, the first https reference is the link, and the id of the report is the
  references joined with commas (cut to 197 characters and `...`).
- The stores of the module manager are read from its OSGi factory configurations (module manager 2.14 and
  later) or from the JCR (2.12 and earlier). The official store wins for an advisory id found in several feeds.
- Report status `OK`, `PARTIAL`, `UNAVAILABLE`, `NOT_CHECKED`, `DISABLED`, error kinds with a `detail`, a
  staleness rule (older than twice the refresh interval) and `nextCheckAt`.
- **Check now** runs in the background and never blocks. The page polls the report. A hidden key
  `onDemandMinIntervalSeconds` (default 30) sets the rate limit, counted from the later of the start and the end
  of the previous check.
- GraphQL API under `Query.securityUpdates` (`report`, `banner`, `settings`) and `Mutation.securityUpdates`
  (`refresh`, `updateSettings`, `resetSettings`).
- Admin page **Server settings > System components > Security updates** (English, French), built from
  Moonstone, WCAG 2.2 AAA contrast.
- Banner for `adminTemplates` users in the Jahia UI, decided server-side: counts distinct components at or
  above a configurable severity, with dismissal policies `SESSION` (browser tab), `HOURS`, `UNTIL_CHANGE`,
  `NEVER`, keyed on component and advisory. Colours are `#RRGGBB` with a contrast of at least 7:1, with
  suggestions.
- Settings section on the page, with full server-side validation, a confirmation before reset, and a
  read-only view for users who cannot change them. Settings are saved through ConfigurationAdmin into
  `karaf/etc`, which Jahia propagates across the cluster.
- OSGi configuration `org.jahia.community.securityupdates` (`enabled`, `refreshIntervalHours`,
  `useForgeSettings`, `extraFeedUrls`, `defaultStoreUrl`, `bannerEnabled`, `bannerMinimumSeverity`,
  `bannerIncludeOwnerReports`, `bannerBackgroundColor`, `bannerTextColor`, `bannerDismissPolicy`,
  `bannerDismissHours`, and the hidden `onDemandMinIntervalSeconds`).
- Each advisory shows the host of the store that published it (`storeHost`), as "(from <host>)" when it is
  not `store.jahia.com`. The page has a glossary, and the banner sits last in the page body, with a compact
  layout in a small window.
- A check has 5 minutes in all. An unexpected failure of one store does not stop the others, and the module
  list is not requested after a transport failure of the feed. Lists longer than 100 items are cut and
  reported.
- Audit log line at INFO for every settings change, with the user and the keys changed.

### Security

- Only https stores and feeds are accepted (`defaultStoreUrl`, `extraFeedUrls`, module manager stores).
- Changing the settings requires the `admin` permission (server administrators). Reading requires
  `adminTemplates`.
- Outbound requests go through Jahia's screened HTTP client (Jahia 8.2.3.7), checked at connect time.
- Error messages never repeat text received from a store. Responses are size and time bounded, JSON nesting is
  limited, each request has a 60 s limit for the whole request, and `extraFeedUrls` is capped at 10.
- The banner stores hashes of the user name and of the advisory keys, not the clear text. Production source maps are not referenced by the bundles and are not in the jar.

[Unreleased]: https://github.com/Jahia/security-updates/commits/main
