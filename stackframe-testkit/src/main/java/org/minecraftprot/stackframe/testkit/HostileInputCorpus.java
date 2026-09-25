package org.minecraftprot.stackframe.testkit;

import java.util.SplittableRandom;

/** Reproducible, bounded adversarial values shared by core and renderer tests. */
public final class HostileInputCorpus {
    public static final long DEFAULT_SEED = 0x43F00D5EEDL;
    public static final int DEFAULT_CASES = 64;
    public static final int MAX_CASES = 5_000;

    private static final String[] RAW_ATOMS = {
        "a", "'", "\"", "\\", "../", "</script>", "\n", "\r", "\t", "\u0000",
        "\u001B[2J", "\u009B", "\u202E", "\uD800", "\uDC00", "e\u0301", "界", "👩🏽‍💻"
    };
    private static final String[] MODEL_ATOMS = {
        "a", "'", "\"", "\\", "../", "</script>", "\n", "e\u0301", "界",
        "👩🏽‍💻", "\u200D", "\uFE0F", "\u200B", "🏴‍☠️", " "
    };

    private HostileInputCorpus() {
    }

    /** Defaults stay cheap in CI; local runs may select 1..5,000 cases. */
    public static int caseCount() {
        var text = System.getenv("STACKFRAME_FUZZ_CASES");
        if (text == null || text.isBlank()) {
            return DEFAULT_CASES;
        }
        var count = Integer.parseInt(text);
        if (count < 1 || count > MAX_CASES) {
            throw new IllegalArgumentException("STACKFRAME_FUZZ_CASES must be in [1, "
                    + MAX_CASES + "]");
        }
        return count;
    }

    public static int caseStart() {
        var text = System.getenv("STACKFRAME_FUZZ_START");
        if (text == null || text.isBlank()) {
            return 0;
        }
        var start = Integer.parseInt(text);
        if (start < 0 || start > 1_000_000) {
            throw new IllegalArgumentException(
                    "STACKFRAME_FUZZ_START must be in [0, 1000000]");
        }
        return start;
    }

    public static long baseSeed() {
        var text = System.getenv("STACKFRAME_FUZZ_SEED");
        return text == null || text.isBlank() ? DEFAULT_SEED : Long.decode(text);
    }

    /** Independent case seeds make one failure reproducible without prior cases. */
    public static long caseSeed(long baseSeed, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        var value = baseSeed + 0x9E3779B97F4A7C15L * (index + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static String failureContext(long baseSeed, int index) {
        return "STACKFRAME_FUZZ_SEED=0x" + Long.toHexString(baseSeed)
                + " STACKFRAME_FUZZ_START=" + index
                + " STACKFRAME_FUZZ_CASES=1"
                + " case=" + index + " caseSeed=0x"
                + Long.toHexString(caseSeed(baseSeed, index));
    }

    /** Includes controls and malformed UTF-16; never pass directly to a renderer. */
    public static String rawText(long seed, int maximumUtf16Units) {
        return text(seed, maximumUtf16Units, RAW_ATOMS, false);
    }

    /** Valid completed-model text, including hostile-looking JSON and Unicode. */
    public static String modelText(long seed, int maximumUtf16Units, boolean allowNewline) {
        return text(seed, maximumUtf16Units, MODEL_ATOMS, !allowNewline);
    }

    private static String text(long seed, int maximum, String[] atoms, boolean omitNewline) {
        if (maximum < 1 || maximum > 16_384) {
            throw new IllegalArgumentException("maximum must be in [1, 16384]");
        }
        var random = new SplittableRandom(seed);
        var target = random.nextInt(12) == 0
                ? maximum : random.nextInt(1, Math.min(maximum, 256) + 1);
        var result = new StringBuilder("x");
        while (result.length() < target) {
            var atom = atoms[random.nextInt(atoms.length)];
            if (omitNewline && atom.equals("\n")) {
                atom = "n";
            }
            if (result.length() + atom.length() > target) {
                result.append('a');
            } else {
                result.append(atom);
            }
        }
        return result.toString();
    }

    /** A fresh throwable graph with cycles, shared nodes, malformed frames, and oversize scalars. */
    public static Throwable throwable(long seed) {
        var random = new SplittableRandom(seed);
        var nodeCount = random.nextInt(1, 21);
        var nodes = new HostileThrowable[nodeCount];
        for (var index = 0; index < nodeCount; index++) {
            var messageLimit = index == 0 && random.nextInt(8) == 0 ? 16_384 : 512;
            var message = index == 0
                    ? "\u001B[2J\nerror[SF9999]: forged "
                            + rawText(random.nextLong(), messageLimit)
                    : rawText(random.nextLong(), messageLimit);
            var frames = random.nextInt(16) == 0 ? null
                    : frames(random, random.nextInt(12) == 0 ? 128 : random.nextInt(0, 25));
            nodes[index] = new HostileThrowable(message, frames,
                    random.nextInt(20) == 0, random.nextInt(20) == 0,
                    random.nextInt(20) == 0);
        }
        for (var index = 0; index < nodeCount; index++) {
            nodes[index].cause = nodes[random.nextInt(nodeCount)];
            var suppressed = random.nextInt(0, 5);
            for (var child = 0; child < suppressed; child++) {
                var target = nodes[random.nextInt(nodeCount)];
                if (target != nodes[index]) {
                    nodes[index].addSuppressed(target);
                }
            }
        }
        return nodes[0];
    }

    private static StackTraceElement[] frames(SplittableRandom random, int count) {
        var frames = new StackTraceElement[count];
        for (var index = 0; index < count; index++) {
            if (random.nextInt(20) != 0) {
                frames[index] = new StackTraceElement(
                        "fuzz." + rawText(random.nextLong(), 128),
                        "run", rawText(random.nextLong(), 128), random.nextInt(-1, 1000));
            }
        }
        return frames;
    }

    private static final class HostileThrowable extends Throwable {
        private final String message;
        private final StackTraceElement[] frames;
        private final boolean unreadableMessage;
        private final boolean unreadableFrames;
        private final boolean unreadableCause;
        private Throwable cause;

        private HostileThrowable(String message, StackTraceElement[] frames,
                boolean unreadableMessage, boolean unreadableFrames, boolean unreadableCause) {
            super(null, null, true, false);
            this.message = message;
            this.frames = frames;
            this.unreadableMessage = unreadableMessage;
            this.unreadableFrames = unreadableFrames;
            this.unreadableCause = unreadableCause;
        }

        @Override
        public String getMessage() {
            if (unreadableMessage) {
                throw new IllegalStateException("unreadable message");
            }
            return message;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            if (unreadableFrames) {
                throw new AssertionError("unreadable frames");
            }
            return frames;
        }

        @Override
        public synchronized Throwable getCause() {
            if (unreadableCause) {
                throw new IllegalStateException("unreadable cause");
            }
            return cause;
        }
    }
}
