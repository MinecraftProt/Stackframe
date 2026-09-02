package org.minecraftprot.stackframe.diagnostic;

/** Loader-neutral evidence category; it describes but does not arbitrate a claim. */
public enum EvidenceKind {
    TYPED_FAILURE,
    STRUCTURED_METADATA,
    VALIDATED_CONTENT,
    MAPPED_SOURCE,
    MESSAGE_PATTERN,
    OTHER
}
