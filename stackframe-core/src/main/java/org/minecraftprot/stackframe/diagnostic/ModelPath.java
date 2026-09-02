package org.minecraftprot.stackframe.diagnostic;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Absolute logical path rooted at {@code $}. Item indices address retained
 * producer-ordered {@link BoundedList#items()} values and never removed content.
 */
public record ModelPath(String value) {
    private static final Pattern PATH_PATTERN =
            Pattern.compile("\\$(?:\\.[A-Za-z][A-Za-z0-9]*(?:\\[(?:0|[1-9][0-9]*)])?)+");
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "diagnosticId", "correlationId", "root", "redactions", "omissions",
            "severity", "code", "title", "locations", "notes", "help", "trace", "evidence",
            "confidence", "children", "items", "omittedCount", "id", "kind", "display",
            "position", "excerpt", "evidenceIds", "startLine", "lines", "labels", "lineNumber",
            "text", "range", "style", "message", "summary", "source", "assessmentId",
            "classifierId", "policyId", "state", "totalFrames", "shownFrames", "omittedFrames",
            "omittedCauses", "destination", "recordId", "relation", "diagnostic", "marker",
            "transformation", "count", "origin", "sensitivity", "disposition", "value", "key",
            "affectedPath", "reason", "start", "end", "line", "column", "category");

    public ModelPath {
        Validation.required(value, "$.modelPath");
        if (!PATH_PATTERN.matcher(value).matches()) {
            throw new OmissionValidationException("$.modelPath", "is not a valid absolute model path");
        }
        for (var segment : value.substring(2).split("\\.")) {
            var field = segment.replaceFirst("\\[[0-9]+]$", "");
            if (!FIELDS.contains(field)) {
                throw new OmissionValidationException(
                        "$.modelPath", "contains unknown schema field '" + field + "'");
            }
            if (segment.indexOf('[') >= 0 && !field.equals("items")) {
                throw new OmissionValidationException(
                        "$.modelPath", "only an items segment may carry a retained-item index");
            }
        }
    }
}
