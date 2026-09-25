package org.minecraftprot.stackframe.fabric;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

/** Installs the server observer before mod initialization and retries at the main entrypoint. */
public final class FabricCaptureBootstrap {
    private static Installation installation;

    private FabricCaptureBootstrap() {
    }

    public static synchronized void install() {
        if (installation != null) {
            return;
        }
        FabricDiagnosticPipeline pipeline = null;
        Log4jFailureCapture capture = null;
        try {
            pipeline = new FabricDiagnosticPipeline();
            capture = Log4jFailureCapture.install(
                    (LoggerContext) LogManager.getContext(false), pipeline::accept);
            var installed = new Installation(capture, pipeline);
            Runtime.getRuntime().addShutdownHook(new Thread(
                    installed::close, "stackframe-diagnostic-shutdown"));
            installation = installed;
        } catch (RuntimeException | LinkageError failure) {
            if (capture != null) {
                try {
                    capture.close();
                } catch (RuntimeException | LinkageError ignored) {
                    // Bootstrap failure must not change Minecraft's logging path.
                }
            }
            if (pipeline != null) {
                try {
                    pipeline.close();
                } catch (RuntimeException | LinkageError ignored) {
                    // The original event is still handled by Log4j.
                }
            }
            System.err.println("[Stackframe] Failure capture unavailable; original server logging continues.");
        }
    }

    private record Installation(
            Log4jFailureCapture capture, FabricDiagnosticPipeline pipeline) {
        private void close() {
            try {
                capture.close();
            } finally {
                pipeline.close();
            }
        }
    }
}
