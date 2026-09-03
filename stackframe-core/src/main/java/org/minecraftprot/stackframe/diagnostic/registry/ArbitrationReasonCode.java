package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.HashSet;
import java.util.regex.Pattern;

/** Stable machine-readable outcomes from the ADR 004 arbitration contract. */
public enum ArbitrationReasonCode {
    SELECTED("selected", "Candidate was the unique highest eligible semantic result."),
    MERGED_COMPATIBLE(
            "merged-compatible",
            "Compatible candidates with the same identity were merged."),
    SUPPRESSED_LOWER_RANK(
            "suppressed-lower-rank",
            "An eligible candidate ranked below the selected result."),
    REJECTED_MALFORMED(
            "rejected-malformed",
            "A candidate violated its structural contract."),
    REJECTED_DUPLICATE_CLASSIFIER_KEY(
            "rejected-duplicate-classifier-key",
            "Every classifier sharing a duplicate key was disabled for the event."),
    REJECTED_INVALID_REFERENCE(
            "rejected-invalid-reference",
            "A candidate referenced an absent failure unit or evidence item."),
    REJECTED_UNKNOWN_DIAGNOSTIC(
            "rejected-unknown-diagnostic",
            "A candidate referenced an unregistered or inactive diagnostic identity."),
    REJECTED_UNSUPPORTED_CLAIM(
            "rejected-unsupported-claim",
            "Required evidence did not support a candidate claim."),
    FALLBACK_NO_ELIGIBLE(
            "fallback-no-eligible",
            "No eligible specialized candidate remained."),
    FALLBACK_CONFLICT(
            "fallback-conflict",
            "Equal-ranked candidates disagreed in meaning or could not combine."),
    FALLBACK_LIMIT(
            "fallback-limit",
            "A deterministic processing limit prevented complete arbitration."),
    FALLBACK_INTERNAL_FAILURE(
            "fallback-internal-failure",
            "An internal arbitration invariant failed and safe generic output was selected."),
    CLASSIFIER_FAILURE(
            "classifier-failure",
            "A classifier failed or returned malformed output and was isolated.");

    private final String key;
    private final String meaning;

    ArbitrationReasonCode(String key, String meaning) {
        this.key = RegistryValidation.identifier(
                key, KeyPatternHolder.KEY, "arbitrationReason.key");
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

    private static final class KeyPatternHolder {
        private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]{0,63}");

        private KeyPatternHolder() {
        }
    }
}
