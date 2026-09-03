package org.minecraftprot.stackframe.diagnostic.registry;

/**
 * Governed remediation semantics. Unsupported actions such as deleting world
 * data, disabling security, broad permission changes, or automatic downloads are
 * intentionally absent.
 */
public enum RemediationAction {
    NONE(
            RemediationSafety.NONE,
            false,
            false,
            false,
            "No operator remediation is authorized."),
    INSPECT_CORRELATED_TRACE(
            RemediationSafety.INSPECT_ONLY,
            false,
            false,
            false,
            "Inspect the correlated trace."),
    CREATE_SANITIZED_SUPPORT_BUNDLE(
            RemediationSafety.INSPECT_ONLY,
            false,
            false,
            false,
            "Create a sanitized local support bundle."),
    VALIDATE_CONFIGURATION(
            RemediationSafety.INSPECT_ONLY,
            false,
            false,
            false,
            "Validate configuration without changing it."),
    RESTART_SERVER(
            RemediationSafety.REVERSIBLE_STATE_CHANGE,
            true,
            true,
            false,
            "Restart the server after verified prerequisites are satisfied."),
    EDIT_CONFIGURATION(
            RemediationSafety.REVERSIBLE_STATE_CHANGE,
            true,
            true,
            true,
            "Edit a bounded configuration value after preserving its prior state."),
    RESTORE_FROM_BACKUP(
            RemediationSafety.DESTRUCTIVE_STATE_CHANGE,
            true,
            true,
            true,
            "Restore a named artifact only from a verified compatible backup."),
    REMOVE_OR_REPLACE_COMPONENT(
            RemediationSafety.DESTRUCTIVE_STATE_CHANGE,
            true,
            true,
            true,
            "Remove or replace a named component only after backup and compatibility checks.");

    private final RemediationSafety safety;
    private final boolean requiresRemedyEvidence;
    private final boolean requiresOperatorConfirmation;
    private final boolean requiresBackup;
    private final String meaning;

    RemediationAction(
            RemediationSafety safety,
            boolean requiresRemedyEvidence,
            boolean requiresOperatorConfirmation,
            boolean requiresBackup,
            String meaning) {
        this.safety = safety;
        this.requiresRemedyEvidence = requiresRemedyEvidence;
        this.requiresOperatorConfirmation = requiresOperatorConfirmation;
        this.requiresBackup = requiresBackup;
        this.meaning = meaning;
    }

    public RemediationSafety safety() {
        return safety;
    }

    public boolean requiresRemedyEvidence() {
        return requiresRemedyEvidence;
    }

    public boolean requiresOperatorConfirmation() {
        return requiresOperatorConfirmation;
    }

    public boolean requiresBackup() {
        return requiresBackup;
    }

    public String meaning() {
        return meaning;
    }
}
