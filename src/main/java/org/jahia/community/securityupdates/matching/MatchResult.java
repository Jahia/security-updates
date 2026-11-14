package org.jahia.community.securityupdates.matching;

import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.StoreError;

import java.util.List;

/**
 * Findings of a match, and the problems (such as an installed version that cannot be compared) met on the way.
 */
public final class MatchResult {

    private final List<Finding> findings;
    private final List<StoreError> problems;

    public MatchResult(List<Finding> findings, List<StoreError> problems) {
        this.findings = List.copyOf(findings);
        this.problems = List.copyOf(problems);
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public List<StoreError> getProblems() {
        return problems;
    }
}
