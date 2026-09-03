package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Declarative remediation ceiling; registry entries cannot execute operator actions. */
public record RemediationPolicy(
        String explanation,
        Set<RemediationAction> actions) {

    public RemediationPolicy {
        RegistryValidation.noNullElements(actions, "remediation.actions");
        if (actions.isEmpty()) {
            throw new RegistryValidationException(
                    "remediation.actions must declare governed semantics");
        }
        actions = Collections.unmodifiableSet(EnumSet.copyOf(actions));
        if (actions.contains(RemediationAction.NONE) && actions.size() != 1) {
            throw new RegistryValidationException(
                    "NONE remediation cannot be combined with another action");
        }
        explanation = RegistryValidation.text(explanation, "remediation.explanation");
        if (!explanation.equals(describe(actions))) {
            throw new RegistryValidationException(
                    "remediation explanation must be derived from governed actions");
        }
    }

    public RemediationSafety safety() {
        if (hasSafety(RemediationSafety.DESTRUCTIVE_STATE_CHANGE)) {
            return RemediationSafety.DESTRUCTIVE_STATE_CHANGE;
        }
        if (hasSafety(RemediationSafety.REVERSIBLE_STATE_CHANGE)) {
            return RemediationSafety.REVERSIBLE_STATE_CHANGE;
        }
        if (hasSafety(RemediationSafety.INSPECT_ONLY)) {
            return RemediationSafety.INSPECT_ONLY;
        }
        return RemediationSafety.NONE;
    }

    public boolean requiresRemedyEvidence() {
        return actions.stream().anyMatch(RemediationAction::requiresRemedyEvidence);
    }

    public boolean requiresOperatorConfirmation() {
        return actions.stream().anyMatch(RemediationAction::requiresOperatorConfirmation);
    }

    public boolean requiresBackup() {
        return actions.stream().anyMatch(RemediationAction::requiresBackup);
    }

    public boolean prohibitsAutomaticExecution() {
        return true;
    }

    public static RemediationPolicy none() {
        return of(RemediationAction.NONE);
    }

    public static RemediationPolicy of(RemediationAction... actions) {
        RegistryValidation.required(actions, "remediation.actions");
        var governedActions = EnumSet.noneOf(RemediationAction.class);
        for (var action : actions) {
            RegistryValidation.required(action, "remediation.actions");
            if (!governedActions.add(action)) {
                throw new RegistryValidationException(
                        "remediation.actions must not contain duplicates");
            }
        }
        return new RemediationPolicy(describe(governedActions), governedActions);
    }

    public static RemediationPolicy inspectOnly(RemediationAction... actions) {
        var governedActions = actions.length == 0
                ? new RemediationAction[] {RemediationAction.INSPECT_CORRELATED_TRACE}
                : actions;
        var policy = of(governedActions);
        if (policy.safety() != RemediationSafety.INSPECT_ONLY) {
            throw new RegistryValidationException(
                    "inspectOnly accepts only inspect-only remediation actions");
        }
        return policy;
    }

    private boolean hasSafety(RemediationSafety expected) {
        return actions.stream().anyMatch(action -> action.safety() == expected);
    }

    private static String describe(Set<RemediationAction> actions) {
        return actions.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(RemediationAction::meaning)
                .collect(Collectors.joining(" "));
    }
}
