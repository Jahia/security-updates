# security-updates

Jahia 8.2 OSGi module that lists the installed components (Jahia core and modules) having updates that
fix security issues, from the Jahia Store security feed. Admin UI at
`/jahia/administration/securityUpdates` (Server settings > System components).

## Key Facts

- **groupId:artifactId**: `org.jahia.community:security-updates` | **version**: `1.0.0-SNAPSHOT`
- **Parent**: `org.jahia.modules:jahia-modules:8.2.2.1`, Java 11 source level (no records, no
  pattern-matching `instanceof`), `jahia-module-type` system, no module signature
- **Java package**: `org.jahia.community.securityupdates` (`model`, `matching`, `feed`, `service`, `graphql`)
- **jahia-depends**: `serverSettings,graphql-dxm-provider,default`
- **Permission**: `adminTemplates` everywhere (GraphQL fields and admin route)
- **Config PID**: `org.jahia.community.securityupdates` (default file in
  `src/main/resources/META-INF/configurations/`, first line `# default configuration - won't be overridden`)

## Architecture

| Class | Role |
|-------|------|
| `matching.SecurityMatcher` | Pure matching rules (advisory per line, owner flags, grouping, compatibility). Heavily unit tested |
| `matching.Versions` | Well-formedness, line (module major / core `major.minor.sp`), comparison via Jahia `VersionComparator` |
| `feed.SecurityFeedParser` / `feed.ModuleCatalogParser` | org.json parsers; unknown `schemaVersion` fails closed; text cleaned and bounded |
| `feed.StoreClient` | Reads feed + catalogue of one store, maps every failure to a `StoreError` (404 = `NOT_PUBLISHED`) |
| `feed.JahiaHttpFetcher` (DS) | GET through Jahia `HttpClientService`: timeouts, no redirects, size limits, keeps the status code |
| `feed.JcrForgeSettingsReader` (DS) | `/settings/forgesSettings` `jnt:forgeServerSettings` nodes, system session |
| `feed.StoreUrls` | Absolute http(s), host, no user info / query / fragment |
| `service.SecurityUpdateService` (DS, OCD) | Daemon-thread schedule, on-demand refresh (30 s rate limit), last-good data per store, status, WARN once per new finding |
| `service.JahiaInstalledComponentsProvider` (DS) | Started modules from `JahiaTemplateManagerService.getTemplatePackageRegistry().getRegisteredModules()` |
| `service.BannerPolicy` / `service.BannerSettings` | Pure banner decision from the current report and the `banner*` settings (lenient: invalid severity -> HIGH, colours -> default pair, policy -> SESSION, each with one WARN) |
| `service.Colors` | `#RRGGBB` parsing, WCAG contrast ratio (7:1 minimum) |
| `service.ContrastSuggestions` | Colour reaching 7:1 by moving the HSL lightness of one colour (smallest step, checked after rounding); same algorithm as `Settings/contrastSuggestions.js`, both pinned by `src/test/resources/contrast-suggestions.json` |
| `service.SettingsValidator` | Strict validation of a full settings set (UI): per-field errors, nothing returned on error |
| `service.SettingsStore` (DS) | Writes every key through `ConfigurationAdmin.getConfiguration(PID, "?").update(...)`, keeping `felix.fileinstall.filename` |
| `graphql.*` | One namespace: `Query.securityUpdates { report banner settings }`, `Mutation.securityUpdates { refresh updateSettings(input) resetSettings }`; input type `InputSecurityUpdatesSettingsValues` |

DS packages are listed one by one in `_dsannotations` (class patterns, `<package>.*`): add a new
package there if it gets a `@Component`, or no `OSGI-INF/*.xml` is generated for it.

## Report status

`OK` only when every configured store returned a schema-1 feed; `PARTIAL` when some feed is missing
but data is available (fresh or kept from an earlier check); `UNAVAILABLE` when no feed is available;
`NOT_CHECKED` before the first check; `DISABLED` by configuration. The UI shows "No known security
update" only for `OK` with no finding.

## Frontend

- `src/javascript/init.js`: `jahiaApp-init:60` callback loads the `security-updates` i18n namespace,
  registers the admin route and shows the banner (`Banner/securityBanner.js`, plain DOM). The banner
  checks `adminTemplates` (`jcr.nodeByPath("/").hasPermission`), then renders `securityUpdates.banner`
  as decided by the server; it never filters findings itself. Selectors: `#security-updates-banner`
  (`data-count`), `[data-sel-role="security-updates-banner-link"]`,
  `[data-sel-role="security-updates-banner-dismiss"]`. Colours as CSS variables
  `--security-updates-banner-bg/-fg`. Dismissal (`Banner/bannerDismissal.js`, pure): key
  `components.join('|')`; SESSION in sessionStorage `security-updates-banner-dismissed`; HOURS and
  UNTIL_CHANGE in localStorage `security-updates-banner-dismissed:<username>` as `{key, at}`; NEVER has no
  button; storage failures fail open.
- UI built from Moonstone (`@jahia/moonstone` ^2.21.1, shared by module federation with `import: false`,
  so the Jahia UI's own copy runs): `LayoutContent` + `Header` (h1 title, Check now, status Chip),
  `Table`/`TableRow` (rows `tabIndex={-1}`), `Chip`, `RadioGroup`/`RadioItem`, `Field`, `Switch`, `Input`,
  `Textarea`, `Button` (`color="accent"` for the main action, `variant="ghost"`/`"outlined"`; there is no
  `primary` variant), `Typography`, `Information`/`Warning`, `Separator`. Moonstone's `Field` puts its id on
  its wrapper and does not associate its label: controls carry `aria-label` and our own help/error
  Typography with ids. Severity and dismiss policy are native selects (Moonstone's Dropdown names its
  listbox after the selected value). SCSS is layout only, on Moonstone CSS variables (`--spacing-*`,
  `--color-*`, `--moon-font-family`). The Jest double `__mocks__/jahiaMoonstone.js` mirrors the real markup
  and marks each component with `data-moonstone`.
- `src/javascript/Settings/`: the settings section (`SettingsForm.jsx`, field ids
  `security-updates-setting-<key>`, errors `<id>-error`, help `<id>-help`, suggestions
  `security-updates-suggestion-text|background`), colour helpers (`colorUtils.js`, same formula as
  `Colors.java`), suggestions (`contrastSuggestions.js`), form/input conversion (`settingsValues.js`).
- `src/javascript/SecurityUpdates/`: page (`SecurityUpdates.jsx`), GraphQL documents, pure helpers
  (`securityUpdatesUtils.js`).
- Locales: `src/main/resources/javascript/locales/{en,fr}.json` (same keys, checked by a test).
- Feed text is untrusted: React text only, links only for `https://` URLs.

## Build

```bash
JAVA_HOME=/usr/lib/jvm/graalvm-jdk-17.0.12+8.1 mvn -B clean install
./node/yarn/dist/bin/yarn lint      # Yarn 1 installed by the frontend-maven-plugin
./node/yarn/dist/bin/yarn test
```

`yarn build:production` (run by Maven) runs lint, jest and webpack: a lint error fails the build. Do not
use a global Yarn Berry: it rewrites the v1 `yarn.lock`.

## Tests

- Java unit tests: `src/test/java` (JUnit 5, AssertJ, Mockito 4); JaCoCo report in `target/site/jacoco`.
- Jest: `src/javascript/**/__tests__`.
- Cypress (Docker): `tests/` copied from `what-is-my-public-server-ip`; specs
  `01-securityUpdates.cy.ts` (API, honest status, page), `02-securityUpdates-Permissions.cy.ts`
  (`adminTemplates` gate), `03-securityUpdates-Findings.cy.ts`, `04-securityUpdates-Banner.cy.ts` and
  `05-securityUpdates-Settings.cy.ts`
  (against the `storefixture` nginx service, files in `tests/storefixture/contents/`; settings switched
  by the provisioning fixtures in `tests/cypress/fixtures/security-updates/`, each of which also points
  the module at the fixture store). `tests/.env.example` must be provided before running.

## Gotchas

- `store.jahia.com` answers 404 for the security feed until it is deployed: the report is then
  `UNAVAILABLE` / `NOT_PUBLISHED`, by design.
- Store passwords come from JCR and must never be logged; transport errors are reported by exception
  type only.
- `org.json` is imported with a wide range on purpose (bnd would pin the build-time version).
- Metatype labels (`OSGI-INF/l10n/**.properties`) are read as ISO-8859-1: write accents as `\uXXXX`
  (a test checks it).
- The default `.cfg` must keep its first line `# default configuration - won't be overridden`: the module
  extender then leaves an existing `karaf/etc` file alone, and FileInstall writes UI changes back into it.
- A new configuration key needs: the OCD method, the `.cfg` line, en/fr labels, `SettingsKeys`,
  `Settings.toValues`, `SettingsValidator`, `GqlSettings`/`GqlSettingsInput`, the form and its locales
  (`DefaultConfigurationTest` and the locale tests catch most omissions).
