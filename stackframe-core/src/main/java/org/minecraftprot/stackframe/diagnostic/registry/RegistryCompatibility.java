package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.Comparator;
import java.util.stream.Collectors;

/** Exact compatibility snapshot for stable registry semantics and safety contracts. */
public final class RegistryCompatibility {
    private static final String HEADER = String.join(
            "\t",
            "code",
            "symbolicKey",
            "area",
            "owner",
            "lifecycle",
            "titleKey",
            "title",
            "meaning",
            "evidence",
            "capabilities",
            "fallbackCode",
            "fallback",
            "preservesOriginal",
            "preservesDetails",
            "permitsInference",
            "remediationSafety",
            "remediation",
            "requiresRemedyEvidence",
            "requiresConfirmation",
            "requiresBackup",
            "prohibitsAutomaticExecution",
            "replacementCode",
            "documentationAnchor");

    private RegistryCompatibility() {
    }

    public static String baseline(DiagnosticRegistrySnapshot registry) {
        RegistryValidation.required(registry, "registry");
        var rows = registry.entries().stream()
                .map(RegistryCompatibility::row)
                .collect(Collectors.joining("\n"));
        return HEADER + "\n" + rows + "\n";
    }

    public static void verify(DiagnosticRegistrySnapshot registry, String committedBaseline) {
        RegistryValidation.required(committedBaseline, "committedBaseline");
        var expected = normalize(baseline(registry));
        var actual = normalize(committedBaseline);
        if (!expected.equals(actual)) {
            throw new RegistryValidationException(
                    "diagnostic registry compatibility baseline drifted; "
                            + "review the semantic or safety migration and run "
                            + ":stackframe-core:updateDiagnosticRegistryBaseline intentionally");
        }
    }

    private static String row(RegistryEntry entry) {
        var capabilities = entry.evidence().capabilities().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
        return String.join(
                "\t",
                entry.code().value(),
                entry.symbolicKey(),
                entry.area().name(),
                entry.owner(),
                entry.lifecycle().name(),
                entry.title().key(),
                entry.title().value(),
                entry.meaning(),
                entry.evidence().description(),
                capabilities,
                entry.fallback().fallbackCode().value(),
                entry.fallback().description(),
                Boolean.toString(entry.fallback().preservesOriginalEvent()),
                Boolean.toString(entry.fallback().preservesCompleteDetails()),
                Boolean.toString(entry.fallback().permitsInferredCauseOrBlame()),
                entry.remediation().safety().name(),
                entry.remediation().description(),
                Boolean.toString(entry.remediation().requiresRemedyEvidence()),
                Boolean.toString(entry.remediation().requiresOperatorConfirmation()),
                Boolean.toString(entry.remediation().requiresBackup()),
                Boolean.toString(entry.remediation().prohibitsAutomaticExecution()),
                entry.replacementCode().map(code -> code.value()).orElse(""),
                entry.documentationAnchor());
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n");
    }
}
