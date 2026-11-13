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
| `graphql.*` | One namespace: `Query.securityUpdates { report }`, `Mutation.securityUpdates { refresh }` |

DS packages are listed one by one in `_dsannotations` (class patterns, `<package>.*`): add a new
package there if it gets a `@Component`, or no `OSGI-INF/*.xml` is generated for it.

## Report status

`OK` only when every configured store returned a schema-1 feed; `PARTIAL` when some feed is missing
but data is available (fresh or kept from an earlier check); `UNAVAILABLE` when no feed is available;
`NOT_CHECKED` before the first check; `DISABLED` by configuration. The UI shows "No known security
update" only for `OK` with no finding.

## Frontend

- `src/javascript/init.js`: `jahiaApp-init:60` callback loads the `security-updates` i18n namespace,
  registers the admin route and shows the banner (`Banner/securityBanner.js`, plain DOM).
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
  `01-securityUpdates.cy.ts` (API, honest status, page) and `02-securityUpdates-Permissions.cy.ts`
  (`adminTemplates` gate). `tests/.env.example` must be provided before running.

## Gotchas

- `store.jahia.com` answers 404 for the security feed until it is deployed: the report is then
  `UNAVAILABLE` / `NOT_PUBLISHED`, by design.
- Store passwords come from JCR and must never be logged; transport errors are reported by exception
  type only.
- `org.json` is imported with a wide range on purpose (bnd would pin the build-time version).
