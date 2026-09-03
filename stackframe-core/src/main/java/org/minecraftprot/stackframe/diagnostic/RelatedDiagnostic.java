package org.minecraftprot.stackframe.diagnostic;

/** One immutable child diagnostic and its explicit semantic relationship. */
public record RelatedDiagnostic(Relation relation, Diagnostic diagnostic) {
    public RelatedDiagnostic {
        relation = Validation.required(relation, "$.relatedDiagnostic.relation");
        diagnostic = Validation.required(diagnostic, "$.relatedDiagnostic.diagnostic");
    }
}
