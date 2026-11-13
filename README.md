# security-updates

A Jahia 8.2 module that lists, in the Jahia administration, the installed components (Jahia core and
modules) for which the Jahia Store publishes updates that fix security issues, much like
`dnf updateinfo list --security`. For each component it shows the installed version, the recommended
version, whether that version runs on this Jahia, the severity and the advisories (id, title, link,
CVEs).

## Installation

- Build with `mvn clean install` (see [Build](#build)), or download the JAR.
- In Jahia, go to **Administration > Server settings > System components > Modules**, upload
  `security-updates-<version>.jar` and check that the module is started.
- Open **Administration > Server settings > System components > Security updates**. The page and the
  API require the `adminTemplates` permission (the one that gives access to module management).

The first check runs about one minute after the module starts; **Check now** runs one at once (at most
once every 30 seconds).

## What the page shows

The page is built from Jahia's design system, Moonstone (the version the Jahia UI provides, shared through
module federation like the other administration modules), so it looks like the other administration
pages: a `LayoutContent` with a `Header` (title, **Check now** as the main action, the report status as a
chip), status lines with Moonstone icons, a Moonstone `Table`, and Moonstone form fields.

- A table of components with security updates, most severe first. Severity is written out, not only
  coloured. Each row gives the installed version, the recommended version, its compatibility with
  this Jahia (`Compatible`, `Needs another Jahia version`, `Unknown`, or `Jahia upgrade required` for
  the platform), the source and the advisories. Advisory links open in a new tab and are only
  rendered for `https://` URLs.
- A filter: **Jahia advisories only** (default) or **All, including reports by module owners**.
  Owner reports are versions that a module developer flagged on the store, labelled "Reported by the
  module owner". Severity and source are chips with a text label.
- The time of the last check and of the last complete check, the store errors, a link to **Manage
  modules**, and a **Check now** button.
- An explicit state: "No known security update for this Jahia" only when every store returned a
  security feed this module understands. When a feed is missing (HTTP 404), unreachable, or in an
  unknown format, the page says "Security feed unavailable" (or that the list may be incomplete),
  never that the platform is up to date.

## Banner

Users with `adminTemplates` also get a small notice, on every page of the Jahia UI (the `/jahia/...` app
shell), for example "Security updates of high severity or above concern this Jahia: 3.", with a link to
the Security updates page.

The server decides whether it is shown, from the current report and the `banner*` settings (see
[Configuration](#configuration)), and exposes the decision as `Query.securityUpdates.banner`:

- `count` is the number of finding rows whose severity is at least `bannerMinimumSeverity` and whose
  source is a Jahia advisory, or an owner report when `bannerIncludeOwnerReports=true`. A finding of
  unknown severity is never counted.
- `components` lists the component of each counted row, sorted (one entry per row).
- `visible` is `bannerEnabled && count > 0`.
- `backgroundColor`, `textColor`, `dismissPolicy`, `dismissHours` are the validated presentation settings,
  and `username` the current Jahia user (for the per-user dismissal key).

A configuration change applies on the next query, without waiting for a check. The browser only renders
the decision: it never filters findings itself. Any error (network, permission, unexpected answer) leaves
the UI unchanged. Users without `adminTemplates` never get it: the banner query is not even sent for them.

**Colours.** The background and text colours are configurable (`#RRGGBB`); their contrast ratio must be at
least 7:1 (WCAG 2.2 AAA, relative luminance formula). The link, the button border and the 3px focus ring
use the text colour, so they keep that contrast against the background (the focus ring needs 3:1).

**Dismissal** (`bannerDismissPolicy`; button or Escape):

| Policy | Hidden until | Stored in |
|---|---|---|
| `SESSION` (default) | the browser session ends | `sessionStorage["security-updates-banner-dismissed"]` = components key |
| `HOURS` | `bannerDismissHours` hours have passed | `localStorage["security-updates-banner-dismissed:<user>"]` = `{"key", "at"}` |
| `UNTIL_CHANGE` | the counted components change | `localStorage["security-updates-banner-dismissed:<user>"]` = `{"key", "at"}` |
| `NEVER` | (no Dismiss button) | nothing |

The components key is the sorted `components` list joined with `|`: with every policy but `NEVER`, the
notice comes back as soon as it changes. The local storage key names the Jahia user, so a shared browser
does not hide the notice from another administrator. Any storage failure (blocked, full, corrupt record)
shows the notice.

## Data sources

For each store base URL `B`:

| Document | Use |
|---|---|
| `B/contents/modules-repository.securityUpdates.json` | Security feed, `schemaVersion` 1: `advisories[]` (`id`, `title`, `severity`, `cvss`, `cves`, `url`, `published`, `summary`, `fixes[] {component, groupId, fixedVersions}`) and `flaggedVersions[]` (`groupId`, `name`, `version`, `severity`, `references`, `note`). |
| `B/contents/modules-repository.moduleList.json` | Module catalogue already read by Jahia's module manager; gives the minimum Jahia version (`requiredVersion`, `version-` prefix removed) of every module version. |

The stores are, in order:

1. the stores configured in the module manager (`jnt:forgeServerSettings` nodes under
   `/settings/forgesSettings`, with their `j:url`, `j:user`, `j:password`, read with a system
   session) when `useForgeSettings=true`;
2. otherwise, or when none is configured, `defaultStoreUrl`;
3. plus every URL of `extraFeedUrls`, read as a security feed only (no catalogue, no credentials).

Responses are handled as follows:

| Response | Reported as |
|---|---|
| 200 with `schemaVersion` 1 | feed used |
| 404 | `NOT_PUBLISHED`: the store does not publish security data |
| 503 (`{"error": ...}`) and any other status | `UNAVAILABLE`, with the status and the store message |
| 401 / 403 | `UNAVAILABLE`, "check the store credentials" |
| 3xx | `UNAVAILABLE`: redirects are not followed, so credentials never reach another host |
| unknown `schemaVersion`, invalid JSON | `NOT_UNDERSTOOD`: fails closed |
| catalogue failure | `CATALOGUE_UNAVAILABLE`: compatibility becomes `Unknown`, the feed is still used |
| schema 1 feed with entries it cannot use | `NOT_UNDERSTOOD` (count of ignored entries); the rest of the feed is used |

When a store fails, the data last read from it is kept and the error says so; the report status is
then `PARTIAL`. The report status is `OK` only when every configured store returned a feed this module
understands, `UNAVAILABLE` when no feed at all is available.

## How findings are computed

For each advisory fix whose `component` is an installed module (bundle symbolic name, and same
`groupId` when the feed gives one) or `jahia-core` (the running `Jahia.VERSION`), with X the installed
version and the *line* of a version being its major for a module and `major.minor.servicePack` for
`jahia-core`:

- X is **not affected** when a fixed version on X's line is <= X, or when X >= every fixed version.
- Otherwise X is affected, and the recommended version is the smallest fixed version on X's line that
  is above X, else the smallest fixed version above X.

Examples, fixes `[2.24.0, 3.10.0]`: 2.20.0 -> 2.24.0; 2.30.0 not affected; 3.5.0 -> 3.10.0; 1.4.0 -> 2.24.0;
4.0.0 not affected. For `jahia-core` with fixes `[8.2.3.7, 8.1.9.3]`: 8.1.9.1 -> 8.1.9.3; 8.2.2.1 -> 8.2.3.7.

An owner-flagged version V affects X when they share name (and `groupId` when both are known) and
major, and X < V.

Findings are grouped per component and source (Jahia advisory / owner report): one row with the
highest severity and every advisory. The row's recommended version is one at which no advisory of the
row applies any more (the candidate is raised until it is stable), so installing it fixes them all.

Compatibility of a module recommendation mirrors the module manager (`ForgeService`): compatible when
the catalogue's required Jahia version is <= the running version and has the same major; unknown when
the version is not in any catalogue. `jahia-core` findings read "Jahia upgrade required".

Versions are compared with Jahia's `org.jahia.utils.comparator.VersionComparator` (Maven
`ComparableVersion` ordering). A version that does not start with a number, is longer than 64
characters or has a segment longer than 9 digits is "malformed": a malformed fixed version is ignored,
a fix entry with no well-formed version is ignored and reported; a malformed installed version (or
running Jahia version) is reported as `MALFORMED_VERSION` and that component is not checked. The rest of
the report is still built, and its status is `PARTIAL`, not `OK`.

Each new finding is logged once at WARN level (`org.jahia.community.securityupdates`).

## Configuration

PID `org.jahia.community.securityupdates`, editable in `karaf/etc/org.jahia.community.securityupdates.cfg`
or in the OSGi configuration UI (labels in English and French). The shipped file starts with
`# default configuration - won't be overridden`, so your changes survive restarts and redeployments.

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | `false` stops the checks; the page then says the check is disabled. |
| `refreshIntervalHours` | `24` | Hours between two scheduled checks (1 to 720). |
| `useForgeSettings` | `true` | Read the stores of the module manager (`/settings/forgesSettings`). |
| `extraFeedUrls` | (empty) | Comma-separated full URLs of additional security feeds. |
| `defaultStoreUrl` | `https://store.jahia.com` | Store used when no store is configured in the module manager. |
| `bannerEnabled` | `true` | Show the notice in the Jahia UI (see [Banner](#banner)). The page is not affected. |
| `bannerMinimumSeverity` | `HIGH` | Lowest severity the notice counts: `LOW`, `MEDIUM`, `HIGH` or `CRITICAL`. Any other value means `HIGH`, with one WARN in the log. |
| `bannerIncludeOwnerReports` | `true` | Count owner-reported findings in the notice, not only Jahia advisories. |
| `bannerBackgroundColor` | `#1B1B1B` | Notice background, `#RRGGBB`. |
| `bannerTextColor` | `#FFFFFF` | Notice text (also link, button and focus ring), `#RRGGBB`. An invalid colour, or a pair below 7:1, means the default pair, with one WARN. |
| `bannerDismissPolicy` | `SESSION` | `SESSION`, `HOURS`, `UNTIL_CHANGE` or `NEVER` (see [Banner](#banner)). Any other value means `SESSION`, with one WARN. |
| `bannerDismissHours` | `24` | Hours a dismissal lasts with `HOURS` (1 to 720). |

### Settings UI

The Security updates page has a **Settings** section that edits every key above with Moonstone fields:
switches, number and text inputs, native selects for the severity and the dismissal (Moonstone's Dropdown
names its list after the selected value rather than after the field), native colour pickers paired with
Moonstone hex inputs, a help text per field, and a live preview of the notice with the computed contrast
ratio. **Save settings** sends the whole set; **Reset to defaults**
writes the default values. Every rule is checked server-side (the configuration fallbacks above, plus the
store URL rules, the 1-720 ranges and the 7:1 contrast): an invalid set is refused with an error per
field, and nothing is written. Errors are shown next to each field (`aria-invalid`, `aria-describedby`),
summarised in an alert, and the first invalid field gets the focus.

**Contrast suggestions.** When the two colours are below 7:1, the form proposes, live, a text colour
that keeps the background and a background colour that keeps the text, each with a swatch, its value,
its ratio and a **Use this text colour #xxxxxx** / **Use this background colour #xxxxxx** button that
fills the field (announced in the status region; save to keep it). A suggestion keeps the hue and
saturation of the colour it changes and moves its HSL lightness towards black or white by the smallest
step (in thousandths) that reaches 7:1, checked after rounding to `#RRGGBB`. HSL lightness 0 and 1 are
black and white whatever the hue, so when no lightness passes there is no suggestion for that side: a mid
grey such as `#777777` has no colour reaching 7:1 against it. When neither side has one, the form offers
the default colours (`#1B1B1B` / `#FFFFFF`). The server computes the same suggestions (one algorithm in
Java and JavaScript, pinned by a shared table of cases): a `LOW_CONTRAST` error of `updateSettings`
carries `suggestions { field value ratio }` and its message ends with, for example, "Suggested: text
#A6A6A6 (7.07:1) or background #C1C1C1 (7.01:1)."

The settings are written through `ConfigurationAdmin` (configuration `org.jahia.community.securityupdates`,
location `?`, every key at once, keeping `felix.fileinstall.filename`). How it persists, from the Jahia
sources (`jahia-private`):

- Felix FileInstall (`org.apache.felix.fileinstall` 3.7.4-jahia1, `ConfigInstaller.doConfigurationEvent`)
  writes a CM update back to the file named by `felix.fileinstall.filename`, through its
  `TypedProperties`, which keeps the comments and the first line of the existing file.
- The module extender (`jahiamodule-extender` `Activator.checkConfigurations`) copies the module's
  `META-INF/configurations/*.cfg` to `karaf/etc` only when the file does not exist there, or when the module's
  file has no line starting with `# default configuration`. The shipped file has that marker, so a
  redeployment or a restart keeps the saved values.
- The config manager (`config-manager` `ConfigServiceImpl`) classifies such a file as `MODULE_DEFAULT`,
  and stores `karaf/etc` in the JCR on each configuration event when its auto-save is on (restored with
  the `USER` files on demand).

When no configuration exists yet, the module writes the `.cfg` itself (with the marker) and points
FileInstall at it.

## GraphQL API

One field on `Query` and one on `Mutation`; every nested field requires `adminTemplates`:

```graphql
query {
  securityUpdates {
    report {
      status            # NOT_CHECKED | DISABLED | OK | PARTIAL | UNAVAILABLE
      checkedAt         # ISO-8601
      lastSuccess       # ISO-8601, last check where every feed was read and understood
      jahiaVersion
      errors { storeUrl kind message }
      findings {
        component groupId installedVersion recommendedVersion
        compatibility     # COMPATIBLE | INCOMPATIBLE | UNKNOWN | JAHIA_UPGRADE
        requiredJahiaVersion
        severity          # CRITICAL | HIGH | MEDIUM | LOW | UNKNOWN
        source            # ADVISORY | OWNER_REPORTED
        advisories { id title url cves severity }
      }
    }
  }
}

query {
  securityUpdates {
    banner { visible count minimumSeverity components backgroundColor textColor dismissPolicy dismissHours username }
    settings { enabled refreshIntervalHours useForgeSettings defaultStoreUrl extraFeedUrls bannerEnabled
               bannerMinimumSeverity bannerIncludeOwnerReports bannerBackgroundColor bannerTextColor
               bannerDismissPolicy bannerDismissHours contrastRatio }
  }
}

mutation {
  securityUpdates { refresh { status checkedAt findings { component severity } } }
}

# The input type is InputSecurityUpdatesSettingsValues (graphql-java-annotations prefixes input types with "Input").
# Every field is required; enumerations and colours are strings, validated server-side.
mutation ($input: InputSecurityUpdatesSettingsValues!) {
  securityUpdates {
    updateSettings(input: $input) { ok errors { field code message } settings { refreshIntervalHours } }
  }
}

mutation {
  securityUpdates { resetSettings { ok settings { bannerDismissPolicy } } }
}
```

`updateSettings` errors are `{ field code message suggestions { field value ratio } }`; codes: `MISSING`, `INVALID_BOOLEAN`, `INVALID_NUMBER`, `OUT_OF_RANGE`, `INVALID_URL`,
`INVALID_VALUE`, `INVALID_COLOR`, `LOW_CONTRAST`, and `WRITE_FAILED` (field null) when the configuration
cannot be written.

`refresh` runs a check unless the last one is less than 30 seconds old. Advisory `url`s are returned
only when they start with `https://`.

## Security

- Store URLs must be absolute `http(s)` URLs naming a host, without user information, query or
  fragment; other URLs are reported as `INVALID_URL` and never fetched.
- HTTP goes through Jahia's `HttpClientService` (Jahia proxy settings apply) with a 10 s connect
  timeout, a 30 s read timeout, a 60 s deadline per body, no redirects, and size limits (2 MB for the
  feed, 16 MB for the catalogue, 4 KB kept of an error body).
- Store credentials are sent only to the store they belong to, as Basic authentication, and only over
  https: for an `http://` store they are not sent and an `INVALID_URL` error says so. They are never
  logged nor returned; transport errors are reported by exception type only.
- All feed text is untrusted: control characters are removed and lengths bounded on parsing; the UI
  renders it as React text (no `dangerouslySetInnerHTML`).

## Limits

- Only started modules are checked (the active version of each module), not installed-but-stopped
  versions.
- The module name in the feed must be the module's bundle symbolic name.
- Compatibility is known only for versions listed in a store catalogue; a feed-only source
  (`extraFeedUrls`) gives no compatibility.
- `store.jahia.com` does not publish the security feed yet: until it does, the report is
  `UNAVAILABLE` with a `NOT_PUBLISHED` error.
- **Check now** (the `refresh` mutation) runs the check in the GraphQL request: with several slow or
  unreachable stores it can take minutes (each request is bounded by the timeouts above).
- Jahia's HTTP client refuses hosts that resolve to an internal address (loopback, private networks),
  and the report then shows the store as unavailable (`SsrfBlockedException`). For a store on a private
  network, add its host name to `jahia.httpClient.allowedInternalHosts` in `jahia.properties` (or the
  `jahia_cfg_jahia_httpClient_allowedInternalHosts` environment variable in Docker).

## Build

```bash
mvn clean install        # Java build + unit tests, then yarn lint, jest and the production webpack build
```

The frontend-maven-plugin installs its own Node and Yarn 1 under `node/`; use `./node/yarn/dist/bin/yarn`
for `yarn lint`, `yarn test` or `yarn build` rather than a global Yarn.

## Tests

- Java: JUnit 5, AssertJ, Mockito (`src/test/java`), coverage report in `target/site/jacoco`.
- JavaScript: Jest and Testing Library (`src/javascript/**/__tests__`).
- End to end: Cypress harness under `tests/` (the `@jahia/cypress` layout of the sibling modules):
  `cd tests && bash ci.build.sh && bash ci.startup.sh`. It needs a `tests/.env.example` (or `.env`)
  providing `MANIFEST`, `TESTS_IMAGE`, `JAHIA_IMAGE`, `MODULE_ID`, `JAHIA_URL`, `SUPER_USER_PASSWORD`,
  `JAHIA_LICENSE`, and the other variables of the sibling modules' harness.
