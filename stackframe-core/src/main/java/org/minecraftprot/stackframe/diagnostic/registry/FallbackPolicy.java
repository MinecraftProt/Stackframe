package org.minecraftprot.stackframe.diagnostic.registry;

import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;

/** Safe degradation contract when this diagnostic cannot be selected or completed. */
public record FallbackPolicy(
        String description,
        DiagnosticCode fallbackCode,
        boolean preservesOriginalEvent,
        boolean preservesCompleteDetails,
        boolean permitsInferredCauseOrBlame) {

    public FallbackPolicy {
        description = RegistryValidation.text(description, "fallback.description");
        RegistryValidation.required(fallbackCode, "fallback.code");
        DiagnosticArea.forCode(fallbackCode);
        if (!preservesOriginalEvent || !preservesCompleteDetails) {
            throw new RegistryValidationException(
                    "fallback must preserve the original event and complete details");
        }
        if (permitsInferredCauseOrBlame) {
            throw new RegistryValidationException(
                    "fallback must not infer cause or blame");
        }
    }
}
