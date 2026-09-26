package org.minecraftprot.stackframe.fabric.client;

/** Verified client observation point; wording must not assign an unproven cause. */
public enum ClientFailurePhase {
    LOGGED("A client failure was observed in an error log."),
    RESOURCE_RELOAD("A client resource reload failed."),
    CONNECTION("A client connection operation failed."),
    CRASH_REPORT("Minecraft constructed a client crash report."),
    CRASH("The client entered its original crash route.");

    private final String safeDescription;

    ClientFailurePhase(String safeDescription) {
        this.safeDescription = safeDescription;
    }

    public String safeDescription() {
        return safeDescription;
    }
}
