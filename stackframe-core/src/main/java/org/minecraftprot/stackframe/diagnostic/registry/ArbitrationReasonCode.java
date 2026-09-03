package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.HashSet;
import java.util.regex.Pattern;

/** Stable machine-readable outcomes from the ADR 004 arbitration contract. */
public enum ArbitrationReasonCode {
    SELECTED(
            "selected",
            ArbitrationReasonScope.SELECTED_CANDIDATE,
            "Candidate was the unique highest eligible semantic result."),
    MERGED_COMPATIBLE(
            "merged-compatible",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "Compatible candidates with the same identity were merged."),
    SUPPRESSED_LOWER_RANK(
            "suppressed-lower-rank",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "An eligible candidate ranked below the selected result."),
    EXCLUDED_LOW_CONFIDENCE(
            "excluded-low-confidence",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "A valid candidate's effective confidence was below specialized eligibility."),
    EXCLUDED_CONFLICT(
            "excluded-conflict",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "An equal-ranked candidate participated in an unresolved semantic conflict."),
    EXCLUDED_LIMIT(
            "excluded-limit",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "A deterministic processing limit prevented the candidate from being selected."),
    EXCLUDED_INTERNAL_FAILURE(
            "excluded-internal-failure",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "An internal arbitration invariant prevented the candidate from being selected."),
    REJECTED_MALFORMED(
            "rejected-malformed",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "A candidate violated its structural contract."),
    REJECTED_DUPLICATE_CLASSIFIER_KEY(
            "rejected-duplicate-classifier-key",
            ArbitrationReasonScope.CLASSIFIER,
            "Every classifier sharing a duplicate key was disabled for the event."),
    REJECTED_INVALID_REFERENCE(
            "rejected-invalid-reference",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "A candidate referenced an absent failure unit or evidence item."),
    REJECTED_UNKNOWN_DIAGNOSTIC(
            "rejected-unknown-diagnostic",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "A candidate referenced an unregistered or inactive diagnostic identity."),
    REJECTED_UNSUPPORTED_CLAIM(
            "rejected-unsupported-claim",
            ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
            "Required evidence did not support a candidate claim."),
    FALLBACK_NO_ELIGIBLE(
            "fallback-no-eligible",
            ArbitrationReasonScope.FALLBACK_SELECTION,
            "No eligible specialized candidate remained."),
    FALLBACK_CONFLICT(
            "fallback-conflict",
            ArbitrationReasonScope.FALLBACK_SELECTION,
            "Equal-ranked candidates disagreed in meaning or could not combine."),
    FALLBACK_LIMIT(
            "fallback-limit",
            ArbitrationReasonScope.FALLBACK_SELECTION,
            "A deterministic processing limit prevented complete arbitration."),
    FALLBACK_INTERNAL_FAILURE(
            "fallback-internal-failure",
            ArbitrationReasonScope.FALLBACK_SELECTION,
            "An internal arbitration invariant failed and safe generic output was selected."),
    CLASSIFIER_FAILURE(
            "classifier-failure",
            ArbitrationReasonScope.CLASSIFIER,
            "A classifier failed or returned malformed output and was isolated.");

    private final String key;
    private final ArbitrationReasonScope scope;
    private final String meaning;

    ArbitrationReasonCode(
            String key,
            ArbitrationReasonScope scope,
            String meaning) {
        this.key = RegistryValidation.identifier(
                key, KeyPatternHolder.KEY, "arbitrationReason.key");
        this.scope = RegistryValidation.required(scope, "arbitrationReason.scope");
        this.meaning = RegistryValidation.text(meaning, "arbitrationReason.meaning");
    }

    static {
        var keys = new HashSet<String>();
        for (var reason : values()) {
            if (!keys.add(reason.key)) {
                throw new RegistryValidationException(
                        "duplicate arbitration reason code " + reason.key);
            }
        }
    }

    public String key() {
        return key;
    }

    public String meaning() {
        return meaning;
    }

    public ArbitrationReasonScope scope() {
        return scope;
    }

    private static final class KeyPatternHolder {
        private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]{0,63}");

        private KeyPatternHolder() {
        }
    }
}
