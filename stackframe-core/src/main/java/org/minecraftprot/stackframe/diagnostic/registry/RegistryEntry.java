package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.Optional;
import java.util.regex.Pattern;
import org.minecraftprot.stackframe.diagnostic.CatalogText;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;

/** One governed diagnostic identity and its classification and safety contracts. */
public record RegistryEntry(
        DiagnosticCode code,
        String symbolicKey,
        DiagnosticArea area,
        String owner,
        CatalogText title,
        String meaning,
        EvidenceRequirement evidence,
        FallbackPolicy fallback,
        RemediationPolicy remediation,
        DiagnosticLifecycle lifecycle,
        Optional<DiagnosticCode> replacementCode,
        String documentationAnchor) {

    private static final Pattern SYMBOLIC_KEY =
            Pattern.compile("[a-z][a-z0-9.-]{0,95}");
    private static final Pattern OWNER =
            Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern ANCHOR =
            Pattern.compile("[a-z][a-z0-9-]{0,95}");
    private static final String GENERIC_FALLBACK = "SF0001";

    public RegistryEntry {
        RegistryValidation.required(code, "code");
        symbolicKey = RegistryValidation.identifier(
                symbolicKey, SYMBOLIC_KEY, "symbolicKey");
        RegistryValidation.required(area, "area");
        var allocatedArea = DiagnosticArea.forCode(code);
        if (allocatedArea != area) {
            throw new RegistryValidationException(
                    code.value() + " belongs to " + allocatedArea + ", not " + area);
        }
        owner = RegistryValidation.identifier(owner, OWNER, "owner");
        RegistryValidation.required(title, "title");
        if (!title.key().equals(symbolicKey + ".title")) {
            throw new RegistryValidationException(
                    "title key must be symbolicKey + '.title'");
        }
        RegistryValidation.text(title.value(), "title.value");
        var first = title.value().codePointAt(0);
        if (Character.isUpperCase(first)
                || title.value().endsWith(".")
                || title.value().endsWith("!")) {
            throw new RegistryValidationException(
                    "title must be a lowercase operation clause without terminal punctuation");
        }
        meaning = RegistryValidation.text(meaning, "meaning");
        RegistryValidation.required(evidence, "evidence");
        RegistryValidation.required(fallback, "fallback");
        RegistryValidation.required(remediation, "remediation");
        RegistryValidation.required(lifecycle, "lifecycle");
        RegistryValidation.required(replacementCode, "replacementCode");
        documentationAnchor = RegistryValidation.identifier(
                documentationAnchor, ANCHOR, "documentationAnchor");

        if (lifecycle == DiagnosticLifecycle.DEPRECATED) {
            if (replacementCode.isEmpty() || replacementCode.get().equals(code)) {
                throw new RegistryValidationException(
                        "deprecated entries require a different replacement code");
            }
            DiagnosticArea.forCode(replacementCode.orElseThrow());
        } else if (replacementCode.isPresent()) {
            throw new RegistryValidationException(
                    "only deprecated entries may declare a replacement code");
        }

        var specialized = !code.value().equals(GENERIC_FALLBACK)
                && lifecycle != DiagnosticLifecycle.RESERVED;
        if (code.value().equals(GENERIC_FALLBACK) && remediation.safety().mutatesState()) {
            throw new RegistryValidationException(
                    "generic fallback must not authorize state-changing remediation");
        }
        if (specialized
                && (!evidence.supports(EvidenceCapability.IDENTITY)
                        || !evidence.supports(EvidenceCapability.SCOPE))) {
            throw new RegistryValidationException(
                    "specialized entries require identity and scope evidence");
        }
        if (!code.value().equals(GENERIC_FALLBACK)
                && !fallback.fallbackCode().value().equals(GENERIC_FALLBACK)) {
            throw new RegistryValidationException(
                    "specialized entries must degrade to SF0001");
        }
        if (remediation.requiresRemedyEvidence()
                && !evidence.supports(EvidenceCapability.REMEDY)) {
            throw new RegistryValidationException(
                    "remediation requiring remedy evidence must declare REMEDY capability");
        }
    }
}
