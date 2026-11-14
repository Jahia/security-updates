package org.jahia.community.securityupdates.matching;

import org.jahia.community.securityupdates.model.Advisory;
import org.jahia.community.securityupdates.model.AdvisoryFix;
import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.Compatibility;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.FlaggedVersion;
import org.jahia.community.securityupdates.model.InstalledComponent;
import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.Severity;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.jahia.community.securityupdates.model.StoreErrorKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Matches installed components against store security feeds. Pure: no I/O, no Jahia runtime.
 *
 * <p>Advisory rule, for an installed version X and the fixed versions of one fix entry (the "line" of a
 * version is its major for a module, {@code major.minor.servicePack} for {@code jahia-core}):
 * <ul>
 *     <li>X is not affected when the fix entry gives an introduced version (the first version with the issue) and
 *     X is below it;</li>
 *     <li>X is not affected when a fixed version on X's line is &lt;= X, or when X &gt;= every fixed version;</li>
 *     <li>otherwise the recommendation is the smallest fixed version on X's line above X, else the smallest
 *     fixed version above X.</li>
 * </ul>
 * Owner-flagged rule: a flagged version V affects X when they share name (and groupId when both are known)
 * and major, and X &lt; V; the recommendation is the smallest such V.
 *
 * <p>Findings are grouped per component and source. The recommendation of a group is a version at which no
 * advisory of the group applies any more (computed by re-checking the candidate until it is stable).
 */
public final class SecurityMatcher {

    /** A CVE, or a Jahia vulnerability identifier: JSA-YYYY-NNNN with an optional numeric suffix (JSA-2026-0036.1066). */
    private static final Pattern CVE = Pattern.compile("CVE-\\d{4}-\\d{4,}|JSA-\\d{4}-\\d{4}(\\.\\d+)?");
    private static final String HTTPS = "https://";
    /** Most advisory references listed in one finding (the most severe first); the finding still covers them all. */
    static final int MAX_REFS_PER_FINDING = 50;
    /** Most advisory references listed in a whole report, beyond the first one of each finding. */
    static final int MAX_REPORT_REFS = 2_000;
    /**
     * Most fixed versions taken into account for one component. The recommendation is refined in rounds, each
     * going over every rule of the component; this bounds that work (rules x fixed versions x rounds) whatever the
     * feeds hold.
     */
    static final int MAX_FIXED_VERSIONS_PER_COMPONENT = 500;
    /** Longest id of an owner report, which joins its references. */
    static final int MAX_OWNER_ID = 200;
    private static final String ELLIPSIS = "...";

    private static final Comparator<Finding> FINDING_ORDER = Comparator
            .comparing((Finding f) -> f.getSeverity().ordinal()).reversed()
            .thenComparing(Finding::getComponent)
            .thenComparing(f -> f.getSource().ordinal());
    private static final Comparator<AdvisoryRef> REF_ORDER = Comparator
            .comparing((AdvisoryRef r) -> r.getSeverity().ordinal()).reversed()
            .thenComparing(AdvisoryRef::getId);

    private final String jahiaVersion;
    private final boolean jahiaVersionUsable;

    /**
     * @param jahiaVersion the running Jahia version, such as {@code 8.2.2.1}
     */
    public SecurityMatcher(String jahiaVersion) {
        this.jahiaVersion = jahiaVersion;
        this.jahiaVersionUsable = Versions.isWellFormed(jahiaVersion);
    }

    /**
     * @param modules the installed modules (the platform is added from the running Jahia version)
     * @param feeds   the security feeds, in store priority order; an advisory id seen twice is used once, from the
     *                first feed that has it (the service puts the Jahia Store first)
     * @param catalog the merged module catalogue of the stores, used for compatibility
     * @return the findings, most severe first, and the problems met
     */
    public MatchResult match(List<InstalledComponent> modules, List<SecurityFeed> feeds, ModuleCatalog catalog) {
        List<StoreError> problems = new ArrayList<>();
        List<InstalledComponent> components = usableComponents(modules, problems);

        List<Advisory> advisories = distinctAdvisories(feeds);
        List<FlaggedVersion> flags = distinctFlags(feeds);

        List<Finding> findings = new ArrayList<>();
        for (InstalledComponent component : components) {
            List<Rule> advisoryRules = advisoryRules(component, advisories, problems);
            toFinding(component, FindingSource.ADVISORY, advisoryRules, catalog).ifPresent(findings::add);
            if (!component.isCore()) {
                List<Rule> flagRules = flagRules(component, flags);
                toFinding(component, FindingSource.OWNER_REPORTED, flagRules, catalog).ifPresent(findings::add);
            }
        }
        findings.sort(FINDING_ORDER);
        List<Finding> bounded = bounded(findings);
        if (!bounded.equals(findings)) {
            problems.add(new StoreError(null, StoreErrorKind.NOT_UNDERSTOOD, StoreErrorDetail.ENTRIES_SKIPPED,
                    "Some advisory references are not listed (at most " + MAX_REFS_PER_FINDING + " per finding and about "
                            + MAX_REPORT_REFS + " in all); the recommended versions cover them all"));
        }
        return new MatchResult(bounded, problems);
    }

    /**
     * Bounds what feeds can push into a report: each finding lists at most {@link #MAX_REFS_PER_FINDING} references
     * (the most severe first), and the report about {@link #MAX_REPORT_REFS}, keeping at least one per finding.
     */
    private static List<Finding> bounded(List<Finding> findings) {
        List<Finding> result = new ArrayList<>(findings.size());
        int remaining = MAX_REPORT_REFS;
        for (Finding finding : findings) {
            List<AdvisoryRef> refs = finding.getAdvisories();
            int keep = Math.max(1, Math.min(Math.min(refs.size(), MAX_REFS_PER_FINDING), remaining));
            remaining = Math.max(0, remaining - keep);
            result.add(keep >= refs.size() ? finding : new Finding(finding.getComponent(), finding.getGroupId(),
                    finding.getInstalledVersion(), finding.getRecommendedVersion(), finding.getCompatibility(),
                    finding.getRequiredJahiaVersion(), finding.getSeverity(), finding.getSource(), refs.subList(0, keep)));
        }
        return result;
    }

    /**
     * Applies the advisory rule to one fix entry.
     *
     * @return the recommended version, or empty when {@code installed} is not affected
     */
    static Optional<String> recommendForAdvisory(String installed, List<String> fixedVersions, boolean core) {
        return recommendForAdvisory(installed, fixedVersions, null, core);
    }

    /**
     * Applies the advisory rule to one fix entry that may give the first version having the issue.
     *
     * @param introduced the first version that has the issue, or null; a malformed one is ignored
     * @return the recommended version, or empty when {@code installed} is not affected
     */
    static Optional<String> recommendForAdvisory(String installed, List<String> fixedVersions, String introduced,
                                                 boolean core) {
        List<String> fixes = wellFormed(fixedVersions);
        if (fixes.isEmpty() || introducedLater(installed, introduced)) {
            return Optional.empty();
        }
        String line = Versions.line(installed, core);
        if (fixedOnLine(installed, fixes, line, core) || Versions.compare(installed, highestOf(fixes)) >= 0) {
            return Optional.empty();
        }
        String lowestAboveOnLine = null;
        String lowestAbove = null;
        for (String fix : fixes) {
            if (Versions.compare(fix, installed) > 0) {
                lowestAbove = lowest(lowestAbove, fix);
                if (line.equals(Versions.line(fix, core))) {
                    lowestAboveOnLine = lowest(lowestAboveOnLine, fix);
                }
            }
        }
        return Optional.ofNullable(lowestAboveOnLine != null ? lowestAboveOnLine : lowestAbove);
    }

    /** Whether a fix on the installed version's line is at or below it: the installed version already has the fix. */
    private static boolean fixedOnLine(String installed, List<String> fixes, String line, boolean core) {
        return fixes.stream()
                .anyMatch(fix -> line.equals(Versions.line(fix, core)) && Versions.compare(fix, installed) <= 0);
    }

    private static String highestOf(List<String> versions) {
        String max = null;
        for (String version : versions) {
            max = highest(max, version);
        }
        return max;
    }

    private static boolean introducedLater(String installed, String introduced) {
        return introduced != null && Versions.isWellFormed(introduced) && Versions.compare(installed, introduced) < 0;
    }

    /**
     * Applies the owner-flagged rule to flagged versions of one module.
     *
     * @return the smallest flagged version of the installed major above {@code installed}, or empty
     */
    static Optional<String> recommendForFlags(String installed, List<String> flaggedVersions) {
        int major = Versions.major(installed);
        String lowest = null;
        for (String flagged : wellFormed(flaggedVersions)) {
            if (Versions.major(flagged) == major && Versions.compare(installed, flagged) < 0) {
                lowest = lowest(lowest, flagged);
            }
        }
        return Optional.ofNullable(lowest);
    }

    // ------------------------------------------------------------------------------------------ internals

    /** One rule applying to a component: what it recommends for a given version, and how it is shown. */
    private static final class Rule {
        private final AdvisoryRef ref;
        private final Function<String, Optional<String>> recommend;

        Rule(AdvisoryRef ref, Function<String, Optional<String>> recommend) {
            this.ref = ref;
            this.recommend = recommend;
        }
    }

    private List<InstalledComponent> usableComponents(List<InstalledComponent> modules, List<StoreError> problems) {
        List<InstalledComponent> components = new ArrayList<>();
        if (jahiaVersionUsable) {
            components.add(new InstalledComponent(InstalledComponent.JAHIA_CORE, null, jahiaVersion));
        } else {
            problems.add(new StoreError(null, StoreErrorKind.MALFORMED_VERSION,
                    "The running Jahia version cannot be compared: Jahia advisories and compatibility are not checked"));
        }
        for (InstalledComponent module : modules) {
            if (module.isCore()) {
                continue;
            }
            if (Versions.isWellFormed(module.getVersion())) {
                components.add(module);
            } else {
                problems.add(new StoreError(null, StoreErrorKind.MALFORMED_VERSION,
                        "The installed version of " + module.getName() + " cannot be compared: it is not checked"));
            }
        }
        return components;
    }

    private static List<Advisory> distinctAdvisories(List<SecurityFeed> feeds) {
        Map<String, Advisory> byId = new LinkedHashMap<>();
        for (SecurityFeed feed : feeds) {
            for (Advisory advisory : feed.getAdvisories()) {
                byId.putIfAbsent(advisory.getId(), advisory);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static List<FlaggedVersion> distinctFlags(List<SecurityFeed> feeds) {
        Map<String, FlaggedVersion> byKey = new LinkedHashMap<>();
        for (SecurityFeed feed : feeds) {
            for (FlaggedVersion flag : feed.getFlaggedVersions()) {
                byKey.putIfAbsent(flag.getGroupId() + ':' + flag.getName() + '@' + flag.getVersion(), flag);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private static List<Rule> advisoryRules(InstalledComponent component, List<Advisory> advisories, List<StoreError> problems) {
        List<Rule> rules = new ArrayList<>();
        int versions = 0;
        for (Advisory advisory : advisories) {
            for (AdvisoryFix fix : advisory.getFixes()) {
                if (concerns(fix, component)) {
                    List<String> fixed = fix.getFixedVersions();
                    String introduced = fix.getIntroducedVersion();
                    versions += fixed.size();
                    if (versions > MAX_FIXED_VERSIONS_PER_COMPONENT) {
                        problems.add(new StoreError(null, StoreErrorKind.NOT_UNDERSTOOD, StoreErrorDetail.ENTRIES_SKIPPED,
                                "The feeds list more than " + MAX_FIXED_VERSIONS_PER_COMPONENT + " fixed versions for "
                                        + component.getName() + ": the advisories beyond them are not checked"));
                        return rules;
                    }
                    boolean core = component.isCore();
                    AdvisoryRef ref = new AdvisoryRef(advisory.getId(), advisory.getTitle(), advisory.getUrl(),
                            advisory.getCves(), advisory.getSeverity());
                    rules.add(new Rule(ref, version -> recommendForAdvisory(version, fixed, introduced, core)));
                }
            }
        }
        return rules;
    }

    private static boolean concerns(AdvisoryFix fix, InstalledComponent component) {
        return fix.getComponent().equals(component.getName())
                && sameGroupWhenKnown(fix.getGroupId(), component.getGroupId());
    }

    private static boolean sameGroupWhenKnown(String expected, String actual) {
        return expected == null || actual == null || expected.equals(actual);
    }

    private static List<Rule> flagRules(InstalledComponent component, List<FlaggedVersion> flags) {
        List<Rule> rules = new ArrayList<>();
        for (FlaggedVersion flag : flags) {
            if (flag.getName().equals(component.getName())
                    && sameGroupWhenKnown(flag.getGroupId(), component.getGroupId())
                    && Versions.isWellFormed(flag.getVersion())) {
                List<String> flagged = Collections.singletonList(flag.getVersion());
                rules.add(new Rule(ownerRef(flag), version -> recommendForFlags(version, flagged)));
            }
        }
        return rules;
    }

    private static AdvisoryRef ownerRef(FlaggedVersion flag) {
        List<String> references = flag.getReferences();
        String id = references.isEmpty()
                ? "owner:" + flag.getName() + '@' + flag.getVersion()
                : capped(String.join(", ", references));
        List<String> cves = new ArrayList<>();
        String url = null;
        for (String reference : references) {
            if (CVE.matcher(reference).matches()) {
                cves.add(reference);
            } else if (url == null && reference.startsWith(HTTPS)) {
                url = reference;
            }
        }
        return new AdvisoryRef(id, flag.getNote(), url, cves, flag.getSeverity());
    }

    private static String capped(String id) {
        return id.length() <= MAX_OWNER_ID ? id : id.substring(0, MAX_OWNER_ID - ELLIPSIS.length()) + ELLIPSIS;
    }

    private Optional<Finding> toFinding(InstalledComponent component, FindingSource source, List<Rule> rules,
                                        ModuleCatalog catalog) {
        String installed = component.getVersion();
        List<Rule> applying = new ArrayList<>();
        String recommended = null;
        for (Rule rule : rules) {
            Optional<String> recommendation = rule.recommend.apply(installed);
            if (recommendation.isPresent()) {
                applying.add(rule);
                recommended = highest(recommended, recommendation.get());
            }
        }
        if (applying.isEmpty()) {
            return Optional.empty();
        }
        recommended = stableRecommendation(recommended, applying);

        Severity severity = Severity.UNKNOWN;
        List<AdvisoryRef> refs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Rule rule : applying) {
            severity = severity.max(rule.ref.getSeverity());
            if (seen.add(rule.ref.getId())) {
                refs.add(rule.ref);
            }
        }
        refs.sort(REF_ORDER);

        Compatibility compatibility;
        String requiredJahiaVersion;
        if (component.isCore()) {
            compatibility = Compatibility.JAHIA_UPGRADE;
            requiredJahiaVersion = recommended;
        } else {
            // the catalogue parser already removed the "version-" prefix
            requiredJahiaVersion = catalog.requiredJahiaVersion(component.getName(), component.getGroupId(), recommended)
                    .orElse(null);
            compatibility = compatibility(requiredJahiaVersion);
        }
        return Optional.of(new Finding(component.getName(), component.getGroupId(), installed, recommended,
                compatibility, requiredJahiaVersion, severity, source, refs));
    }

    /**
     * Raises {@code candidate} until no rule of the group applies to it any more. Each round moves to a
     * strictly higher version taken from the rules, so the loop ends; the bound is a safety net.
     */
    private static String stableRecommendation(String candidate, List<Rule> rules) {
        String current = candidate;
        for (int round = 0; round <= rules.size(); round++) {
            String next = null;
            for (Rule rule : rules) {
                Optional<String> still = rule.recommend.apply(current);
                if (still.isPresent()) {
                    next = highest(next, still.get());
                }
            }
            if (next == null) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private Compatibility compatibility(String requiredJahiaVersion) {
        if (requiredJahiaVersion == null || !jahiaVersionUsable || !Versions.isWellFormed(requiredJahiaVersion)) {
            return Compatibility.UNKNOWN;
        }
        boolean notNewer = Versions.compare(requiredJahiaVersion, jahiaVersion) <= 0;
        boolean sameMajor = Versions.major(requiredJahiaVersion) == Versions.major(jahiaVersion);
        return notNewer && sameMajor ? Compatibility.COMPATIBLE : Compatibility.INCOMPATIBLE;
    }

    private static List<String> wellFormed(List<String> versions) {
        List<String> result = new ArrayList<>();
        for (String version : versions) {
            if (Versions.isWellFormed(version)) {
                result.add(version);
            }
        }
        return result;
    }

    private static String lowest(String current, String candidate) {
        return current == null || Versions.compare(candidate, current) < 0 ? candidate : current;
    }

    private static String highest(String current, String candidate) {
        return current == null || Versions.compare(candidate, current) > 0 ? candidate : current;
    }
}
