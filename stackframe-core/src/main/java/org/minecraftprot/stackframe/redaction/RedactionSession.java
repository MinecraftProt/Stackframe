package org.minecraftprot.stackframe.redaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.minecraftprot.stackframe.diagnostic.BoundedList;
import org.minecraftprot.stackframe.diagnostic.CandidateText;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.RedactionMarker;
import org.minecraftprot.stackframe.diagnostic.RedactionNotice;
import org.minecraftprot.stackframe.diagnostic.Sensitivity;
import org.minecraftprot.stackframe.diagnostic.TextDisposition;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;

/** One diagnostic's redaction accounting; create a new session for each event. */
public final class RedactionSession {
    private final RedactionPolicy policy;
    private final Map<NoticeKey, Integer> counts = new LinkedHashMap<>();

    RedactionSession(RedactionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public DisplayText transform(CandidateText candidate, RedactionPolicy.FieldKind kind,
            TextOrigin origin) {
        return policy.transform(candidate, kind, origin, this);
    }

    public BoundedList<RedactionNotice> notices() {
        var notices = new ArrayList<RedactionNotice>();
        counts.forEach((key, count) -> notices.add(
                new RedactionNotice(key.marker(), key.disposition(), count)));
        return BoundedList.of(notices);
    }

    DisplayText redacted(TextOrigin origin, Sensitivity sensitivity, String category) {
        var marker = new RedactionMarker(category);
        count(marker, TextDisposition.REDACTED);
        return DisplayText.redacted(origin, sensitivity, marker);
    }

    DisplayText omitted(TextOrigin origin, Sensitivity sensitivity, String category) {
        var marker = new RedactionMarker(category);
        count(marker, TextDisposition.OMITTED);
        return DisplayText.omitted(origin, sensitivity, marker);
    }

    DisplayText generalized(String value, TextOrigin origin, Sensitivity sensitivity,
            String category) {
        var marker = new RedactionMarker(category);
        var display = DisplayText.generalized(value, origin, sensitivity, marker);
        count(marker, TextDisposition.GENERALIZED);
        return display;
    }

    private void count(RedactionMarker marker, TextDisposition disposition) {
        counts.merge(new NoticeKey(marker, disposition), 1, Integer::sum);
    }

    private record NoticeKey(RedactionMarker marker, TextDisposition disposition) {
    }
}
