package org.minecraftprot.stackframe.renderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Destination-specific terminal evidence and a snapshot of its process environment. */
public record TerminalCapabilities(boolean outputIsTerminal, Map<String, String> environment) {
    private static final List<String> RELEVANT_ENVIRONMENT = List.of(
            "NO_COLOR", "CLICOLOR", "CI", "GITHUB_ACTIONS", "GITLAB_CI", "TF_BUILD",
            "JENKINS_URL", "TEAMCITY_VERSION", "BUILDKITE", "CIRCLECI", "TERM",
            "WT_SESSION", "ANSICON", "ConEmuANSI");

    public TerminalCapabilities {
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        var relevant = new HashMap<String, String>();
        for (var name : RELEVANT_ENVIRONMENT) {
            var value = environment.get(name);
            if (value != null) {
                relevant.put(name, value);
            }
        }
        environment = Map.copyOf(relevant);
    }

    /**
     * Conservatively probes direct {@code System.out} output. Adapters writing to
     * another destination must supply that destination's terminal status instead.
     */
    public static TerminalCapabilities forSystemOut() {
        return new TerminalCapabilities(System.console() != null, System.getenv());
    }
}
