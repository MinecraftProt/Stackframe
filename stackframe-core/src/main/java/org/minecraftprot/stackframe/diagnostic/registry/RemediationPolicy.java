package org.minecraftprot.stackframe.diagnostic.registry;

/** Declarative remediation ceiling; registry entries cannot execute operator actions. */
public record RemediationPolicy(
        String description,
        RemediationSafety safety,
        boolean requiresRemedyEvidence,
        boolean requiresOperatorConfirmation,
        boolean requiresBackup,
        boolean prohibitsAutomaticExecution) {

    public RemediationPolicy {
        description = RegistryValidation.text(description, "remediation.description");
        RegistryValidation.required(safety, "remediation.safety");
        if (!prohibitsAutomaticExecution) {
            throw new RegistryValidationException(
                    "remediation must prohibit automatic execution");
        }
        if (safety == RemediationSafety.NONE
                && (requiresRemedyEvidence || requiresOperatorConfirmation || requiresBackup)) {
            throw new RegistryValidationException(
                    "NONE remediation cannot declare action prerequisites");
        }
        if (safety.mutatesState()
                && (!requiresRemedyEvidence || !requiresOperatorConfirmation || !requiresBackup)) {
            throw new RegistryValidationException(
                    "state-changing remediation requires remedy evidence, confirmation, and backup");
        }
    }

    public static RemediationPolicy none(String description) {
        return new RemediationPolicy(
                description, RemediationSafety.NONE, false, false, false, true);
    }

    public static RemediationPolicy inspectOnly(String description) {
        return new RemediationPolicy(
                description, RemediationSafety.INSPECT_ONLY, false, false, false, true);
    }
}
