package org.minecraftprot.stackframe.fabric.config;

/** Safe operator-facing configuration error without echoing an untrusted value. */
public final class ConfigurationProblem extends Exception {
    private final int line;
    private final String key;

    public ConfigurationProblem(int line, String key, String expectation) {
        super("config/stackframe.properties:" + line + " [" + key + "]: " + expectation);
        this.line = line;
        this.key = key;
    }

    public int line() {
        return line;
    }

    public String key() {
        return key;
    }
}
