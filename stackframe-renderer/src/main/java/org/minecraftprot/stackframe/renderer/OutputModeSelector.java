package org.minecraftprot.stackframe.renderer;

import java.util.Locale;
import java.util.Map;

/** Resolves an operator preference to a deterministic renderer mode. */
public final class OutputModeSelector {
    private OutputModeSelector() {
    }

    /**
     * Explicit modes win over environment hints, including {@code NO_COLOR}.
     * Automatic mode emits ANSI only when the destination and terminal type are
     * known to support Stackframe's fixed SGR styling.
     */
    public static OutputMode select(
            OutputPreference preference, TerminalCapabilities capabilities) {
        if (preference == null || capabilities == null) {
            throw new IllegalArgumentException("preference and capabilities must not be null");
        }
        if (preference == OutputPreference.PLAIN) {
            return OutputMode.PLAIN;
        }
        if (preference == OutputPreference.ANSI) {
            return OutputMode.ANSI;
        }

        var environment = capabilities.environment();
        if (!capabilities.outputIsTerminal()
                || present(environment.get("NO_COLOR"))
                || isCi(environment)
                || "0".equals(environment.get("CLICOLOR"))) {
            return OutputMode.PLAIN;
        }

        var term = environment.getOrDefault("TERM", "").toLowerCase(Locale.ROOT);
        if (term.equals("dumb") || term.equals("unknown")) {
            return OutputMode.PLAIN;
        }
        if (knownTerminal(term) || windowsAnsiTerminal(environment)) {
            return OutputMode.ANSI;
        }
        return OutputMode.PLAIN;
    }

    private static boolean isCi(Map<String, String> environment) {
        for (var variable : new String[] {
                "GITHUB_ACTIONS", "GITLAB_CI", "TF_BUILD", "JENKINS_URL",
                "TEAMCITY_VERSION", "BUILDKITE", "CIRCLECI"}) {
            if (present(environment.get(variable))) {
                return true;
            }
        }
        var ci = environment.get("CI");
        return present(ci) && !ci.equalsIgnoreCase("false") && !ci.equals("0");
    }

    private static boolean knownTerminal(String term) {
        return term.equals("ansi") || term.equals("linux") || term.equals("cygwin")
                || term.equals("alacritty") || term.equals("kitty") || term.equals("foot")
                || term.startsWith("xterm") || term.startsWith("screen")
                || term.startsWith("tmux") || term.startsWith("rxvt")
                || term.startsWith("konsole") || term.startsWith("vt")
                || term.startsWith("wezterm");
    }

    private static boolean windowsAnsiTerminal(Map<String, String> environment) {
        return present(environment.get("WT_SESSION"))
                || present(environment.get("ANSICON"))
                || "ON".equalsIgnoreCase(environment.get("ConEmuANSI"));
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }
}
