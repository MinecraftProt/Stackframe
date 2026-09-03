package org.minecraftprot.stackframe.diagnostic;

/** Why producer input was excluded without retaining the removed content. */
public enum OmissionReason {
    COUNT_LIMIT,
    DEPTH_LIMIT,
    TEXT_LIMIT,
    BYTE_BUDGET,
    REDACTION_POLICY,
    INVALID_INPUT
}
