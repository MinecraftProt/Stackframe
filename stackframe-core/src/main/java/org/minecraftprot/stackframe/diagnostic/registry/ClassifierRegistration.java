package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.regex.Pattern;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;

/** Stable classifier identity and reviewed non-evidentiary arbitration metadata. */
public record ClassifierRegistration(
        String classifierKey,
        DiagnosticCode diagnosticCode,
        int precedence,
        CombinationPolicy combinationPolicy) {

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9.-]{0,95}");

    public ClassifierRegistration {
        classifierKey = RegistryValidation.identifier(classifierKey, KEY, "classifierKey");
        RegistryValidation.required(diagnosticCode, "diagnosticCode");
        DiagnosticArea.forCode(diagnosticCode);
        if (precedence < -100 || precedence > 100) {
            throw new RegistryValidationException(
                    "classifier precedence must be between -100 and 100");
        }
        RegistryValidation.required(combinationPolicy, "combinationPolicy");
    }
}
