package org.minecraftprot.stackframe.diagnostic.registry;

/**
 * One immutable reason assigned to one arbitration outcome subject.
 *
 * <p>The singular {@code reason} field makes zero or multiple reasons
 * unrepresentable for an assignment. Construction rejects a reason whose
 * governed scope does not match the subject.
 */
public record ArbitrationReasonAssignment(
        ArbitrationReasonScope scope,
        ArbitrationReasonCode reason) {

    public static final String CONTRACT_ID = "arbitration-reason-assignment-v1";
    public static final String CONTRACT =
            "One required scope and exactly one required reason; the reason scope must match.";

    public ArbitrationReasonAssignment {
        RegistryValidation.required(scope, "arbitrationReasonAssignment.scope");
        RegistryValidation.required(reason, "arbitrationReasonAssignment.reason");
        if (reason.scope() != scope) {
            throw new RegistryValidationException(
                    "arbitration reason " + reason.key()
                            + " applies to " + reason.scope() + ", not " + scope);
        }
    }

    public static ArbitrationReasonAssignment selectedCandidate(
            ArbitrationReasonCode reason) {
        return new ArbitrationReasonAssignment(
                ArbitrationReasonScope.SELECTED_CANDIDATE, reason);
    }

    public static ArbitrationReasonAssignment nonSelectedCandidate(
            ArbitrationReasonCode reason) {
        return new ArbitrationReasonAssignment(
                ArbitrationReasonScope.NON_SELECTED_CANDIDATE, reason);
    }

    public static ArbitrationReasonAssignment fallbackSelection(
            ArbitrationReasonCode reason) {
        return new ArbitrationReasonAssignment(
                ArbitrationReasonScope.FALLBACK_SELECTION, reason);
    }

    public static ArbitrationReasonAssignment classifierFailure(
            ArbitrationReasonCode reason) {
        return new ArbitrationReasonAssignment(
                ArbitrationReasonScope.CLASSIFIER, reason);
    }
}
