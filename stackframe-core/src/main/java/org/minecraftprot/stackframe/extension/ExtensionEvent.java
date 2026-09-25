package org.minecraftprot.stackframe.extension;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.minecraftprot.stackframe.diagnostic.DisplayText;

/** Bounded, already-redacted technical context supplied by the trusted pipeline. */
public record ExtensionEvent(DisplayText failureType, Map<String, DisplayText> context) {
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9.-]{0,63}");

    public ExtensionEvent {
        if (failureType == null || context == null || context.size() > 16) {
            throw new IllegalArgumentException("invalid extension event");
        }
        var safe = new HashMap<String, DisplayText>();
        context.forEach((key, value) -> {
            if (key == null || !KEY.matcher(key).matches() || value == null) {
                throw new IllegalArgumentException("invalid extension context entry");
            }
            safe.put(key, value);
        });
        context = Map.copyOf(safe);
    }
}
