package org.minecraftprot.stackframe.matrix;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Test-only mod that emits a typed failure from one selected server phase. */
public final class FailureFixture implements ModInitializer, PreLaunchEntrypoint {
    private static final Logger LOGGER = LogManager.getLogger("stackframe.matrix.fixture");
    private static final String SCENARIO = System.getenv("STACKFRAME_MATRIX_SCENARIO");
    private static final AtomicBoolean EMITTED = new AtomicBoolean();

    @Override
    public void onPreLaunch() {
        emit("mod-loading");
    }

    @Override
    public void onInitialize() {
        emit("startup");
    }

    public static boolean isScenario(String name) {
        return name.equals(SCENARIO);
    }

    public static void emit(String phase) {
        if (!isScenario(phase) || !EMITTED.compareAndSet(false, true)) {
            return;
        }
        var failure = new IllegalStateException("SF_MATRIX_DETAIL_" + phase);
        LOGGER.error("SF_MATRIX_ORIGINAL_{}", phase, failure);
        LOGGER.error("SF_MATRIX_REPEAT_{}", phase, failure);
        System.out.println("SF_MATRIX_EMITTED_" + phase);
    }

    public static void throwFromMixin() {
        if (isScenario("mixin") && EMITTED.compareAndSet(false, true)) {
            throw new IllegalStateException("SF_MATRIX_DETAIL_mixin");
        }
    }
}
