package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/** Exact compatibility snapshot for stable registry semantics and safety contracts. */
public final class RegistryCompatibility {
    private static final String ENTRY_HEADER = String.join(
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
            "remediationActions",
            "remediationSafety",
            "remediationExplanation",
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
        var areas = Arrays.stream(DiagnosticArea.values())
                .sorted(Comparator.comparingInt(DiagnosticArea::rangeDigit))
                .map(area -> String.join(
                        "\t",
                        area.name(),
                        Character.toString(area.rangeDigit()),
                        Integer.toString(area.minimumSuffix()),
                        Integer.toString(area.maximumSuffix()),
                        area.range(),
                        area.displayName()))
                .collect(Collectors.joining("\n"));
        var entries = registry.entries().stream()
                .map(RegistryCompatibility::entryRow)
                .collect(Collectors.joining("\n"));
        var classifiers = registry.classifiers().stream()
                .map(classifier -> String.join(
                        "\t",
                        classifier.classifierKey(),
                        classifier.diagnosticCode().value(),
                        Integer.toString(classifier.precedence()),
                        classifier.combinationPolicy().name()))
                .collect(Collectors.joining("\n"));
        var combinationPolicies = Arrays.stream(CombinationPolicy.values())
                .sorted(Comparator.comparing(Enum::name))
                .map(policy -> policy.name() + "\t" + policy.meaning())
                .collect(Collectors.joining("\n"));
        var arbitrationReasons = Arrays.stream(ArbitrationReasonCode.values())
                .sorted(Comparator.comparing(ArbitrationReasonCode::key))
                .map(reason -> String.join(
                        "\t",
                        reason.key(),
                        reason.scope().name(),
                        reason.meaning()))
                .collect(Collectors.joining("\n"));
        var remediationActions = Arrays.stream(RemediationAction.values())
                .sorted(Comparator.comparing(Enum::name))
                .map(RegistryCompatibility::remediationActionRow)
                .collect(Collectors.joining("\n"));
        return new StringBuilder()
                .append("[areas]\n")
                .append("area\trangeDigit\tminimumSuffix\tmaximumSuffix\trange\tmeaning\n")
                .append(areas)
                .append("\n[entries]\n")
                .append(ENTRY_HEADER)
                .append('\n')
                .append(entries)
                .append("\n[classifiers]\n")
                .append("classifierKey\tdiagnosticCode\tprecedence\tcombinationPolicy\n")
                .append(classifiers)
                .append("\n[combinationPolicies]\n")
                .append("combinationPolicy\tmeaning\n")
                .append(combinationPolicies)
                .append("\n[arbitrationReasons]\n")
                .append("reasonCode\tscope\tmeaning\n")
                .append(arbitrationReasons)
                .append("\n[arbitrationReasonAssignment]\n")
                .append("contractId\tfields\tscopeRequired\treasonCardinality\tscopeMatch\n")
                .append(ArbitrationReasonAssignment.CONTRACT_ID)
                .append("\tscope,reason\ttrue\texactly-one\ttrue\n")
                .append("\n[remediationActions]\n")
                .append("action\tsafety\trequiresRemedyEvidence\trequiresConfirmation")
                .append("\trequiresBackup\tmeaning\n")
                .append(remediationActions)
                .append('\n')
                .toString();
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

    private static String entryRow(RegistryEntry entry) {
        var capabilities = entry.evidence().capabilities().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
        var remediationActions = entry.remediation().actions().stream()
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
                remediationActions,
                entry.remediation().safety().name(),
                entry.remediation().explanation(),
                Boolean.toString(entry.remediation().requiresRemedyEvidence()),
                Boolean.toString(entry.remediation().requiresOperatorConfirmation()),
                Boolean.toString(entry.remediation().requiresBackup()),
                Boolean.toString(entry.remediation().prohibitsAutomaticExecution()),
                entry.replacementCode().map(code -> code.value()).orElse(""),
                entry.documentationAnchor());
    }

    private static String remediationActionRow(RemediationAction action) {
        return String.join(
                "\t",
                action.name(),
                action.safety().name(),
                Boolean.toString(action.requiresRemedyEvidence()),
                Boolean.toString(action.requiresOperatorConfirmation()),
                Boolean.toString(action.requiresBackup()),
                action.meaning());
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n");
    }
}
