package org.minecraftprot.stackframe.normalization;

import java.util.Objects;
import java.util.Optional;

/** Presence and readability state for one throwable message. */
public record NormalizedMessage(State state, Optional<NormalizedText> text) {
    public enum State {
        PRESENT,
        ABSENT,
        UNREADABLE
    }

    public NormalizedMessage {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(text, "text");
        if ((state == State.PRESENT) != text.isPresent()) {
            throw new IllegalArgumentException("text must be present exactly when state is PRESENT");
        }
    }

    public static NormalizedMessage present(NormalizedText text) {
        return new NormalizedMessage(State.PRESENT, Optional.of(text));
    }

    public static NormalizedMessage absent() {
        return new NormalizedMessage(State.ABSENT, Optional.empty());
    }

    public static NormalizedMessage unreadable() {
        return new NormalizedMessage(State.UNREADABLE, Optional.empty());
    }
}
