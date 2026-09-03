package org.minecraftprot.stackframe.diagnostic;

import java.util.Optional;

/**
 * Opaque arbitration result metadata. Renderers preserve it and must not infer,
 * rank, or recompute confidence from these identifiers.
 */
public record ConfidenceReference(
        Optional<OpaqueIdentifier> assessmentId,
        Optional<OpaqueIdentifier> classifierId,
        BoundedList<EvidenceId> evidenceIds,
        Optional<OpaqueIdentifier> policyId) {

    public ConfidenceReference {
        assessmentId = Validation.optional(assessmentId, "$.confidence.assessmentId");
        classifierId = Validation.optional(classifierId, "$.confidence.classifierId");
        evidenceIds = Validation.required(evidenceIds, "$.confidence.evidenceIds");
        Validation.size(evidenceIds.items(), ModelLimits.EVIDENCE_PER_NODE, "$.confidence.evidenceIds");
        policyId = Validation.optional(policyId, "$.confidence.policyId");
    }

    public static ConfidenceReference unassessed() {
        return new ConfidenceReference(
                Optional.empty(), Optional.empty(), BoundedList.empty(), Optional.empty());
    }
}
