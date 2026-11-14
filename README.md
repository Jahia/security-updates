# security-updates

A Jahia 8.2 module that lists, in the Jahia administration, the installed components (Jahia core and
modules) for which the Jahia Store publishes updates that fix security issues, much like
`dnf updateinfo list --security`. For each component it shows the installed version, the recommended
version, whether that version runs on this Jahia, the severity and the advisories (id, title, link,
CVEs).

> **Status: the Jahia Store does not publish the security feed yet.** It will publish it in a later
> version. Until then, the page shows **Not available yet** and the report status is `UNAVAILABLE` with a
> `NOT_PUBLISHED` error. This is expected. There is nothing to configure and nothing is broken. The module
> starts finding updates by itself on the first check after the store publishes the feed. In the meantime
> you can point it at a store or a feed of your own (see [Data sources](#data-sources) and
> [Hosting a feed](#hosting-a-feed)).

## Requirements

| Need | Value |
|---|---|
| Jahia | 8.2.3.7 or later (`Jahia-Required-Version` in the module manifest, from the `jahia-modules` parent in `pom.xml`) |
| Modules | `serverSettings` and `graphql-dxm-provider` (`Jahia-Depends`), both part of a standard Jahia |
| Network | Outbound HTTPS from every Jahia node to the stores (on a standard Jahia, `store.jahia.com`, the store of the module manager), through the Jahia proxy settings if any |
| To see the page, the banner and the report | The `adminTemplates` permission (the permission that gives access to module management) |
| To change the settings | The `admin` permission, held by the server administrators. A user with only `adminTemplates` (for example the web designer role) sees the settings read-only. |

Jahia 8.2.3.7 is required because it screens every outbound request of the Jahia HTTP client against
internal addresses at connect time (see [Security](#security)). Earlier 8.2 releases do not do it.

## Installation

- Get `security-updates-<version>.jar` from the Jahia Store or from the GitHub releases of the project,
  or build it yourself (see [Build](#build)).
- In Jahia, go to **Administration > Server settings > System components > Modules**, upload the JAR
  and check that the module is started.
- Open **Administration > Server settings > System components > Security updates**.

The first check runs about one minute after the module starts. **Check now** starts one at once.

## What the page shows

- A status chip: **Up to date**, **Updates needed**, **Partly checked**, **No security data**,
  **Not available yet**, **Turned off** or **Not checked yet**. The chip is worded, never colour only.
- A table of the components with security updates, most severe first. Each row gives the component, its
  severity, the installed version, the recommended version, its compatibility with this Jahia
  ("Compatible with this Jahia", "Needs another Jahia version", "Not known: the module is not in the
  store's module list", or "Jahia upgrade required" for the platform), the source, the advisories and what
  to do ("Update {component} in Manage modules" with a link for a module, "Jahia upgrade documentation" for
  `jahia-core`). Advisory links open in a new tab, with a visible icon, and are rendered only for `https://`
  URLs.
- Advisories that do not come from `store.jahia.com` show `(from <store host>)`, the host of the store that
  published them. When the advisory link goes to another host, the page shows
  `(from <store host>, link to <link host>)`. Anyone who runs a store can publish any advisory id: read the host.
- A filter: **Jahia advisories only** or **All, including reports by module owners**, with a count each.
  The page opens on **All** when owner reports exist. Owner reports are versions that a module developer
  flagged on the store. They are not Jahia advisories.
- The problems found during the last check, per store, in plain language. The technical text is in English
  and is labelled "Technical details (in English)".
- A short glossary ("What these terms mean": advisory, CVE, store, module list), in plain words.
- When a filter leaves no finding, the page says so.
- The time of the last check and of the last check that reached every store, and when the next one is due.
- After you save or reset the settings, the page loads the report again. If that fails, it keeps the last
  report and says "The security report could not be updated: the last report loaded is shown. Reload the page
  to try again."

- "No known security update for this Jahia" appears only when the report is `OK`, recent and empty.
  When a feed is missing, unreachable, in an unknown format, or the report is stale, the page says so
  and never says that the platform is up to date.

## Banner

Users with `adminTemplates` also get a notice (the banner) in the Jahia UI, for example
"3 components need a security update (high severity or above).", with a link to the Security updates page.
It is a fixed box at the bottom right of the window, and the last element of the page body, so it is the
last stop of the Tab order. It moves to the top right only when the focused element would be covered and the
focused element is clear of the top edge. It then stays there for 2 seconds for that element. In a window at
most 40rem wide or high, it is a compact strip over the full width of the edge, at most 40% of the window
high, and scrolls inside.

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
the next query, without waiting for a check. Any error (network, permission, unexpected answer, no answer
within 20 seconds) leaves the banner as it is, and the browser asks again after 60 seconds. Users without
`adminTemplates` never see it.

The banner reads the server again every 15 minutes, when the browser tab becomes visible again (no request
if the last one is less than 30 seconds old), and when the page changes the report or the settings. It is
hidden on the Security updates page itself. A screen reader hears it only when its count or its dismissal
keys change. After **Hide this banner**, a status message tells when it comes back. Focus goes to the last
element outside the banner that had it, else to the element focused when the banner appeared, else it stays
on the page body.

For tests, the `<html>` element carries `data-security-updates-banner="pending"` while the banner asks the
server and `"ready"` after each decision. The attribute is removed when the banner stops.

**Colours.** The background and text colours are configurable (`#RRGGBB`). Their contrast ratio must be at
least 7:1 (WCAG 2.2 AAA, relative luminance formula). The link, the button border and the 3px focus ring
use the text colour, so they keep that contrast. The settings form and the server suggest a colour that
reaches 7:1 when a pair does not.

**Dismissal** (`bannerDismissPolicy`; the **Hide this banner** button only, the Escape key does nothing):

| Policy | Hidden until | Stored in |
|---|---|---|
| `SESSION` (default) | the browser tab is closed | `sessionStorage` of the tab: a new tab shows the banner again |
| `HOURS` | `bannerDismissHours` hours have passed | `localStorage`, key `security-updates-banner-dismissed:<hash of the user name>` |
| `UNTIL_CHANGE` | a new advisory or a new component is counted | `localStorage`, same key |
| `NEVER` | (no dismiss button) | nothing |

The identity of a dismissal is the set of `dismissalKeys`. Whatever the policy except `NEVER`, the banner
comes back as soon as a key is not in the dismissed set: a new advisory, even for a component that was
already counted, or a new component. It stays hidden when keys only disappear (an update was installed).
Any storage failure (blocked, full, corrupt record) shows the banner.

The browser stores hashes, never the advisory ids or the user name in clear. The hash is SHA-256 (hex) in a
secure context, and a 32-bit FNV-1a (8 hex characters) otherwise. In `localStorage` the value is
`{keys: [hash per key], at}`. In `sessionStorage`, `security-updates-banner-dismissed` holds the array of
hashes and `security-updates-banner-announced` holds `<count>|<sorted hashes>`. A record written in clear by a
development version never matches, so the banner shows again.

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

**Form of the store URL.** `B` is the base of the store: the module appends `/contents/...` to it. On a
standard Jahia, the module reads the store of the module manager,
`https://store.jahia.com/en/sites/private-app-store`. The module manager appends
`/contents/modules-repository.moduleList.json` to that same base, so the security feed is read at
`https://store.jahia.com/en/sites/private-app-store/contents/modules-repository.securityUpdates.json`.
`defaultStoreUrl` (default `https://store.jahia.com`) is used only when the module manager holds no store,
or when `useForgeSettings=false`. The module appends the paths to whatever base it is given.
A URL has at most 2,048 characters and only printable ASCII characters.

The stores read, in order:

1. the stores of the module manager, when `useForgeSettings=true`, with their user and password
   (Basic authentication, sent only to that store, over https, no redirects). Where they are kept depends on the
   module manager version:
   - module manager 2.14 and later: OSGi factory configurations
     `karaf/etc/org.jahia.modules.modulemanager.forge.configuration-<name>.cfg` (keys `url`, `user`, `password`);
   - module manager 2.12 and earlier: JCR nodes `jnt:forgeServerSettings` under `/settings/forgesSettings`.

   When neither can be read, the report has an error of kind `UNAVAILABLE` with the detail
   `FORGE_SETTINGS_UNREADABLE`, and the default store is used instead. The report status is then at best
   `PARTIAL`;
2. otherwise, or when none is configured, `defaultStoreUrl` (when it is not empty). With
   `useForgeSettings=false`, the module reads `defaultStoreUrl`, not the module manager stores;
3. plus every URL of `extraFeedUrls` (at most 10), read as a security feed only: no catalogue, no credentials.

At most 20 stores and feeds are read in one check. The Jahia Store (`store.jahia.com`) is read first.

**A store that answers 404.** A store with no security feed gives the error `NOT_PUBLISHED`. When another
feed answers, that error still keeps the report `PARTIAL`. To read only your own feed, set
`useForgeSettings=false`, empty `defaultStoreUrl`, and list your feed in `extraFeedUrls`: no other store is
then read.

**Trust boundary.** The stores of the module manager can be edited by every user who holds `adminTemplates`
in the module manager. That is the module manager's own trust boundary, not this module's. Those users
decide which stores are read and with which credentials. If that matters to you, set `useForgeSettings=false`
and configure `defaultStoreUrl` (editable by server administrators only).

**Same advisory in several stores.** When two feeds publish an advisory with the same `id`, the one of the
official Jahia Store (`store.jahia.com`) wins. Between other feeds the first one in the order above wins.
A private store or an extra feed cannot replace an advisory of the Jahia Store.

### Hosting a feed

To publish your own feed, serve these documents:

- The feed at `<base>/contents/modules-repository.securityUpdates.json` (a store), or at any https URL
  listed in `extraFeedUrls`. Answer HTTP 200 with a JSON body of at most 2 MB, in UTF-8, in the
  [Feed format](#feed-format). Use https only, and no redirect: a redirect is not followed (`REDIRECT`).
  Answer 404 when nothing is published: the module reports `NOT_PUBLISHED`.
- Answer within the limits: 10 seconds to connect, 30 seconds without data, 60 seconds for the whole request.
  Each Jahia node fetches the feed once per check (every `refreshIntervalHours`, plus on demand), so plan for
  one request per node per day by default.
- A private certificate authority must be trusted by the JVM of Jahia, or the connection fails
  (`CONNECTION`).
- Optional Basic credentials are sent only to a store of the module manager, never to `extraFeedUrls`.
- For compatibility, the module catalogue at `<base>/contents/modules-repository.moduleList.json`, read only
  for a store (not for a feed of `extraFeedUrls`) that has a security feed, at most 4 MB. It is the document
  of the module manager: a JSON array whose first element has a `modules` array of
  `{"name", "groupId", "versions": [{"version", "requiredVersion"}]}`. `name`, `groupId` and `versions` are
  required, and `name` is the module id (bundle symbolic name). `requiredVersion` carries the prefix
  `version-`, for example `version-8.2.3.7`, and the module removes it. Without a catalogue,
  compatibility is `Unknown`.

## When checks run

- The first check runs about one minute after the module starts, then every `refreshIntervalHours`
  (default 24).
- A change of `enabled`, `refreshIntervalHours`, `useForgeSettings`, `extraFeedUrls` or `defaultStoreUrl`
  restarts the schedule: the next check runs about one minute later. When such a change arrives during a
  check, that check publishes nothing and one new check runs as soon as it ends. A change of the `banner*`
  settings does not start a check and does not cancel one. It applies on the next query.
- Turning the checks on again (`enabled=true`) shows `NOT_CHECKED` until the first check, and keeps
  `lastSuccess`. A check that runs while the checks are turned off publishes nothing, even if they are turned
  on again before it ends. One new check follows.
- **Check now** (the `refresh` mutation) never blocks. It starts a check in the background and returns at
  once. The page shows the progress and polls the report. It starts nothing in three cases: a check is
  already running, the checks are turned off, or less than `onDemandMinIntervalSeconds` have passed since
  the later of the start and the end of the previous check. The default is 30 seconds. It is a hidden key,
  see [Configuration](#configuration). The page then says that no new check was started.
- One check runs at a time. A scheduled check that falls due during another one is skipped. A new check
  follows the running one only if that check read its settings before the skipped check was due. A check
  that starts after a settings change needs no second one. `nextCheckAt` is the time of the skip plus the
  interval.
- **Time limit.** A check has 5 minutes in all. No new store starts after that. The store being read when the
  time is over finishes within its own request limits. Each store not reached gets the error `UNAVAILABLE` /
  `TIMEOUT` ("Not checked: the check ran out of time"), the data last read from it is kept, and the report is
  `PARTIAL`.
- **One failing store.** An unexpected failure while a store is checked gives that store the error
  `UNAVAILABLE` / `CHECK_FAILED`. The other stores are still read and the data last read from the failing
  store is kept. The cause is in the Jahia log.
- When the feed of a store fails in transport (`TIMEOUT`, `CONNECTION`, `SSRF_BLOCKED`), the module list of
  that store is not requested, and no catalogue error is added. Other feed errors (`TOO_LARGE`, an HTTP
  status, unreadable JSON) still read the module list.
- The page polls the report every 2 seconds, at most 90 times, and stops after 3 failed polls in a row.
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
| `PARTIAL` | A feed is usable but the check is incomplete. Causes: a store failed (its last good data is kept and the error says so), entries were skipped, a version could not be compared, the module manager stores could not be read, the check ran out of time, or the report is stale. Findings shown are real. A missing finding is not proof of safety. |
| `UNAVAILABLE` | No security feed is available at all. |

Only a `CATALOGUE_UNAVAILABLE` error does not prevent `OK`: compatibility is then `Unknown` and the feed is
still used. Every other error kind makes the report `PARTIAL` or `UNAVAILABLE`.

`lastSuccess` is the time of the last check whose status was `OK`.

Errors (`report.errors { storeUrl kind detail message }`). The `message` is a short English technical text
made of status codes and this module's own words. It never repeats text received from a store. The page
translates the kind and the detail.

| Kind | When | Details |
|---|---|---|
| `NOT_PUBLISHED` | The store answers 404 for the feed: it does not publish security data. With another working feed, the report is `PARTIAL`. | |
| `UNAVAILABLE` | The store cannot be read, or the check could not run. Also when more than 20 stores and feeds are configured (no detail). | `SSRF_BLOCKED` (Jahia refused to connect, see [Security](#security)), `TIMEOUT` (the 10 s connect, 30 s without data or 60 s for the whole request ran out, or the check ran out of its 5 minutes), `CONNECTION` (DNS, TLS, refused, reset), `REDIRECT` (not followed), `AUTH` (401 or 403), `HTTP_STATUS` (any status other than 200, 404, 401, 403 or 3xx), `TOO_LARGE`, `FORGE_SETTINGS_UNREADABLE`, `CHECK_FAILED` (an unexpected failure of this store, see the Jahia log) |
| `NOT_UNDERSTOOD` | The feed was received but not understood, or some entries, references, fixed versions or an invalid `introducedVersion` were ignored, or a list was cut (more than 10,000 advisories or flagged versions, or more than 100 fixes per advisory, fixed versions per fix, CVEs per advisory or references per flagged version). | `INVALID_JSON` (also a feed nested too deeply, or an unexpected parser failure), `SCHEMA_UNSUPPORTED` (the feed's `schemaVersion` is not 1: a newer module may understand it), `ENTRIES_SKIPPED` |
| `INVALID_URL` | A configured URL is refused and not fetched. | `NOT_HTTPS` for a plain http URL, none for another invalid URL |
| `CATALOGUE_UNAVAILABLE` | The module catalogue could not be read, or was cut or truncated. Non blocking. | as for `UNAVAILABLE`, plus `INVALID_JSON`, `ENTRIES_SKIPPED` |
| `MALFORMED_VERSION` | An installed version (or the running Jahia version) cannot be compared: that component is not checked. | |
| `NO_STORE` | No store is configured (for example an empty `defaultStoreUrl` and no module manager store). | |
| `STALE` | The last check is older than twice the refresh interval. | |

When a store fails, the data last read from it is kept and the error says so. The error `message` of a store
whose last data is shown ends with "(showing the data of the last successful check of this store)".

## How findings are computed

For each advisory fix whose `component` is an installed module (bundle symbolic name, and same
`groupId` only when both the feed and the module give one) or `jahia-core` (the running `Jahia.VERSION`), with X the installed
version and the *line* of a version being its major for a module and `major.minor.servicePack` for
`jahia-core`:

- X is **not affected** when it is below the fix's `introducedVersion` (when the fix has one), when a fixed
  version on X's line is <= X, or when X >= every fixed version.
- Otherwise X is affected, and the recommended version is the smallest fixed version on X's line that
  is above X, else the smallest fixed version above X.

Examples, fixes `[2.24.0, 3.10.0]`: 2.20.0 -> 2.24.0; 2.30.0 not affected; 3.5.0 -> 3.10.0; 1.4.0 -> 2.24.0;
4.0.0 not affected. For `jahia-core` with fixes `[8.2.3.7, 8.1.9.3]`: 8.1.9.1 -> 8.1.9.3; 8.2.2.1 -> 8.2.3.7.

`introducedVersion` is optional. With introduced in 2.0.0 and fixed in 2.24.0 and 3.10.0: 1.4.0 is not
affected; 2.0.0 -> 2.24.0; 2.30.0 not affected; 3.0.0 -> 3.10.0. It applies to modules and to `jahia-core`. A
malformed `introducedVersion` is ignored: the fix is kept, as if the field was absent (the conservative
choice), and the value is counted as skipped (`ENTRIES_SKIPPED`, status `PARTIAL`). JSON `null` counts as
absent.

In a flagged version, `version` is the **first fixed version**. An owner-flagged version V affects X when they
share name (and `groupId` when both are known) and major, and X < V. The finding recommends V. A flagged
version named `jahia-core` is ignored.

Findings are grouped per component and source (Jahia advisory or owner report): one row with the
highest severity and every advisory. The row's recommended version is one at which no advisory of the
row applies any more (the candidate is raised until it is stable), so installing it fixes them all. This is
the version that clears every advisory of the row.

Compatibility of a module recommendation mirrors the module manager (`ForgeService`): compatible when
the catalogue's required Jahia version is <= the running version and has the same major; unknown when
the version is not in any catalogue. `jahia-core` findings read "Jahia upgrade required".

Versions are compared with Jahia's `org.jahia.utils.comparator.VersionComparator` (Maven
`ComparableVersion` ordering). A version is well formed when it is digits, optionally followed by one
separator (`.`, `-`, `_` or `+`) and letters, digits or those separators. It is "malformed" when it
does not match, is longer than 64 characters (checked before any cut) or has a segment longer than
9 digits. For example `3a` and `3_` are malformed.

- A malformed fixed version of a fix that has other well-formed versions is dropped and counted as skipped.
  The report then has `ENTRIES_SKIPPED` and is `PARTIAL`, never `OK`.
- A fix with no well-formed version is dropped and counted once.
- A malformed flagged version is skipped and counted.
- A malformed installed version (or running Jahia version) is reported as `MALFORMED_VERSION` and that
  component is not checked. The rest of the report is still built, and its status is `PARTIAL`.

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
        {"component": "example-module", "groupId": "org.example", "introducedVersion": "2.0.0", "fixedVersions": ["2.4.1", "3.1.0"]}
      ]
    }
  ],
  "flaggedVersions": [
    {
      "name": "example-module",
      "version": "2.4.0",
      "severity": "medium",
      "references": ["CVE-2026-54321", "https://example.com/notes/2.4.0"],
      "note": "Fixes a known issue of the earlier versions"
    }
  ]
}
```

Rules:

- `schemaVersion` must be the integer `1`. Anything else (missing, a string, `1.0`, another number)
  fails closed: `NOT_UNDERSTOOD` with the detail `SCHEMA_UNSUPPORTED`. The document must be a JSON object, nested
  at most 64 levels. A deeper document is `NOT_UNDERSTOOD` / `INVALID_JSON`.
- `advisories[]`: `id` (required), `fixes[]` (required, at least one valid fix), `title` (defaults to the
  id), `severity`, `cvss`, `cves[]`, `url`, `published`. The `summary` field is not read.
  - A fix needs `component` (the bundle symbolic name of the module, or `jahia-core`) and `fixedVersions[]`
    with at least one well-formed version. `groupId` and `introducedVersion` are optional.
  - `cvss` and `published` are accepted but not shown.
- `flaggedVersions[]`: `name` and `version` are required. `groupId`, `severity`, `references[]` and
  `note` are optional. `version` is the **first fixed version**: the installed versions below it on the same
  major are reported, and `version` is the recommended one.
- `severity` is `CRITICAL`, `HIGH`, `MEDIUM` or `LOW`, in any case. Anything else, or none, is `UNKNOWN`. A finding
  of unknown severity is listed but never counted by the banner.
- Entries that cannot be used (a missing required field, no valid fix, not an object) are skipped and
  counted: `NOT_UNDERSTOOD` with the detail `ENTRIES_SKIPPED`, status `PARTIAL`. Unknown keys are ignored.
- Limits: 2 MB per feed; at most 10,000 advisories and 10,000 flagged versions; at most 100 fixes per
  advisory, 100 fixed versions per fix, 100 CVEs per advisory and 100 references per flagged version.
  A list longer than its limit is cut, and the report says so: `NOT_UNDERSTOOD` / `ENTRIES_SKIPPED`,
  status `PARTIAL`.
- Text values are cut at 500 characters, ids and CVEs at 100, names (component, `groupId`, flagged `name`) at
  200, URLs at 2,048. Tab, CR and LF become spaces. Other control characters and format characters are
  removed. A `url` that is not `http(s)` is dropped, and only `https://` links are shown.
- A report lists at most 50 advisory references per finding and about 2,000 in all, and at most 500 fixed
  versions per component are considered. When one of these caps applies, the report says so
  (`ENTRIES_SKIPPED`).
- **Owner references.** In an owner flag, each reference that looks like `CVE-YYYY-NNNN` or `JSA-YYYY-NNNN`
  goes to the CVE list of the finding (a JSA id goes there too). The first reference that starts with
  `https://` is the link. The id of an owner report is the references joined with commas, cut to 197
  characters followed by `...` when longer (or `owner:<name>@<version>` when there is no reference); the note is its title.
- The same advisory `id` in several feeds: see [Data sources](#data-sources).

## Configuration

PID `org.jahia.community.securityupdates`, editable in `karaf/etc/org.jahia.community.securityupdates.cfg`
or in the OSGi configuration UI (labels in English and French), or from the Settings section of the page
(server administrators). The shipped file starts with `# default configuration - won't be overridden`, so your
changes survive restarts and redeployments.

| Key | Default | Range / values | Description |
|---|---|---|---|
| `enabled` | `true` | `true`, `false` | `false` stops the checks; the page then says the checks are turned off. |
| `refreshIntervalHours` | `24` | 1 to 720 | Hours between two scheduled checks. |
| `useForgeSettings` | `true` | `true`, `false` | Read the stores of the module manager. |
| `extraFeedUrls` | (empty) | at most 10 https URLs | Full URLs of additional security feeds, separated by commas or spaces. |
| `defaultStoreUrl` | `https://store.jahia.com` | https base URL, or empty | Store used only when the module manager holds no store, or when `useForgeSettings=false`. Empty means none. |
| `bannerEnabled` | `true` | `true`, `false` | Show the banner in the Jahia UI. The page is not affected. |
| `bannerMinimumSeverity` | `HIGH` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | Lowest severity the banner counts. |
| `bannerIncludeOwnerReports` | `true` | `true`, `false` | Count owner-reported findings in the banner, not only Jahia advisories. |
| `bannerBackgroundColor` | `#1B1B1B` | `#RRGGBB` | Banner background. |
| `bannerTextColor` | `#FFFFFF` | `#RRGGBB` | Banner text (also link, button and focus ring). The pair needs a contrast of at least 7:1. |
| `bannerDismissPolicy` | `SESSION` | `SESSION`, `HOURS`, `UNTIL_CHANGE`, `NEVER` | See [Banner](#banner). |
| `bannerDismissHours` | `24` | 1 to 720 | Hours a dismissal lasts with `HOURS`. |
| `onDemandMinIntervalSeconds` | `30` | 0 to 3600 | Hidden key, not shown in the settings page. Seconds from the later of the start and the end of the previous check before **Check now** may start another. The e2e tests set it to 0, except the rate-limit test (3600). The page never changes it. |

**A `.cfg` file edited by hand is read leniently**, so a typo never stops the module. The service reads the
raw values: an unreadable value takes its default and the module still starts.

- A missing key takes its default, silently.
- A boolean or a number that cannot be read takes its default, with one WARN in the log, for example
  `Invalid enabled 'ture': using true` or `Invalid refreshIntervalHours 'abc': using 24`.
- A number out of range is clamped to the nearest limit (for example `refreshIntervalHours=0` becomes 1), with
  one WARN. This includes numbers beyond the 32-bit range, for example
  `refreshIntervalHours 4294967297 is out of range (1 to 720): using 720`.
- A property with several values (an array) is joined with ", ".
- `extraFeedUrls` accepts commas or spaces. Beyond 10 URLs only the first 10 are used, with one WARN.
- An empty or unknown `bannerMinimumSeverity` means `HIGH`, an empty or unknown `bannerDismissPolicy` means
  `SESSION`, and an invalid colour or a pair below 7:1 means the default pair. An unknown severity or policy
  and an invalid colour give a WARN. An empty severity or policy gives none. An empty colour gives a WARN.
- An empty `defaultStoreUrl` means no default store: with no module manager store either, the report
  shows `NO_STORE`.
- A URL that is not https is not an error of the file: it is reported at check time (`INVALID_URL`).

The warnings are logged once per configuration change, by `org.jahia.community.securityupdates`.

The settings form (Settings section of the page, server administrators only) is stricter than the file:
**Save settings** sends the whole set, the server checks every rule (https URLs, at most 10 feeds, ranges, the
7:1 contrast) and refuses an invalid set with an error per field, so nothing is written. **Reset to defaults**
asks for confirmation, then writes the default values. For a user without `admin` the section is read-only: the values are shown as text, in one list per group,
with no control. While a save runs, the text fields are read-only. Editing a field clears the status message.
A refused save lists links to the fields to correct ("Settings to correct:").
A change to the checks (`enabled`, `refreshIntervalHours`, `useForgeSettings`, `defaultStoreUrl`,
`extraFeedUrls`) restarts the schedule. The page then says "Settings saved. The next check runs in about one
minute." when checks are on. Otherwise (for example a banner change) it says "Settings saved. They apply at
once." If the `.cfg` file exists but is not loaded yet, the save fails with `WRITE_FAILED` and the file is
not overwritten: try again in a few seconds.

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
      lastSuccess       # ISO-8601, last check whose status was OK
      nextCheckAt       # ISO-8601, null when disabled or not scheduled
      jahiaVersion
      errors { storeUrl kind detail message }
      findings {
        component groupId installedVersion recommendedVersion
        compatibility     # COMPATIBLE | INCOMPATIBLE | UNKNOWN | JAHIA_UPGRADE
        requiredJahiaVersion
        severity          # CRITICAL | HIGH | MEDIUM | LOW | UNKNOWN
        source            # ADVISORY | OWNER_REPORTED
        advisories { id title url cves severity storeHost }
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

- `settings` returns the values stored in the configuration (after the lenient reading above, and without the
  `user:password@` part of a URL), and
  `canEdit`, true only for users who hold `admin`. `updateSettings` and `resetSettings` return the values
  they saved.
- Field error codes of `updateSettings`: `OUT_OF_RANGE`, `INVALID_URL`, `NOT_HTTPS`, `TOO_MANY`
  (more than 10 feeds), `INVALID_VALUE`, `INVALID_COLOR`, `LOW_CONTRAST` (with `suggestions`) and
  `WRITE_FAILED` (field null) when the configuration cannot be written. The input fields are non-null, so
  `MISSING`, `INVALID_BOOLEAN` and `INVALID_NUMBER` are refused by GraphQL itself and never reach a client.
- Advisory `url`s are returned only when they start with `https://`.
- `storeHost` is the lower-case host of the store whose feed published the advisory or the owner report.

## Security

- **Permissions.** Reading is for `adminTemplates`. Changing the settings is for `admin`, so that a module
  manager user cannot disable the checks, point the module at another store or hide the banner. The
  administration route of the page requires `adminTemplates`.
- **Audit log.** Every settings change is logged at INFO by `org.jahia.community.securityupdates.service.SettingsStore`:
  `Security updates settings saved by <user>: <key>: <old> -> <new>; ...`. Store credentials are not settings of
  this module and never appear in it.
- **HTTP.** All requests go through Jahia's `HttpClientService` (Jahia proxy settings apply) with a 10 s
  connect timeout, a 30 s read timeout, a 60 s deadline for the whole request (a watchdog thread cancels it),
  no redirects, and size limits (2 MB for
  the feed, 4 MB for the catalogue, at most 4 KB read of an error body). The whole check has 5 minutes.
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
- **Untrusted text.** All feed text is untrusted: control characters are removed (tab, CR and LF become spaces) and lengths bounded on
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
- **Header size is not bounded.** The size limits apply to response bodies only. Jahia core's
  `HttpClientService` builds its pools with the default `Http1Config`, which has no limit on the length or the
  number of response headers. A hostile store could stream headers until the heap runs out. This is reported
  to Jahia. The module keeps the screened shared client on purpose: its own client would lose the SSRF
  screening and the proxy settings. Configure only stores that you trust.
- **A third-party store can publish any advisory id.** The page shows `(from <store host>)` for an advisory that
  does not come from `store.jahia.com`. The official store wins for an id found in several feeds.
- **Reading level.** The page uses the terms of a Jahia administrator (advisory, CVE, store, module list).
  A glossary on the page defines them.

## Upgrade

- Upload the new JAR in **Modules**. Your settings stay: the `.cfg` file in `karaf/etc` is not overwritten
  (its first line is `# default configuration - won't be overridden`). The new version runs a check one minute
  after it starts.
- A new key added by a release is read with its default until you set it.

## Uninstall

- Stop and undeploy the module in **Modules**.
- The file `karaf/etc/org.jahia.community.securityupdates.cfg` stays, so the settings are kept if you
  install the module again. Delete it for a clean removal.
- The banner dismissals live in the browsers (`sessionStorage`, and `localStorage` keys that start with
  `security-updates-banner-dismissed`). They hold hashes only and can stay.

## Troubleshooting

To see what a check does, in the Jahia (Karaf) console: `log:set DEBUG org.jahia.community.securityupdates`,
and `log:set INFO org.jahia.community.securityupdates` to go back.

| Symptom | Cause | Action |
|---|---|---|
| "Not available yet", `NOT_PUBLISHED` | The store does not publish the security feed yet. | Nothing. Wait for the store to publish it, or add an `extraFeedUrls` feed. If another feed works and you want no `PARTIAL` status, set `useForgeSettings=false` and empty `defaultStoreUrl`. |
| "No security data" with `UNAVAILABLE`, detail `SSRF_BLOCKED` | The store name resolves to an internal address. | Add the host to `jahia.httpClient.allowedInternalHosts` and restart Jahia. |
| Detail `CONNECTION` or `TIMEOUT` | No route, proxy, DNS or TLS problem from this node, or a slow store. With the message "Not checked: the check ran out of time", the 5 minutes of the check ran out before this store. | Check the outbound HTTPS and the proxy from the node, and the DEBUG log. Remove slow stores. |
| Detail `HTTP_STATUS` | The store answered with a status other than 200, 404, 401, 403 or a redirect. | Check the store, or try again later. |
| Detail `TOO_LARGE` | The feed is larger than 2 MB, or the catalogue larger than 4 MB. | Make the document smaller. |
| Detail `CHECK_FAILED` | An unexpected failure while this store was checked. The other stores were read. | See the ERROR line "The check of the security source ... failed unexpectedly" in the Jahia log. |
| Detail `AUTH` | The store refused the credentials. | Fix the store user and password in the module manager. |
| Detail `REDIRECT` | The store URL redirects. Redirects are not followed. | Use the final https URL. |
| `INVALID_URL` with `NOT_HTTPS` | A store or feed uses `http://`. | Use an `https://` URL. |
| `NOT_UNDERSTOOD` with `SCHEMA_UNSUPPORTED` | The feed uses a newer `schemaVersion`. | Update this module. |
| `NOT_UNDERSTOOD` with `INVALID_JSON` | The feed is not valid JSON, is nested more than 64 levels, or hit a parser defect. | Check the feed. See the WARN "Security feed of ... could not be parsed" in the log. |
| `NOT_UNDERSTOOD` with `ENTRIES_SKIPPED` | Some entries, malformed versions or references were ignored, or a list passed its limit of 100 or 10,000. The report is `PARTIAL`. | Fix the feed ([Feed format](#feed-format)). The findings shown are real. |
| `CATALOGUE_UNAVAILABLE` | The module list could not be read or was cut. The report can stay `OK`. | Compatibility shows "Not known". Fix the module list of the store. |
| `MALFORMED_VERSION` | An installed version, or the Jahia version, cannot be compared. | That component is not checked. See [How findings are computed](#how-findings-are-computed). |
| `NO_STORE` | No store is configured. | Add a store in Manage modules, or set `defaultStoreUrl`. |
| `FORGE_SETTINGS_UNREADABLE` | The module manager stores could not be read. | See the log; set `useForgeSettings=false` to use only `defaultStoreUrl`. |
| `STALE` | No check finished within twice the interval. | Look for errors in the log, use **Check now**, check that the module is started. |
| Check now does nothing | A check is running, or the previous one started or ended less than `onDemandMinIntervalSeconds` ago, or checks are disabled. | Wait a minute. |
| A known vulnerable module is not listed | The feed names it differently from its bundle symbolic name, or it is stopped. | See the FAQ. |
| No banner | `bannerEnabled=false`, no counted finding, banner dismissed, user without `adminTemplates`, or you are on the Security updates page. | Check `bannerMinimumSeverity` and the dismissal policy. |
| Settings are read-only | The user holds `adminTemplates` but not `admin`. | Ask a server administrator. |
| Settings differ between nodes | The cluster did not deliver `karaf/etc` yet. | Check the cluster file synchronisation. |
| The report differs between nodes | Each node checks on its own, at its own time, and keeps its report in memory. | Compare the times of the last check. Use **Check now** on each node. |

## FAQ

**Does the module send my inventory to the store?** No. It only downloads two public documents from each
store (the security feed and the module catalogue) and compares them with your installed components
locally. The names and versions of your modules are never sent. The only thing sent to a store is the Basic
credential of a private store configured in the module manager.

**Why is my module not matched?** The `component` name in the feed must be the module's bundle symbolic
name, that is the module id (for example `jahia-oauth`), not its display name. The `groupId` must match
when both the feed and the module give one.

**Does it work offline?** The module needs outbound HTTPS. Without it, the report is `UNAVAILABLE`.

## Build

```bash
mvn clean install        # frontend first (yarn lint, jest, webpack, in generate-resources), then Java compile, tests, package
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
     Never commit `tests/.env`.
  3. `cd tests && bash ci.build.sh && bash ci.startup.sh`.

  The tests use a store fixture served over https with a certificate that Jahia trusts (and a test-only njs
  switch on port 8081, used by spec 08), bind Jahia to 127.0.0.1, set the hidden key
  `onDemandMinIntervalSeconds` to 0 so they never wait (3600 in the rate-limit test), run accessibility
  checks with `cypress-axe`, and do not print the super user password.
  The test image built by the `@jahia/cypress` harness contains `tests/.env`. Never push the image
  (`TESTS_IMAGE`), and remove the images after a run.
- To run one Java test class with its nested classes, use `-Dtest='Class*'`: `-Dtest=Class` skips the
  `@Nested` classes with this Surefire version.

## License

MIT, see [LICENSE.txt](LICENSE.txt).
