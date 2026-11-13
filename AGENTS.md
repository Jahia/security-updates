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
| `feed.SecurityFeedParser` / `feed.ModuleCatalogParser` | org.json parsers; unknown `schemaVersion` fails closed; text cleaned and bounded; 10,000 entries cap; skipped entries counted |
| `feed.JsonNesting` | Cheap depth pre-scan (rejects nesting above 64 outside strings), replaces catching `StackOverflowError` |
| `feed.UntrustedText`, `feed.Json`, `feed.BoundedReader` | Text cleaning, typed JSON access, size and deadline bounded body reads |
| `feed.StoreClient` | Reads feed + catalogue of one store, maps every failure to a `StoreError` with a `StoreErrorDetail` (404 = `NOT_PUBLISHED`, catalogue skipped then); messages never echo remote text |
| `feed.JahiaHttpFetcher` (DS, `deactivate` = `cancelAll`) | GET through the screened client: timeouts, no redirects, size limits (feed 2 MB, catalogue 4 MB), keeps the current request so it can be aborted |
| `feed.ModuleManagerForgeSettingsReader` (DS, `ForgeSettingsReader`) | Module manager stores: OSGi factory configs `org.jahia.modules.modulemanager.forge.configuration` (module manager >= 2.14), else `JcrForgeSettingsReader`; throws `ForgeSettingsException` when unreadable (no silent fallback) |
| `feed.JcrForgeSettingsReader` (plain class) | `/settings/forgesSettings` `jnt:forgeServerSettings` nodes (module manager <= 2.12), system session |
| `feed.StoreUrls` | Absolute https only, ASCII, host, no user info / query / fragment; `isPlainHttp` for the `NOT_HTTPS` detail; `isJahiaStore` |
| `service.SecurityUpdateService` (DS, OCD) | One lock guards report, settings and schedule state, never held on the network. Daemon thread `security-updates-check`; non-blocking `refresh` (one coalesced task); `onDemandMinIntervalSeconds` from the later of last start/end; staleness (2 x interval) and `nextCheckAt`; reschedules only when `Settings.sameSchedule` is false; the task catches `Throwable`; `deactivate` cancels in-flight HTTP |
| `service.ReportAssembler` | Builds one report: stores, last-good data per store, matching, status. Not thread-safe (one check at a time) |
| `service.StoreTargets` | Store order (module manager or default, then extra feeds, Jahia Store first), max 20 targets |
| `service.JahiaInstalledComponentsProvider` (DS) | Started modules from `JahiaTemplateManagerService.getTemplatePackageRegistry().getRegisteredModules()` |
| `service.BannerPolicy` / `service.BannerSettings` | Pure banner decision: distinct components, `dismissalKeys` (`<component>\|<advisory id>`); lenient settings: invalid severity -> HIGH, colours -> default pair, policy -> SESSION, each with one WARN |
| `service.Settings` / `service.SettingsKeys` | Lenient reading of the properties (defaults, clamping, WARN once per change); hidden `onDemandMinIntervalSeconds` is not in `SettingsKeys.ALL` |
| `service.Colors`, `service.ContrastSuggestions` | WCAG contrast (7:1); suggestion by HSL lightness, same algorithm as `Settings/contrastSuggestions.js`, both pinned by `src/test/resources/contrast-suggestions.json` |
| `service.SettingsValidator` | Strict validation of a full settings set (UI): per-field errors (`NOT_HTTPS`, `TOO_MANY`, ...), nothing written on error |
| `service.SettingsStore` (DS) | `synchronized`; writes every key through `ConfigurationAdmin.getConfiguration(PID, "?").update(...)` with `MapDictionary` (`FrameworkUtil.asDictionary` is not available on R7), keeping `felix.fileinstall.filename` and the hidden key; audit INFO log; `load()` serves the `settings` query |
| `graphql.*` | One namespace: `Query.securityUpdates { report banner settings }`, `Mutation.securityUpdates { refresh updateSettings(input) resetSettings }`; input type `InputSecurityUpdatesSettingsValues`; `SecurityUpdatesGraphQLExtensionsProvider` (DS) holds the services, `ServiceLocator` reads them |

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
- When no configuration exists yet, `SettingsStore` writes the `.cfg` itself (temp file + atomic move, with the
  marker, removed again if the update fails) and points FileInstall at it.

## Report status

`OK` only when every configured store returned a schema-1 feed, every component was compared and the report is
not stale; `PARTIAL` when a feed is usable but the check is incomplete (any error kind except
`CATALOGUE_UNAVAILABLE`, or stale); `UNAVAILABLE` when no feed is available; `NOT_CHECKED` before the first
check; `DISABLED` by configuration. The UI shows "No known security update" only for `OK`, not stale, no
finding. The UI shows "Not available yet" when the status is `UNAVAILABLE` and every error is `NOT_PUBLISHED`.
Error kinds and details: `model.StoreErrorKind`, `model.StoreErrorDetail`; the README has the table.

## Frontend

- `src/javascript/init.js`: `jahiaApp-init:60` callback loads the `security-updates` i18n namespace,
  registers the admin route (`SecurityUpdates/register.js`) and starts the banner (`Banner/securityBanner.js`,
  plain DOM). One GraphQL request gets `jcr.nodeByPath("/").hasPermission("adminTemplates")` and
  `securityUpdates.banner`; the banner renders the server's decision and never filters findings itself.
  It refreshes every 15 min (`REFRESH_INTERVAL_MS`), on `visibilitychange`, and on the window event
  `security-updates:changed`; it hides while the page route is open. Selectors: `#security-updates-banner`
  (`data-count`, `data-position`), `[data-sel-role="security-updates-banner-link"]`,
  `[data-sel-role="security-updates-banner-dismiss"]`. Colours as CSS variables
  `--security-updates-banner-bg/-fg`.
- Dismissal (`Banner/bannerDismissal.js`, pure): identity is the `dismissalKeys` set; shown again when any
  current key is not in the stored set. SESSION in sessionStorage `security-updates-banner-dismissed` (per
  browser tab); HOURS and UNTIL_CHANGE in localStorage `security-updates-banner-dismissed:<SHA-256 hex of the
  user name>` as `{keys, at}`; NEVER has no button; storage failures fail open.
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
- `src/javascript/SecurityUpdates/`: `SecurityUpdates.jsx` (page), `useCheck.js` (Check now: calls `refresh`,
  then polls `report` every 2 s, at most 90 times, until `inProgress` is false), `reportView.js` (pure status,
  filter and error-text decisions), `ReportOverview`, `StatusMessage`, `StoreErrors`, `Findings`,
  `FindingsTable`, `AdvisoryList`, `propTypes.js`, `SecurityUpdates.gql.js`, `securityUpdatesUtils.js`.
- `src/javascript/Settings/`: `SettingsForm.jsx` (field ids `security-updates-setting-<key>`, errors `<id>-error`,
  help `<id>-help`), `fields.jsx`, `BannerPreview.jsx`, `ResetDialog.jsx`, `settingsResult.js` (field errors by a
  known-key map, never by arbitrary object keys), `settingsValues.js`, `colorUtils.js` (same formula as
  `Colors.java`), `contrastSuggestions.js`, `Settings.gql.js`. `canEdit=false` renders a read-only form.
- Plurals use `Intl.PluralRules` with `.one/.other` keys, not i18next suffixes.
- Locales: `src/main/resources/javascript/locales/{en,fr}.json` (same keys and placeholders, checked by a test).
  French says "bandeau" for the banner and "bulletin" for an advisory.
- Feed text is untrusted: React text only, links only for `https://` URLs.
- webpack builds in production mode by default with `hidden-source-map`; `yarn watch` uses development.

## Build

```bash
mvn -B clean install     # use a JDK 11 or 17 as JAVA_HOME
PATH="$PWD/node:$PATH" node/yarn/dist/bin/yarn lint     # Yarn 1 installed by the frontend-maven-plugin
PATH="$PWD/node:$PATH" node/yarn/dist/bin/yarn test
```

Maven installs Node 22.20.0 and Yarn 1.22.22 under `node/` during `generate-resources`, then runs
`yarn build:production` (lint, jest, webpack): a lint error fails the build. The bundled Yarn runs the `node`
found on `PATH`, so put `./node` first. `./node` exists after the first Maven build. Never use a global Yarn or
Corepack: Yarn Berry rewrites the v1 `yarn.lock`. The root `.yarnrc.yml` is a Yarn Berry file: it should be
deleted, or `"packageManager": "yarn@1.22.22"` added to `package.json`.

## Tests

- Java unit tests: `src/test/java` (JUnit 5, AssertJ, Mockito 4); JaCoCo report in `target/site/jacoco`.
  `SecurityUpdatesSchemaTest` validates the UI GraphQL documents against the generated schema.
- Jest: `src/javascript/**/__tests__` (including jest-axe).
- Cypress (Docker): `tests/` (the `@jahia/cypress` layout), against the `storefixture` nginx service (files in
  `tests/storefixture/contents/`). The fixture store is served over https with a certificate that Jahia trusts
  (http stores are refused). Provisioning fixtures in `tests/cypress/fixtures/security-updates/` set the
  settings, point the module at the fixture store and set the hidden `onDemandMinIntervalSeconds=0`, so no spec
  waits for the rate limit. Specs use `cypress-axe`. `ci.startup.sh` must not print the super user password.
  Run: build the jar, create `tests/.env` from `tests/.env.example` (placeholders only, committed by the
  maintainer), then `cd tests && bash ci.build.sh && bash ci.startup.sh`. Never read or write a `.env` file
  with real values in an assistant session.

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
  `Settings.toValues`, `SettingsValidator`, `GqlSettings`/`GqlSettingsInput`, the form and its locales
  (`DefaultConfigurationTest` and the locale tests catch most omissions). The hidden key
  `onDemandMinIntervalSeconds` is only in `SecurityUpdatesConfig`, `Settings` and the `.cfg`.
- Not done (known): `SecurityMatcher.toFinding` is long; no node id in the report; the report is not persisted;
  the `settings` query shows values after the lenient reading.
