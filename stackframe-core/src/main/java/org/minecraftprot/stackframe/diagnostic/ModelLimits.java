package org.minecraftprot.stackframe.diagnostic;

/** Hard maxima for diagnostic schema 1.0. Producers may apply smaller documented budgets. */
public final class ModelLimits {
    public static final int DIAGNOSTIC_NODES = 64;
    public static final int DIAGNOSTIC_DEPTH = 8;
    public static final int LOCATIONS_PER_NODE = 16;
    public static final int EXCERPT_LINES = 32;
    public static final int LABELS_PER_EXCERPT = 64;
    public static final int NOTES_PER_NODE = 32;
    public static final int HELP_PER_NODE = 16;
    public static final int EVIDENCE_PER_NODE = 64;
    public static final int REDACTION_NOTICES = 32;
    public static final int OMISSIONS_PER_SCOPE = 32;
    public static final int TITLE_CODE_POINTS = 200;
    public static final int LOCATION_CODE_POINTS = 1_024;
    public static final int EXCERPT_LINE_CODE_POINTS = 4_096;
    public static final int TEXT_CODE_POINTS = 4_096;
    public static final int DOCUMENT_UTF8_BYTES = 262_144;

    private ModelLimits() {
    }
}
