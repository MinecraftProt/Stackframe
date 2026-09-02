package org.minecraftprot.stackframe.diagnostic;

/**
 * Logical diagnostic schema version, independent of artifact versions and
 * diagnostic codes. This implementation produces schema {@value #CURRENT_TEXT}.
 */
public record SchemaVersion(int major, int minor) {
    public static final String CURRENT_TEXT = "1.0";
    public static final SchemaVersion CURRENT = new SchemaVersion(1, 0);

    public SchemaVersion {
        if (major < 0 || minor < 0) {
            throw new SchemaValidationException("$.schemaVersion", "major and minor must be non-negative");
        }
    }

    public String value() {
        return major + "." + minor;
    }

    @Override
    public String toString() {
        return value();
    }
}
