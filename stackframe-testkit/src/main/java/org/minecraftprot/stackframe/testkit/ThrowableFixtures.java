package org.minecraftprot.stackframe.testkit;

/** Fresh, deterministic throwable graphs for normalizer and adapter contract tests. */
public final class ThrowableFixtures {
    public static final String SYNTHETIC_SECRET = "STACKFRAME_TEST_SECRET_DO_NOT_USE";
    private static final StackTraceElement[] NO_FRAMES = new StackTraceElement[0];

    private ThrowableFixtures() {
    }

    public static Throwable wrapperWithSuppressed() {
        var cause = fixed(new IllegalArgumentException("invalid example:entry"), "Registry.java", 17);
        var wrapper = fixed(new IllegalStateException("server startup failed", cause), "Main.java", 42);
        wrapper.addSuppressed(fixed(new IllegalStateException("secondary load failure"),
                "Loader.java", 8));
        wrapper.addSuppressed(fixed(new IllegalStateException("cleanup failed"),
                "Loader.java", 12));
        return wrapper;
    }

    public static Throwable causeAndSuppressedCycle() {
        var outer = new MutableCauseThrowable("outer");
        var inner = new MutableCauseThrowable("inner");
        outer.cause = inner;
        inner.addSuppressed(outer);
        outer.setStackTrace(NO_FRAMES);
        inner.setStackTrace(NO_FRAMES);
        return outer;
    }

    public static Throwable sharedCauseAndSuppressed() {
        var shared = fixed(new IllegalArgumentException("shared"), "Shared.java", 3);
        var outer = fixed(new IllegalStateException("outer", shared), "Outer.java", 4);
        outer.addSuppressed(shared);
        return outer;
    }

    /** The returned graph has exactly {@code nodes} cause-linked throwables. */
    public static Throwable deepCauseChain(int nodes) {
        requireCount(nodes, 4_096);
        Throwable chain = null;
        for (var index = nodes; index >= 1; index--) {
            var next = new IllegalStateException("depth-" + index, chain);
            next.setStackTrace(NO_FRAMES);
            chain = next;
        }
        return chain;
    }

    public static Throwable largeStack(int frames) {
        requireCount(frames, 10_000);
        var entries = new StackTraceElement[frames];
        for (var index = 0; index < frames; index++) {
            entries[index] = new StackTraceElement(
                    "example.fixture.Frame" + index,
                    "call",
                    "Frame" + index + ".java",
                    index + 1);
        }
        var failure = new IllegalStateException("large synthetic stack");
        failure.setStackTrace(entries);
        return failure;
    }

    public static Throwable unusualUnicode() {
        return fixed(new IllegalArgumentException("e\u0301 界 👩🏽‍💻 malformed:\uD800"),
                "Unicode.java", 9);
    }

    public static Throwable missingMetadata() {
        var failure = new IllegalStateException((String) null);
        failure.setStackTrace(NO_FRAMES);
        return failure;
    }

    public static Throwable secretBearing() {
        return fixed(new IllegalStateException(
                "token=" + SYNTHETIC_SECRET + " at /fixture/private/server.properties"),
                "ServerConfig.java", 23);
    }

    public static Throwable missingMod() {
        var cause = fixed(new ClassNotFoundException("example.lib.Api"), "Resolver.java", 6);
        return fixed(new IllegalStateException("example-addon requires example-lib", cause),
                "ModLoader.java", 34);
    }

    public static Throwable mixinFailure() {
        var cause = fixed(new IllegalArgumentException("injection target not found"),
                "MixinEngine.java", 51);
        return fixed(new IllegalStateException("mixin apply failed for example.Server", cause),
                "ModLoader.java", 39);
    }

    public static Throwable registryDecodeFailure() {
        var cause = fixed(new IllegalArgumentException("unknown key example:missing"),
                "Registry.java", 18);
        return fixed(new IllegalStateException("registry decode failed", cause),
                "WorldLoader.java", 62);
    }

    public static Throwable datapackLoadFailure() {
        var cause = fixed(new IllegalArgumentException("malformed value in example:item"),
                "DataReader.java", 11);
        return fixed(new IllegalStateException("datapack load failed", cause),
                "WorldLoader.java", 68);
    }

    private static <T extends Throwable> T fixed(T failure, String fileName, int lineNumber) {
        failure.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("example.fixture.Server", "load", fileName, lineNumber)
        });
        return failure;
    }

    private static void requireCount(int count, int maximum) {
        if (count < 1 || count > maximum) {
            throw new IllegalArgumentException("fixture count must be in [1, " + maximum + "]");
        }
    }

    private static final class MutableCauseThrowable extends Throwable {
        private Throwable cause;

        private MutableCauseThrowable(String message) {
            super(message);
        }

        @Override
        public Throwable getCause() {
            return cause;
        }
    }
}
