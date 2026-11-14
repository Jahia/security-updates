# security-updates

Jahia 8.2.3.7+ OSGi module that lists the installed components (Jahia core and modules) having updates that
fix security issues, from the Jahia Store security feed. Admin UI at
`/jahia/administration/securityUpdates` (Server settings > System components). User documentation is in
`README.md`; this file holds the internals.

## Key Facts

- **groupId:artifactId**: `org.jahia.community:security-updates` | **version**: `1.0.0-SNAPSHOT`
- **Parent**: `org.jahia.modules:jahia-modules:8.2.3.7` (gives `Jahia-Required-Version: 8.2.3.7`), Java 11
  source level (no records, no pattern-matching `instanceof`), `jahia-module-type` system, no module signature
- **Java package**: `org.jahia.community.securityupdates` (`model`, `matching`, `feed`, `service`, `graphql`)
- **jahia-depends**: `serverSettings,graphql-dxm-provider`
- **Permissions**: `adminTemplates` reads (report, banner, settings, refresh) and gates the admin route;
  `admin` (server administrators) writes (`updateSettings`, `resetSettings`, `canEdit`). In Jahia's tree
  `adminTemplates` is a child of `admin`; the web-designer role has `adminTemplates` only
  (`root-roles.xml` in jahia-private `war/src/main/webapp/WEB-INF/etc/repository/`). The permissions are
  constants in `graphql.Permissions`; there is no `src/main/import`.
- **Config PID**: `org.jahia.community.securityupdates` (default file in
  `src/main/resources/META-INF/configurations/`, first line `# default configuration - won't be overridden`)
- Outbound HTTP only through `HttpClientService.getHttpClient(url)`, the screened client (Jahia 8.2.3.7
  `SsrfProtectionDnsResolver`, checked at connect time). Never use `getHttpClientForConfiguredTarget`: the URLs
  can be set by GraphQL users.

## Architecture

| Class | Role |
|-------|------|
| `matching.SecurityMatcher` | Pure matching rules (advisory per line, owner flags, grouping, compatibility, caps: 50 refs per finding, 2,000 per report, 500 fixed versions per component, owner id 200 chars). Heavily unit tested |
| `matching.Versions` | Well-formedness, line (module major / core `major.minor.sp`), comparison via Jahia `VersionComparator` |
| `feed.SecurityFeedParser` / `feed.ModuleCatalogParser` | org.json parsers; unknown `schemaVersion` fails closed; text cleaned and bounded (tab/CR/LF -> space, control and format characters removed); versions are validated before any cut (more than 64 characters = malformed); optional `fixes[].introducedVersion` (malformed = ignored, fix kept, counted as skipped); caps (10,000 advisories and flagged versions; 100 fixes per advisory, fixed versions per fix, CVEs per advisory, references per flagged version) mark the feed truncated: `NOT_UNDERSTOOD` / `ENTRIES_SKIPPED`, message `StoreClient.TRUNCATED_FEED`; each malformed fixed version dropped from a kept fix is counted as skipped (never `OK`), a fix with only malformed versions is dropped and counted once; an unexpected `RuntimeException` is `INVALID_JSON` with `unexpected parser failure (<ExceptionType>)` and a WARN |
| `feed.JsonNesting` | Cheap depth pre-scan (rejects nesting above 64 outside strings). A quote opens a string only at the start of the document or after `{ [ , : ; =`, whitespace ignored, so a quote inside an unquoted token cannot hide the depth. Both parsers also catch `StackOverflowError` as a backstop: `INVALID_JSON`, "The security feed is nested too deeply" / "The module list is nested too deeply" |
| `feed.UntrustedText`, `feed.Json`, `feed.BoundedReader` | Text cleaning, typed JSON access, size and deadline bounded body reads |
| `feed.StoreClient` | Reads feed + catalogue of one store, maps every failure to a `StoreError` with a `StoreErrorDetail` (404 = `NOT_PUBLISHED`, catalogue skipped then); the catalogue is also skipped after a transport failure of the feed (`TIMEOUT`, `CONNECTION`, `SSRF_BLOCKED`), with no catalogue error; `TOO_LARGE`, HTTP status and parse errors still read it; messages never echo remote text |
| `feed.JahiaHttpFetcher` (DS, own `deactivate()` = `cancelAll()` + stop the watchdog) | GET through the screened client: 10 s connect, 30 s response, one 60 s limit for the whole request, enforced by the daemon watchdog thread `security-updates-http-deadline`; no redirects; size limits (feed 2 MB, catalogue 4 MB); an error or oversized body is not read further and the connection is closed; keeps the current request so it can be aborted. Timeout: detail `TIMEOUT`, message `Security feed: timed out (SocketTimeoutException)` (`Module list: ` for the catalogue) |
| `feed.ModuleManagerForgeSettingsReader` (DS, `ForgeSettingsReader`) | Module manager stores: OSGi factory configs `org.jahia.modules.modulemanager.forge.configuration` (module manager >= 2.14), else `JcrForgeSettingsReader`; throws `ForgeSettingsException` when unreadable (no silent fallback) |
| `feed.JcrForgeSettingsReader` (plain class) | `/settings/forgesSettings` `jnt:forgeServerSettings` nodes (module manager <= 2.12), system session |
| `feed.StoreUrls` | Absolute https only, ASCII, host, no user info / query / fragment; `isPlainHttp` for the `NOT_HTTPS` detail; `isJahiaStore` |
| `service.SecurityUpdateService` (DS, OCD) | One lock guards report, settings and schedule state, never held on the network. Daemon thread `security-updates-check`; non-blocking `refresh` (one coalesced task); `onDemandMinIntervalSeconds` from the later of last start/end; staleness (2 x interval) and `nextCheckAt`; reads the raw properties (an unreadable value takes its default with a WARN, the component still activates); reschedules only when `Settings.sameSchedule` is false; when the schedule inputs (stores, interval, enabled) change during a check, that check publishes nothing and a rerun flag starts one new check at its end (banner-only changes do not cancel); a check that starts after a settings change clears any pending rerun; a scheduled tick skipped while one runs sets the rerun flag only if that check read its settings before the tick was due, and `nextCheckAt` = skip time + interval; a check running when the checks are disabled publishes nothing, even if they are enabled again before it ends, and one rerun follows; re-enabling shows `NOT_CHECKED` but keeps `lastSuccess`; `getBanner()` reads the report and the settings in one snapshot; the task catches `Throwable`; `deactivate` cancels in-flight HTTP |
| `service.ReportAssembler` | Builds one report: stores, last-good data per store, matching, status. Not thread-safe (one check at a time). `CHECK_DEADLINE` is 5 minutes from the start of the check (through `mustStop`): no new store starts after it, the store being read finishes within its own limits, and each store not reached gets `UNAVAILABLE` / `TIMEOUT` "Not checked: the check ran out of time" (plus the kept-data suffix), WARN `Security source {} not checked: the check ran out of time (5 minutes)`. Each store is isolated: a `Throwable` other than `OutOfMemoryError` and `ThreadDeath` gives that store `UNAVAILABLE` / `CHECK_FAILED` ("The check of this store failed unexpectedly; see the Jahia log", ERROR log `The check of the security source {} failed unexpectedly`), the other stores are still read and its last good data is kept |
| `service.StoreTargets` | Store order (module manager or default, then extra feeds, Jahia Store first), max 20 targets. The default store is added only when no module manager store was read and `defaultStoreUrl` is not empty, so `useForgeSettings=false` + empty `defaultStoreUrl` reads only the extra feeds. A 404 store (`NOT_PUBLISHED`) next to a working feed keeps the report `PARTIAL` |
| `service.JahiaInstalledComponentsProvider` (DS) | Started modules from `JahiaTemplateManagerService.getTemplatePackageRegistry().getRegisteredModules()` |
| `service.BannerPolicy` / `service.BannerSettings` | Pure banner decision: distinct components, `dismissalKeys` (`<component>\|<advisory id>`); lenient settings: invalid severity -> HIGH, colours -> default pair, policy -> SESSION, each with one WARN |
| `service.Settings` / `service.SettingsKeys` | Lenient reading of the properties (defaults, WARN once per change; a number outside the int range is clamped, for example `refreshIntervalHours 4294967297 is out of range (1 to 720): using 720`; a multi-valued property is joined with ", "); hidden `onDemandMinIntervalSeconds` is not in `SettingsKeys.ALL` |
| `service.Colors`, `service.ContrastSuggestions` | WCAG contrast (7:1); suggestion by HSL lightness, same algorithm as `Settings/contrastSuggestions.js`, both pinned by `src/test/resources/contrast-suggestions.json` |
| `service.SettingsValidator` | Strict validation of a full settings set (UI): per-field errors (`NOT_HTTPS`, `TOO_MANY`, ...), nothing written on error |
| `service.SettingsStore` (DS) | `synchronized`; creates a new `.cfg` through a hard link (`Files.createLink`), so an existing file is never replaced; without hard links it checks that the file does not exist, then moves without `REPLACE_EXISTING`; refuses to write when the `.cfg` exists but is not loaded yet (`WRITE_FAILED` in the UI, ERROR `Could not save the security updates settings` in the log, file never overwritten); writes every key through `ConfigurationAdmin.getConfiguration(PID, "?").update(...)` with `MapDictionary` (`FrameworkUtil.asDictionary` is not available on R7), keeping `felix.fileinstall.filename` and the hidden key; audit INFO log, which never shows the `user:password@` part of a URL (nor does the `settings` query); `load()` serves the `settings` query |
| `graphql.*` | One namespace: `Query.securityUpdates { report banner settings }`, `Mutation.securityUpdates { refresh updateSettings(input) resetSettings }`; `SecurityUpdatesAdvisory.storeHost: String!` is the lower-case host of the store that published the advisory or owner report; `lastSuccess` is the last check whose status was `OK`; input type `InputSecurityUpdatesSettingsValues`; `SecurityUpdatesGraphQLExtensionsProvider` (DS) holds the services, `ServiceLocator` reads them |

DS packages are listed one by one in `_dsannotations` (class patterns, `<package>.*`): add a new
package there if it gets a `@Component`, or no `OSGI-INF/*.xml` is generated for it. `Import-Package` lists
`org.json` with a wide range on purpose, then `*`.

## Settings persistence (how it survives restarts and a cluster)

From the Jahia sources (`jahia-private`):

- Felix FileInstall (`org.apache.felix.fileinstall` 3.7.4-jahia1, `ConfigInstaller.doConfigurationEvent`)
  writes a ConfigurationAdmin update back to the file named by `felix.fileinstall.filename`, through its
  `TypedProperties`, which keeps the comments and the first line of the existing file.
- The module extender (`jahiamodule-extender` `Activator.checkConfigurations`) copies the module's
  `META-INF/configurations/*.cfg` to `karaf/etc` only when the file does not exist there, or when the module's
  file has no line starting with `# default configuration`. The shipped file has that marker.
- The config manager (`config-manager` `ConfigServiceImpl`) classifies such a file as `MODULE_DEFAULT`,
  and stores `karaf/etc` in the JCR on each configuration event when its auto-save is on. This is how
  `karaf/etc` changes reach the other cluster nodes.
- When no configuration exists yet, `SettingsStore` writes the `.cfg` itself to a temp file, with the marker,
  then links it to its final name (hard link, never replacing a file), removes it again if the update fails,
  and points FileInstall at it.

## Report status

`OK` only when every configured store returned a schema-1 feed, every component was compared and the report is
not stale; `PARTIAL` when a feed is usable but the check is incomplete (any error kind except
`CATALOGUE_UNAVAILABLE`, or stale); `UNAVAILABLE` when no feed is available; `NOT_CHECKED` before the first
check; `DISABLED` by configuration. The UI shows "No known security update" only for `OK`, not stale, no
finding. The UI shows "Not available yet" when the status is `UNAVAILABLE` and every error is `NOT_PUBLISHED`.
Error kinds and details: `model.StoreErrorKind`, `model.StoreErrorDetail`; the README has the table. `UNAVAILABLE` also covers the per-store `CHECK_FAILED` and the out-of-time `TIMEOUT`; `NOT_UNDERSTOOD` / `ENTRIES_SKIPPED` also covers the caps of 100; `CATALOGUE_UNAVAILABLE` also covers a truncated catalogue; `MALFORMED_VERSION` also covers the Jahia version. The banner never depends on the report status: a `PARTIAL` or stale report still shows the banner for its findings.

## Frontend

- `src/javascript/init.js`: `jahiaApp-init:60` callback loads the `security-updates` i18n namespace,
  registers the admin route (`SecurityUpdates/register.js`) and starts the banner (`Banner/securityBanner.js`,
  plain DOM). One GraphQL request gets `jcr.nodeByPath("/").hasPermission("adminTemplates")` and
  `securityUpdates.banner`; the banner renders the server's decision and never filters findings itself.
  It refreshes every 15 min (`REFRESH_INTERVAL_MS`), on `visibilitychange` (skipped when the last fetch is less than 30 s old), and on the window event
  `security-updates:changed`; it hides while the page route is open. A failed request (network, 20 s timeout through `AbortController`, HTTP or GraphQL error, malformed answer) keeps the current banner and is retried after 60 s (`RETRY_INTERVAL_MS`). `<html data-security-updates-banner="pending|ready">` is the test hook: `pending` while a request runs, `ready` after each decision, removed when the banner stops (the e2e absence checks wait for `ready`). `init.js` isolates each step and logs a `console.warn` when one fails. Selectors: `#security-updates-banner`
  (`data-count`, `data-position`), `[data-sel-role="security-updates-banner-link"]`,
  `[data-sel-role="security-updates-banner-dismiss"]`. Colours as CSS variables
  `--security-updates-banner-bg/-fg`.
  The banner is the LAST child of `<body>` and a fixed box at the bottom right. It moves to the other edge only when it would cover the focused element and that element is clear of the other edge, and it holds the new edge for 2 s (`FLIP_HOLD_MS`) for the same element (overlap check on focusin and again after a `requestAnimationFrame`, on scroll in capture mode and on resize, at
  most once per frame). While it shows, `<html>` gets a scroll-padding at that side, written only when the value changes. At a width or height of at most 40rem it is a compact full-width strip on the edge, at most 40vh high, scrolling inside. The Hide button is created once and
  updated in place, so focus stays. The banner is announced only when the count or the dismissal keys change; the last
  announced state is in sessionStorage `security-updates-banner-announced`. After Hide, the polite status
  `#security-updates-banner-feedback` gives one text per policy. Focus goes to the last element outside the banner that had it, else to the element focused when the banner appeared, else it stays on body; Jahia's
  `<main>` is not touched.
- Dismissal (`Banner/bannerDismissal.js`, pure): identity is the `dismissalKeys` set; shown again when any
  current key is not in the stored set. everything stored is hashed: SHA-256 hex (SubtleCrypto) in a secure context, else FNV-1a as 8 hex characters. SESSION in sessionStorage `security-updates-banner-dismissed` (a JSON array of the hashed keys, per
  browser tab); HOURS and UNTIL_CHANGE in localStorage `security-updates-banner-dismissed:<user hash>` as `{keys: [hash per key], at}`; sessionStorage `security-updates-banner-announced` holds `<count>|<sorted hashes>`. Keys stored in clear by an older version never match, so the banner shows again (fail open). NEVER has no button; storage failures fail open.
- UI built from Moonstone (`@jahia/moonstone`, shared by module federation with `import: false`, so the Jahia
  UI's own copy runs): `LayoutContent` + `Header`, `Table`/`TableRow`, `Chip`, `RadioGroup`/`RadioItem`,
  `Switch`, `Input`, `Textarea`, `Button` (`color="accent"` for the main action; there is no `primary` variant),
  `Typography`, `Dialog` (reset confirmation), `Loader`. Controls use native `<label htmlFor>`; our own
  help/error Typography carry ids. Severity and dismiss policy are native selects (Moonstone's Dropdown names
  its listbox after the selected value). SCSS is layout only, on Moonstone CSS variables. The Jest double
  `__mocks__/jahiaMoonstone.js` mirrors the real markup and marks each component with `data-moonstone`.
  Residual: Moonstone accent and danger buttons (white text) may not reach 7:1.
- `src/javascript/common/`: `constants.js` (shared constants, event names, the Jahia upgrade documentation URL,
  not verified), `i18n.js`.
- `src/javascript/SecurityUpdates/`: `SecurityUpdates.jsx` (page; it refetches the report after a settings save or reset, through the `onSettingsChanged` prop of `SettingsForm`; a failed refetch keeps the last good report and shows `page.updateError`), `useCheck.js` (Check now: calls `refresh`,
  then polls `report` every 2 s, at most 90 times, until `inProgress` is false; stops after 3 failed polls in a row and announces the failure; announces completion only once the new report is loaded; a click during a check adds "A check is already running."), `reportView.js` (pure status,
  filter and error-text decisions; when every error is `NOT_PUBLISHED` the page shows a neutral "Store details" heading `#security-updates-store-details-title`, and names the Jahia Store only for the host store.jahia.com), `ReportOverview`, `StatusMessage`, `StoreErrors` (the label "Technical details (in English):" is translated and only the server message carries `lang="en"`), `Findings`,
  `FindingsTable`, `AdvisoryList` (shows "(from <store>)" for a store other than store.jahia.com, and "(from <store>, link to <host>)" when the link goes to another host; external links get a visible new-tab icon), `propTypes.js`, `SecurityUpdates.gql.js`, `securityUpdatesUtils.js`.
- `src/javascript/Settings/`: `SettingsForm.jsx` (field ids `security-updates-setting-<key>`, errors `<id>-error`,
  help `<id>-help`), `fields.jsx`, `BannerPreview.jsx`, `ResetDialog.jsx`, `settingsResult.js` (field errors by a
  known-key map, never by arbitrary object keys), `settingsValues.js`, `colorUtils.js` (same formula as
  `Colors.java`), `contrastSuggestions.js`, `Settings.gql.js`. `canEdit=false` renders the values as text, in a `<dl>` per group, with no control.
- The report and settings queries use the Apollo fetch policy `no-cache`: both write to `ROOT_QUERY.securityUpdates` and the app shell defines no merge for it.
- Page extras: a glossary in a `<details>` ("What these terms mean"), and the `filteredEmpty` text announced when a filter leaves nothing.
- Settings form: after a colour suggestion focus moves to the changed field and the confirmation shows next to the preview; editing a field clears its error and the status message; during a save the text inputs are read-only and the fieldset has `aria-busy`; a refused save lists links to the failing fields ("Settings to correct:"); the disabled hours field shows a note; the saved message depends on whether a check setting changed (`saved` or `savedNow` in `en.json`). The reset dialog has Cancel first (focused when it opens) then the danger button "Reset to defaults", and `aria-describedby` on its body.
- Plurals use `Intl.PluralRules` with `.one/.other` keys, not i18next suffixes.
- Locales: `src/main/resources/javascript/locales/{en,fr}.json` (same keys and placeholders, checked by a test).
  French says "bandeau" for the banner and "bulletin" for an advisory.
- Feed text is untrusted: React text only, links only for `https://` URLs.
- webpack builds in production mode by default with `hidden-source-map`; the maps are not referenced by the bundles and `pom.xml` leaves them out of the jar; `yarn watch` uses development.

## Build

```bash
mvn -B clean install     # use a JDK 11 or 17 as JAVA_HOME
PATH="$PWD/node:$PATH" node/yarn/dist/bin/yarn lint     # Yarn 1 installed by the frontend-maven-plugin
PATH="$PWD/node:$PATH" node/yarn/dist/bin/yarn test
```

Maven installs Node 22.20.0 and Yarn 1.22.22 under `node/` during `generate-resources`, then runs
`yarn build:production` (lint, jest, webpack) in the same phase, before the Java compile and the Java tests: a
lint error fails the build. The bundled Yarn runs the `node`
found on `PATH`, so put `./node` first. `./node` exists after the first Maven build. Never use a global Yarn or
Corepack: Yarn Berry rewrites the v1 `yarn.lock`. The root `.yarnrc.yml` (`nodeLinker: node-modules`) is a Yarn Berry
file that is still committed. `package.json` has no `packageManager` field. Yarn 1 ignores the file; it only
matters if a Corepack Yarn runs in this directory.

## Tests

- Java unit tests: `src/test/java` (JUnit 5, AssertJ, Mockito 4); JaCoCo report in `target/site/jacoco`.
  `SecurityUpdatesSchemaTest` validates the UI GraphQL documents against the generated schema.
- Jest: `src/javascript/**/__tests__` (including jest-axe).
- `-Dtest=Class` skips `@Nested` classes with this Surefire version: use `-Dtest='Class*'`.
- Cypress (Docker): `tests/` (the `@jahia/cypress` layout), against the `storefixture` nginx service (files in
  `tests/storefixture/contents/`). The fixture store is served over https with a certificate that Jahia trusts
  (http stores are refused). Provisioning fixtures in `tests/cypress/fixtures/security-updates/` set the
  settings, point the module at the fixture store and set the hidden `onDemandMinIntervalSeconds=0` (3600 only in the rate-limit test), so no other spec
  waits for the rate limit. Specs use `cypress-axe`. `ci.startup.sh` must not print the super user password.
  Spec `08-securityUpdates-StoreErrors.cy.ts` covers the store error pages: the `storefixture` nginx has a
  test-only njs switch on port 8081 that changes the answer of the fixture store. Jahia is bound to 127.0.0.1 in the tests.
  Run: build the jar, create `tests/.env` from `tests/.env.example` (placeholders only, committed by the
  maintainer), then `cd tests && bash ci.build.sh && bash ci.startup.sh`. Never read or write a `.env` file
  with real values in an assistant session. `.env` stays in the Cypress image on purpose: `@jahia/cypress` builds with its own
  `env.Dockerfile` and its scripts source `.env` in the container. Remove the images after a run. The image contains `tests/.env`: never push `TESTS_IMAGE`.

## Gotchas

- `store.jahia.com` answers 404 for the security feed until it is deployed: the report is then
  `UNAVAILABLE` / `NOT_PUBLISHED`, by design.
- Store passwords come from the module manager (OSGi factory config or JCR) and must never be logged; transport
  errors are reported by exception type only; error messages never repeat remote text.
- The module manager stores are editable by `adminTemplates` holders: their own trust boundary. https applies,
  credentials go only to that host, no redirects.
- Metatype labels (`OSGI-INF/l10n/security-updates/config*.properties`) are read as ISO-8859-1: write accents as
  `\uXXXX` (a test checks it).
- The default `.cfg` must keep its first line `# default configuration - won't be overridden`: the module
  extender then leaves an existing `karaf/etc` file alone, and FileInstall writes UI changes back into it.
- Initial JCR content is not used. If `src/main/import` is ever added, bump the version when it changes: it is
  imported once per module version.
- A new configuration key needs: the OCD method, the `.cfg` line, en/fr labels, `SettingsKeys`,
  `Settings.fromProperties` + the constructor + `toValues`, and for a key that changes the checks
  `Settings.sameSchedule` and `SCHEDULE_FIELDS` in `Settings/settingsValues.js`, `SettingsValidator`,
  `GqlSettings`/`GqlSettingsInput`, the form fields, the en/fr locales, the README configuration table and the
  Cypress fixtures (`DefaultConfigurationTest` and the locale tests catch most omissions). The hidden key
  `onDemandMinIntervalSeconds` is in `SecurityUpdatesConfig`, `Settings`, the `.cfg`, the metatype labels
  and the e2e fixtures; it is not in `SettingsKeys.ALL`.
- Metatype `defaultStoreUrl` is `required=false`. The dismiss labels of the metatype are the same texts as the UI.
- `HttpClientService` in Jahia core builds its pools with the default `Http1Config`: header size and count are not bounded, so the size limits cover bodies only. The module keeps the screened client on purpose (its own client would lose the SSRF screening and the proxy settings).
- Not done (known): `SecurityMatcher.toFinding` is long; no node id in the report; the report is not persisted;
  the `settings` query shows values after the lenient reading.
