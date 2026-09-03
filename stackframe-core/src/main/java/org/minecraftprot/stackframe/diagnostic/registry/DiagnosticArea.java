package org.minecraftprot.stackframe.diagnostic.registry;

import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;

/** Governed allocation area for one accepted {@code SFxxxx} range. */
public enum DiagnosticArea {
    GENERIC_INTERNAL('0', 0, "Stackframe and generic fallback"),
    LIFECYCLE_STARTUP('1', 0, "Server lifecycle and startup"),
    DATA_RESOURCES('2', 0, "Data, resources, registries, and worlds"),
    MODS_DEPENDENCIES('3', 0, "Mods, mixins, and dependencies"),
    NETWORKING_PLAYERS('4', 0, "Networking and players"),
    STORAGE_ENVIRONMENT('5', 0, "Storage, permissions, and environment");

    private final char rangeDigit;
    private final int minimumSuffix;
    private final String displayName;

    DiagnosticArea(char rangeDigit, int minimumSuffix, String displayName) {
        this.rangeDigit = rangeDigit;
        this.minimumSuffix = minimumSuffix;
        this.displayName = displayName;
    }

    public String range() {
        return "SF" + rangeDigit + "xxx";
    }

    public String displayName() {
        return displayName;
    }

    public char rangeDigit() {
        return rangeDigit;
    }

    public int minimumSuffix() {
        return minimumSuffix;
    }

    public int maximumSuffix() {
        return 999;
    }

    public boolean contains(DiagnosticCode code) {
        RegistryValidation.required(code, "code");
        var value = code.value();
        var suffix = Integer.parseInt(value.substring(3));
        return value.charAt(2) == rangeDigit && suffix >= minimumSuffix;
    }

    public DiagnosticCode code(int suffix) {
        if (suffix < minimumSuffix || suffix > maximumSuffix()) {
            throw new RegistryValidationException(
                    "suffix is outside allocated range " + range());
        }
        var value = new char[] {
            'S',
            'F',
            rangeDigit,
            (char) ('0' + suffix / 100),
            (char) ('0' + suffix / 10 % 10),
            (char) ('0' + suffix % 10)
        };
        return new DiagnosticCode(new String(value));
    }

    public static DiagnosticArea forCode(DiagnosticCode code) {
        RegistryValidation.required(code, "code");
        for (var area : values()) {
            if (area.contains(code)) {
                return area;
            }
        }
        throw new RegistryValidationException(
                code.value() + " is outside the allocated SF0xxx-SF5xxx ranges");
    }
}
