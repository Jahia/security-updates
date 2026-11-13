# security-updates

A Jahia 8.2 module that lists, in the Jahia administration, the installed components (Jahia core and
modules) for which the Jahia Store publishes updates that fix security issues, much like
`dnf updateinfo list --security`. For each component it shows the installed version, the recommended
version, whether that version runs on this Jahia, the severity and the advisories (id, title, link,
CVEs).

> **Status: the Jahia Store does not publish the security feed yet.** Until it does, the page shows
> **Not available yet** and the report status is `UNAVAILABLE` with a `NOT_PUBLISHED` error. This is
> expected. There is nothing to configure and nothing is broken. The module starts finding updates by
> itself on the first check after the store publishes the feed. In the meantime you can point it at a
> store or a feed of your own (see [Data sources](#data-sources)).

## Requirements

| Need | Value |
|---|---|
| Jahia | 8.2.3.7 or later (`Jahia-Required-Version` in the module manifest, from the `jahia-modules` parent in `pom.xml`) |
| Modules | `serverSettings` and `graphql-dxm-provider` (`Jahia-Depends`), both part of a standard Jahia |
| Network | Outbound HTTPS from every Jahia node to the stores (`store.jahia.com` by default), through the Jahia proxy settings if any |
| To see the page, the banner and the report | The `adminTemplates` permission (the permission that gives access to module management) |
| To change the settings | The `admin` permission, held by the server administrators. A user with only `adminTemplates` (for example the web designer role) sees the settings read-only. |

Jahia 8.2.3.7 is required because it screens every outbound request of the Jahia HTTP client against
internal addresses at connect time (see [Security](#security)). Earlier 8.2 releases do not do it.

## Installation

- Download `security-updates-<version>.jar`: _download link to add when the first release is published_.
  Or build it yourself (see [Build](#build)).
- In Jahia, go to **Administration > Server settings > System components > Modules**, upload the JAR
  and check that the module is started.
- Open **Administration > Server settings > System components > Security updates**.

The first check runs about one minute after the module starts. **Check now** starts one at once.

_Screenshots to add: the Security updates page with findings, the settings section, the banner._

## What the page shows

- A status chip: **Up to date**, **Updates needed**, **Partly checked**, **No security data**,
  **Not available yet**, **Turned off** or **Not checked yet**. The chip is worded, never colour only.
- A table of the components with security updates, most severe first. Each row gives the component, its
  severity, the installed version, the recommended version, its compatibility with this Jahia
  (`Compatible`, `Needs another Jahia version`, `Unknown`, or `Jahia upgrade required` for the
  platform), the source, the advisories and what to do (a link to **Manage modules** for a module, to the
  Jahia upgrade documentation for `jahia-core`). Advisory links open in a new tab and are rendered only
  for `https://` URLs.
- A filter: **Jahia advisories only** or **All, including reports by module owners**, with a count each.
  The page opens on **All** when owner reports exist. Owner reports are versions that a module developer
  flagged on the store. They are not Jahia advisories.
- The problems found during the last check, per store, in plain language, with the technical text
  under **Technical details**.
- The time of the last check and of the last complete check, and when the next one is due.
- "No known security update for this Jahia" appears only when the report is `OK`, recent and empty.
  When a feed is missing, unreachable, in an unknown format, or the report is stale, the page says so
  and never says that the platform is up to date.

## Banner

Users with `adminTemplates` also get a notice (the banner) at the top or bottom of the Jahia UI, for example
"3 components need a security update (high severity or above).", with a link to the Security updates page.

The server decides whether it is shown, from the current report and the `banner*` settings (see
[Configuration](#configuration)), and exposes the decision as `Query.securityUpdates.banner`:

- `count` is the number of **distinct components** that have at least one counted finding. A counted
  finding has a severity at least `bannerMinimumSeverity` and is a Jahia advisory, or an owner report when
  `bannerIncludeOwnerReports=true`. A finding of unknown severity is never counted.
- `components` lists those components, distinct and sorted.
- `dismissalKeys` lists one `<component>|<advisory id>` per counted advisory, distinct and sorted.
- `visible` is `bannerEnabled && count > 0`.
- `backgroundColor`, `textColor`, `dismissPolicy`, `dismissHours` are the validated presentation settings,
  and `username` the current Jahia user.

The browser only renders the decision. It never filters findings itself. A configuration change applies on
the next query, without waiting for a check. Any error (network, permission, unexpected answer) leaves the
UI unchanged. Users without `adminTemplates` never see it.

The banner reads the server again every 15 minutes, when the browser tab becomes visible again, and when the
page changes the report or the settings. It is hidden on the Security updates page itself.

**Colours.** The background and text colours are configurable (`#RRGGBB`). Their contrast ratio must be at
least 7:1 (WCAG 2.2 AAA, relative luminance formula). The link, the button border and the 3px focus ring
use the text colour, so they keep that contrast. The settings form and the server suggest a colour that
reaches 7:1 when a pair does not.

**Dismissal** (`bannerDismissPolicy`; the **Hide this banner** button or Escape):

| Policy | Hidden until | Stored in |
|---|---|---|
| `SESSION` (default) | the browser tab is closed | `sessionStorage` of the tab: a new tab shows the banner again |
| `HOURS` | `bannerDismissHours` hours have passed | `localStorage`, key with a hash of the user name |
| `UNTIL_CHANGE` | a new advisory or a new component is counted | `localStorage`, key with a hash of the user name |
| `NEVER` | (no dismiss button) | nothing |

The identity of a dismissal is the set of `dismissalKeys`. Whatever the policy except `NEVER`, the banner
comes back as soon as a key is not in the dismissed set: a new advisory, even for a component that was
already counted, or a new component. It stays hidden when keys only disappear (an update was installed).
Any storage failure (blocked, full, corrupt record) shows the banner.

## Data sources

For each store base URL `B`:

| Document | Use |
|---|---|
| `B/contents/modules-repository.securityUpdates.json` | The security feed (see [Feed format](#feed-format)). |
| `B/contents/modules-repository.moduleList.json` | The module catalogue already read by Jahia's module manager. It gives the minimum Jahia version of every module version. It is not read when the store has no security feed. |

**HTTPS only.** Every store and feed URL must be an absolute `https://` URL naming a host, in ASCII, without
user information, query or fragment. A plain `http://` URL is refused everywhere: in `defaultStoreUrl`, in
`extraFeedUrls` and in the stores of the module manager. It is never fetched and the report shows an
`INVALID_URL` error with the detail `NOT_HTTPS`. A feed read over http could be altered on the way and
hide a vulnerability.

**Form of the store URL.** `B` is the base of the store: the module appends `/contents/...` to it. The
default is `https://store.jahia.com`. A store such as `https://storedev.jahia.com` works the same way. The
module manager's default store is registered with a longer base (`https://store.jahia.com/en/sites/private-app-store`):
the module appends the same paths to whatever base the module manager holds. Check that the feed exists
at the resulting address before relying on it: it is not verified that the store serves the feed below
that longer base.

The stores read, in order:

1. the stores of the module manager, when `useForgeSettings=true`, with their user and password
   (Basic authentication, sent only to that store, over https, no redirects). Where they are kept depends on the
   module manager version:
   - module manager 2.14 and later: OSGi factory configurations
     `karaf/etc/org.jahia.modules.modulemanager.forge.configuration-<name>.cfg` (keys `url`, `user`, `password`);
   - module manager 2.12 and earlier: JCR nodes `jnt:forgeServerSettings` under `/settings/forgesSettings`.

   When neither can be read, the report shows `UNAVAILABLE` with the detail `FORGE_SETTINGS_UNREADABLE`
   (status `PARTIAL`), and the default store is used instead;
2. otherwise, or when none is configured, `defaultStoreUrl`;
3. plus every URL of `extraFeedUrls` (at most 10), read as a security feed only: no catalogue, no credentials.

At most 20 stores and feeds are read in one check. The Jahia Store (`store.jahia.com`) is read first.

**Trust boundary.** The stores of the module manager can be edited by every user who holds `adminTemplates`
in the module manager. That is the module manager's own trust boundary, not this module's. Those users
decide which stores are read and with which credentials. If that matters to you, set `useForgeSettings=false`
and configure `defaultStoreUrl` (editable by server administrators only).

**Same advisory in several stores.** When two feeds publish an advisory with the same `id`, the one of the
official Jahia Store (`store.jahia.com`) wins. Between other feeds the first one in the order above wins.
A private store or an extra feed cannot replace an advisory of the Jahia Store.

## When checks run

- The first check runs about one minute after the module starts, then every `refreshIntervalHours`
  (default 24).
- A change of `enabled`, `refreshIntervalHours`, `useForgeSettings`, `extraFeedUrls` or `defaultStoreUrl`
  restarts the schedule: the next check runs about one minute later. A change of the `banner*` settings
  does not start a check. It applies on the next query.
- **Check now** (the `refresh` mutation) never blocks: it starts a check in the background and returns
  at once, and the page shows the progress and polls the report. It starts nothing when a check is
  already running, when checks are disabled, or when the previous check started or ended less than
  `onDemandMinIntervalSeconds` ago (30 seconds by default, a hidden key, see
  [Configuration](#configuration)). The page then says that no new check was started.
- One check runs at a time. A scheduled check that falls due during another one is skipped.
- **Stale report.** A report whose last check is older than twice the refresh interval is stale: the
  scheduled check may have stopped. Its status is then at best `PARTIAL`, it carries a `STALE` error, and the
  page does not say "up to date". `nextCheckAt` tells when the next scheduled check is due (null when the
  check is disabled).

## Cluster

- The settings are written to `karaf/etc/org.jahia.community.securityupdates.cfg`. Jahia shares the files
  of `karaf/etc` across the cluster, so a change saved on one node reaches every node, and each one
  applies it and restarts its own schedule.
- The report is not shared. Each node checks the stores on its own, keeps the report **in memory**, and
  answers with its own report. The page shows the node that answered your request. After a restart a
  node has no report until its first check (one minute later).
- Each node needs outbound HTTPS to the stores.

## Report status and errors

The status of the report:

| Status | Meaning |
|---|---|
| `NOT_CHECKED` | No check has run yet. |
| `DISABLED` | `enabled=false`. |
| `OK` | Every configured store returned a feed this module understands, every installed component could be compared, and the report is not stale. |
| `PARTIAL` | A feed is usable but the check is incomplete: a store failed (its last good data is kept and the error says so), entries were skipped, a version could not be compared, the module manager stores could not be read, or the report is stale. Findings shown are real, a missing finding is not proof of safety. |
| `UNAVAILABLE` | No security feed is available at all. |

Only a `CATALOGUE_UNAVAILABLE` error does not prevent `OK`: compatibility is then `Unknown` and the feed is
still used. Every other error kind makes the report `PARTIAL` or `UNAVAILABLE`.

Errors (`report.errors { storeUrl kind detail message }`). The `message` is a short English technical text
made of status codes and this module's own words. It never repeats text received from a store. The page
translates the kind and the detail.

| Kind | When | Details |
|---|---|---|
| `NOT_PUBLISHED` | The store answers 404 for the feed: it does not publish security data. | |
| `UNAVAILABLE` | The store cannot be read, or the check could not run. Also when more than 20 stores and feeds are configured. | `SSRF_BLOCKED` (Jahia refused to connect, see [Security](#security)), `TIMEOUT`, `CONNECTION` (DNS, TLS, refused, reset), `REDIRECT` (not followed), `AUTH` (401 or 403), `HTTP_STATUS` (any other error status), `TOO_LARGE`, `FORGE_SETTINGS_UNREADABLE`, `CHECK_FAILED` (see the Jahia log) |
| `NOT_UNDERSTOOD` | The feed was received but not understood, or some entries or references were ignored. | `INVALID_JSON`, `SCHEMA_UNSUPPORTED` (the feed's `schemaVersion` is not 1: a newer module may understand it), `ENTRIES_SKIPPED` |
| `INVALID_URL` | A configured URL is refused and not fetched. | `NOT_HTTPS` for a plain http URL |
| `CATALOGUE_UNAVAILABLE` | The module catalogue could not be read or was truncated. Non blocking. | as for `UNAVAILABLE`, `ENTRIES_SKIPPED` |
| `MALFORMED_VERSION` | An installed version (or the running Jahia version) cannot be compared: that component is not checked. | |
| `NO_STORE` | No store is configured (for example an empty `defaultStoreUrl` and no module manager store). | |
| `STALE` | The last check is older than twice the refresh interval. | |

When a store fails, the data last read from it is kept and the error says so.

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

Findings are grouped per component and source (Jahia advisory or owner report): one row with the
highest severity and every advisory. The row's recommended version is one at which no advisory of the
row applies any more (the candidate is raised until it is stable), so installing it fixes them all.

Compatibility of a module recommendation mirrors the module manager (`ForgeService`): compatible when
the catalogue's required Jahia version is <= the running version and has the same major; unknown when
the version is not in any catalogue. `jahia-core` findings read "Jahia upgrade required".

Versions are compared with Jahia's `org.jahia.utils.comparator.VersionComparator` (Maven
`ComparableVersion` ordering). A version that does not start with a number, is longer than 64
characters or has a segment longer than 9 digits is "malformed". A malformed fixed version is ignored. A fix
entry with no well-formed version is ignored and reported. A malformed installed version (or running Jahia
version) is reported as `MALFORMED_VERSION` and that component is not checked. The rest of the report is
still built, and its status is `PARTIAL`.

Each new finding is logged once at WARN level (`org.jahia.community.securityupdates`), with the number of
references and no remote text. It is logged again if it disappears and comes back.

## Feed format

A feed is a JSON object. A minimal valid example:

```json
{
  "schemaVersion": 1,
  "advisories": [
    {
      "id": "JSA-2026-0001",
      "title": "Cross-site scripting in the example module",
      "severity": "high",
      "cves": ["CVE-2026-12345"],
      "url": "https://example.com/advisories/JSA-2026-0001",
      "fixes": [
        {"component": "example-module", "groupId": "org.example", "fixedVersions": ["2.4.1", "3.1.0"]}
      ]
    }
  ],
  "flaggedVersions": [
    {
      "name": "example-module",
      "version": "2.3.0",
      "severity": "medium",
      "references": ["CVE-2026-54321", "https://example.com/notes/2.3.0"],
      "note": "Known issue fixed in 2.4.0"
    }
  ]
}
```

Rules:

- `schemaVersion` must be the integer `1`. Anything else (missing, a string, `1.0`, another number)
  fails closed: `NOT_UNDERSTOOD` with the detail `SCHEMA_UNSUPPORTED`. The document must be a JSON object, nested
  at most 64 levels.
- `advisories[]`: `id` (required), `fixes[]` (required, at least one valid fix), `title` (defaults to the
  id), `severity`, `cvss`, `cves[]`, `url`, `published`. The `summary` field is not read.
  - A fix needs `component` (the bundle symbolic name of the module, or `jahia-core`) and `fixedVersions[]`
    with at least one well-formed version. `groupId` is optional.
- `flaggedVersions[]`: `name` and `version` are required. `groupId`, `severity`, `references[]` and
  `note` are optional.
- `severity` is `CRITICAL`, `HIGH`, `MEDIUM` or `LOW`, in any case. Anything else, or none, is `UNKNOWN`. A finding
  of unknown severity is listed but never counted by the banner.
- Entries that cannot be used (a missing required field, no valid fix, not an object) are skipped and
  counted: `NOT_UNDERSTOOD` with the detail `ENTRIES_SKIPPED`, status `PARTIAL`. Unknown keys are ignored.
- Limits: 2 MB per feed; at most 10,000 advisories and 10,000 flagged versions; at most 100 fixes per
  advisory and 100 items per list; text values are cut at 500 characters, ids and CVEs at 100, URLs at 2,048.
  Control characters are removed. An `url` that is not `http(s)` is dropped, and only `https://` links are
  shown.
- A report lists at most 50 advisory references per finding and about 2,000 in all, and at most 500 fixed
  versions per component are considered. When a cap applies, the report says so (`ENTRIES_SKIPPED`).
- **Owner references.** In an owner flag, each reference that looks like `CVE-YYYY-NNNN` or `JSA-YYYY-NNNN`
  is shown in the CVE list of the finding (a JSA id is shown there too). The first reference that starts with
  `https://` is the link. The id of an owner report is the references joined with commas, cut at 200
  characters (or `owner:<name>@<version>` when there is no reference); the note is its title.
- The same advisory `id` in several feeds: see [Data sources](#data-sources).

## Configuration

PID `org.jahia.community.securityupdates`, editable in `karaf/etc/org.jahia.community.securityupdates.cfg`
or in the OSGi configuration UI (labels in English and French), or from the Settings section of the page
(server administrators). The shipped file starts with `# default configuration - won't be overridden`, so your
changes survive restarts and redeployments.

| Key | Default | Range / values | Description |
|---|---|---|---|
| `enabled` | `true` | `true`, `false` | `false` stops the checks; the page then says the check is disabled. |
| `refreshIntervalHours` | `24` | 1 to 720 | Hours between two scheduled checks. |
| `useForgeSettings` | `true` | `true`, `false` | Read the stores of the module manager. |
| `extraFeedUrls` | (empty) | at most 10 https URLs | Full URLs of additional security feeds, separated by commas or spaces. |
| `defaultStoreUrl` | `https://store.jahia.com` | https base URL, or empty | Store used when no store is configured in the module manager, or when `useForgeSettings=false`. Empty means none. |
| `bannerEnabled` | `true` | `true`, `false` | Show the banner in the Jahia UI. The page is not affected. |
| `bannerMinimumSeverity` | `HIGH` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | Lowest severity the banner counts. |
| `bannerIncludeOwnerReports` | `true` | `true`, `false` | Count owner-reported findings in the banner, not only Jahia advisories. |
| `bannerBackgroundColor` | `#1B1B1B` | `#RRGGBB` | Banner background. |
| `bannerTextColor` | `#FFFFFF` | `#RRGGBB` | Banner text (also link, button and focus ring). The pair needs a contrast of at least 7:1. |
| `bannerDismissPolicy` | `SESSION` | `SESSION`, `HOURS`, `UNTIL_CHANGE`, `NEVER` | See [Banner](#banner). |
| `bannerDismissHours` | `24` | 1 to 720 | Hours a dismissal lasts with `HOURS`. |
| `onDemandMinIntervalSeconds` | `30` | 0 to 3600 | Hidden key, not shown in the settings page. Seconds between the starts of two **Check now** checks. The e2e tests set it to 0. The page never changes it. |

**A `.cfg` file edited by hand is read leniently**, so a typo never stops the module:

- A missing key takes its default, silently.
- A boolean or a number that cannot be read takes its default, with one WARN in the log.
- A number out of range is clamped to the nearest limit (for example `refreshIntervalHours=0` becomes 1), with
  one WARN.
- `extraFeedUrls` accepts commas or spaces. Beyond 10 URLs only the first 10 are used, with one WARN.
- An empty or unknown `bannerMinimumSeverity` means `HIGH`, an empty or unknown `bannerDismissPolicy` means
  `SESSION`, and an invalid colour or a pair below 7:1 means the default pair. Only the unknown or invalid
  values give a WARN, an empty value does not.
- An empty `defaultStoreUrl` means no default store: with no module manager store either, the report
  shows `NO_STORE`.
- A URL that is not https is not an error of the file: it is reported at check time (`INVALID_URL`).

The warnings are logged once per configuration change, by `org.jahia.community.securityupdates`.

The settings form (Settings section of the page, server administrators only) is stricter than the file:
**Save settings** sends the whole set, the server checks every rule (https URLs, at most 10 feeds, ranges, the
7:1 contrast) and refuses an invalid set with an error per field, so nothing is written. **Reset to defaults**
asks for confirmation, then writes the default values. For a user without `admin` the section is read-only.
Saving takes effect on the next check, about one minute later.

## GraphQL API

One field on `Query` and one on `Mutation`. `report`, `banner`, `settings` and `refresh` require
`adminTemplates`. `updateSettings` and `resetSettings` require `admin`: a user without it gets the GraphQL
error "Permission denied". The full schema is in the GraphQL schema of the running Jahia
(`/modules/graphql`).

```graphql
query {
  securityUpdates {
    report {
      status            # NOT_CHECKED | DISABLED | OK | PARTIAL | UNAVAILABLE
      inProgress        # a check runs now on this node
      stale             # last check older than twice the refresh interval
      checkedAt         # ISO-8601
      lastSuccess       # ISO-8601, last check where every feed was read and understood
      nextCheckAt       # ISO-8601, null when disabled or not scheduled
      jahiaVersion
      errors { storeUrl kind detail message }
      findings {
        component groupId installedVersion recommendedVersion
        compatibility     # COMPATIBLE | INCOMPATIBLE | UNKNOWN | JAHIA_UPGRADE
        requiredJahiaVersion
        severity          # CRITICAL | HIGH | MEDIUM | LOW | UNKNOWN
        source            # ADVISORY | OWNER_REPORTED
        advisories { id title url cves severity }
      }
    }
    banner { visible count minimumSeverity components dismissalKeys backgroundColor textColor dismissPolicy dismissHours username }
    settings { canEdit enabled refreshIntervalHours useForgeSettings defaultStoreUrl extraFeedUrls bannerEnabled
               bannerMinimumSeverity bannerIncludeOwnerReports bannerBackgroundColor bannerTextColor
               bannerDismissPolicy bannerDismissHours contrastRatio }
  }
}

# Non blocking: starts a check in the background, returns the current report with inProgress true.
# Poll `report` until inProgress is false.
mutation {
  securityUpdates { refresh { status inProgress checkedAt } }
}

# The input type is InputSecurityUpdatesSettingsValues (graphql-java-annotations prefixes input types with "Input").
# Every field is required. Variables: {"input": {"enabled": true, "refreshIntervalHours": 24, ...}}
mutation ($input: InputSecurityUpdatesSettingsValues!) {
  securityUpdates {
    updateSettings(input: $input) { ok errors { field code message suggestions { field value ratio } } settings { refreshIntervalHours } }
  }
}

mutation {
  securityUpdates { resetSettings { ok settings { bannerDismissPolicy } } }
}
```

Example of variables for `updateSettings`:

```json
{
  "input": {
    "enabled": true, "refreshIntervalHours": 12, "useForgeSettings": true,
    "defaultStoreUrl": "https://store.jahia.com", "extraFeedUrls": "",
    "bannerEnabled": true, "bannerMinimumSeverity": "HIGH", "bannerIncludeOwnerReports": true,
    "bannerBackgroundColor": "#1B1B1B", "bannerTextColor": "#FFFFFF",
    "bannerDismissPolicy": "SESSION", "bannerDismissHours": 24
  }
}
```

Notes:

- `settings` returns the values stored in the configuration (after the lenient reading above), and
  `canEdit`, true only for users who hold `admin`. `updateSettings` and `resetSettings` return the values
  they saved.
- Field error codes of `updateSettings`: `OUT_OF_RANGE`, `INVALID_URL`, `NOT_HTTPS`, `TOO_MANY`
  (more than 10 feeds), `INVALID_VALUE`, `INVALID_COLOR`, `LOW_CONTRAST` (with `suggestions`) and
  `WRITE_FAILED` (field null) when the configuration cannot be written. The input fields are non-null, so
  `MISSING`, `INVALID_BOOLEAN` and `INVALID_NUMBER` are refused by GraphQL itself and never reach a client.
- Advisory `url`s are returned only when they start with `https://`.

## Security

- **Permissions.** Reading is for `adminTemplates`. Changing the settings is for `admin`, so that a module
  manager user cannot disable the checks, point the module at another store or hide the banner. The
  administration route of the page requires `adminTemplates`.
- **Audit log.** Every settings change is logged at INFO by `org.jahia.community.securityupdates.service.SettingsStore`:
  `Security updates settings saved by <user>: <key>: <old> -> <new>; ...`. Store credentials are not settings of
  this module and never appear in it.
- **HTTP.** All requests go through Jahia's `HttpClientService` (Jahia proxy settings apply) with a 10 s
  connect timeout, a 30 s read timeout, a 60 s deadline per body, no redirects, and size limits (2 MB for
  the feed, 4 MB for the catalogue, 4 KB kept of an error body).
- **Outbound request screening (SSRF).** Jahia 8.2.3.7 refuses, at connect time, to connect to an
  internal address (loopback, private networks, link-local, cloud metadata), even if the host name resolves
  to one later. The report then shows the store as unavailable with the detail `SSRF_BLOCKED`. The module
  has no way around it. For a store on a private network, in `jahia.properties` (or the
  `jahia_cfg_jahia_httpClient_...` environment variables in Docker):
  - `jahia.httpClient.allowedInternalHosts`: host names that may resolve to an internal address;
  - `jahia.httpClient.blockInternalAddresses`: the switch of the screening;
  - `jahia.httpClient.blockUnresolvableProxiedTargets`: whether a target that the Jahia server cannot
    resolve but a proxy could is blocked.
- **Credentials.** Store credentials are sent only to the store they belong to, as Basic authentication, only
  over https. They are never logged nor returned, and transport errors are reported by exception type only.
- **Untrusted text.** All feed text is untrusted: control characters are removed and lengths bounded on
  parsing, remote text is never echoed in error messages, and the UI renders it as React text (no
  `dangerouslySetInnerHTML`).
- **CSRF.** The GraphQL mutations rely on the Jahia security filter profile, which is on by default. Do not
  disable it.

See [SECURITY.md](SECURITY.md) to report a vulnerability.

## Limits

- Only started modules are checked (the active version of each module), not installed-but-stopped
  versions.
- The module name in the feed must be the module's bundle symbolic name.
- Compatibility is known only for versions listed in a store catalogue; a feed-only source
  (`extraFeedUrls`) gives no compatibility.
- The report is kept in memory, per node: it is empty after a restart until the first check.
- The module does not install anything. It tells you what to update.

## Upgrade

- Upload the new JAR in **Modules**. Your settings stay: the `.cfg` file in `karaf/etc` is not overwritten
  (its first line is `# default configuration - won't be overridden`). The new version runs a check one minute
  after it starts.
- A new key added by a release is read with its default until you set it.
- Starting with 1.0.0 the module requires Jahia 8.2.3.7.

## Uninstall

- Stop and undeploy the module in **Modules**.
- The file `karaf/etc/org.jahia.community.securityupdates.cfg` stays, so the settings are kept if you
  install the module again. Delete it for a clean removal.
- The banner dismissals live in the browsers (`sessionStorage`, and `localStorage` keys that start with
  `security-updates-banner-dismissed`). They hold no personal data and can stay.

## Troubleshooting

To see what a check does, in the Jahia (Karaf) console: `log:set DEBUG org.jahia.community.securityupdates.feed`,
and `log:set INFO org.jahia.community.securityupdates.feed` to go back.

| Symptom | Cause | Action |
|---|---|---|
| "Not available yet", `NOT_PUBLISHED` | The store does not publish the security feed yet. | Nothing. Wait for the store to publish it, or add an `extraFeedUrls` feed. |
| "No security data" with `UNAVAILABLE`, detail `SSRF_BLOCKED` | The store name resolves to an internal address. | Add the host to `jahia.httpClient.allowedInternalHosts` and restart Jahia. |
| Detail `CONNECTION` or `TIMEOUT` | No route, proxy, DNS or TLS problem from this node. | Check the outbound HTTPS and the proxy from the node, and the DEBUG log. |
| Detail `AUTH` | The store refused the credentials. | Fix the store user and password in the module manager. |
| Detail `REDIRECT` | The store URL redirects. Redirects are not followed. | Use the final https URL. |
| `INVALID_URL` with `NOT_HTTPS` | A store or feed uses `http://`. | Use an `https://` URL. |
| `NOT_UNDERSTOOD` with `SCHEMA_UNSUPPORTED` | The feed uses a newer `schemaVersion`. | Update this module. |
| `FORGE_SETTINGS_UNREADABLE` | The module manager stores could not be read. | See the log; set `useForgeSettings=false` to use only `defaultStoreUrl`. |
| `STALE` | No check finished within twice the interval. | Look for errors in the log, use **Check now**, check that the module is started. |
| Check now does nothing | A check is running, or one started less than `onDemandMinIntervalSeconds` ago, or checks are disabled. | Wait a minute. |
| A known vulnerable module is not listed | The feed names it differently from its bundle symbolic name, or it is stopped. | See the FAQ. |
| No banner | `bannerEnabled=false`, no counted finding, banner dismissed, user without `adminTemplates`, or you are on the Security updates page. | Check `bannerMinimumSeverity` and the dismissal policy. |
| Settings are read-only | The user holds `adminTemplates` but not `admin`. | Ask a server administrator. |
| Settings differ between nodes | The cluster did not deliver `karaf/etc` yet. | Check the cluster file synchronisation. |

## FAQ

**Does the module send my inventory to the store?** No. It only downloads two public documents from each
store (the security feed and the module catalogue) and compares them with your installed components
locally. The names and versions of your modules are never sent. The only thing sent to a store is the Basic
credential of a private store configured in the module manager.

**Why is my module not matched?** The `component` name in the feed must be the module's bundle symbolic
name (the module name shown in the **Modules** page, for example `jahia-oauth`), and the `groupId`, when the
feed gives one, must match.

**Does it work offline?** The module needs outbound HTTPS. Without it, the report is `UNAVAILABLE`.

## Build

```bash
mvn clean install        # Java build + unit tests, then yarn lint, jest and the production webpack build
```

Maven installs Node 22.20.0 and Yarn 1.22.22 under `node/` (frontend-maven-plugin) and runs the frontend
build with them. To run a frontend command yourself, after the first Maven build, use that Yarn with that
Node first on the path:

```bash
PATH="$PWD/node:$PATH" node/yarn/dist/bin/yarn lint     # or test, build:production
```

Do not use a global Yarn or Corepack: a Yarn Berry rewrites the Yarn 1 `yarn.lock`. Java 11 source
level; build with a JDK 11 or 17.

## Tests

- Java: JUnit 5, AssertJ, Mockito (`src/test/java`), coverage report in `target/site/jacoco`.
- JavaScript: Jest and Testing Library (`src/javascript/**/__tests__`), including accessibility checks.
- End to end: Cypress against a Jahia in Docker, in `tests/`. It needs Docker and Node (`npx`). On a fresh
  clone:
  1. Build the module: `mvn clean install` (the harness copies `target/*-SNAPSHOT.jar`).
  2. Create `tests/.env` from `tests/.env.example`, which holds placeholders only, and fill it with your
     values (image names, Jahia license, super user password, Nexus credentials if your images need them).
     `tests/.env` is not committed.
  3. `cd tests && bash ci.build.sh && bash ci.startup.sh`.

  The tests use a store fixture served over https with a certificate that Jahia trusts, set the hidden key
  `onDemandMinIntervalSeconds` to 0 so they never wait, run accessibility checks with `cypress-axe`, and do
  not print the super user password.

## License

MIT, see [LICENSE.txt](LICENSE.txt).
