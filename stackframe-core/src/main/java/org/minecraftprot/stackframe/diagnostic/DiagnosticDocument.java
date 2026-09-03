package org.minecraftprot.stackframe.diagnostic;

/**
 * Sole completed value accepted by renderers. Construction verifies schema 1.0,
 * deep tree identity, all hard limits, post-redaction coordinates, local
 * references, exact omission paths/counts, and the 262,144-byte UTF-8 string
 * budget.
 */
public record DiagnosticDocument(
        SchemaVersion schemaVersion,
        DiagnosticId diagnosticId,
        CorrelationId correlationId,
        Diagnostic root,
        BoundedList<RedactionNotice> redactions,
        BoundedList<Omission> omissions) {

    public DiagnosticDocument {
        schemaVersion = Validation.required(schemaVersion, "$.schemaVersion");
        if (!schemaVersion.equals(SchemaVersion.CURRENT)) {
            throw new SchemaValidationException(
                    "$.schemaVersion",
                    "unsupported version " + schemaVersion + "; expected " + SchemaVersion.CURRENT);
        }
        diagnosticId = Validation.required(diagnosticId, "$.diagnosticId");
        correlationId = Validation.required(correlationId, "$.correlationId");
        root = Validation.required(root, "$.root");
        redactions = Validation.required(redactions, "$.redactions");
        omissions = Validation.required(omissions, "$.omissions");
        Validation.size(redactions.items(), ModelLimits.REDACTION_NOTICES, "$.redactions");
        Validation.size(omissions.items(), ModelLimits.OMISSIONS_PER_SCOPE, "$.omissions");
        DocumentValidator.validate(
                schemaVersion, diagnosticId, correlationId, root, redactions, omissions);
    }
}
