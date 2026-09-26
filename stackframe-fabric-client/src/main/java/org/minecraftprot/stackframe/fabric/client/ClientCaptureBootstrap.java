package org.minecraftprot.stackframe.fabric.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

/** Installs early, then retries at client initialization if logging was unavailable. */
public final class ClientCaptureBootstrap {
    private static volatile Installation installation;

    private ClientCaptureBootstrap() {
    }

    public static synchronized void install() {
        if (installation != null) {
            return;
        }
        ClientDiagnosticPipeline pipeline = null;
        ClientLog4jCapture capture = null;
        try {
            pipeline = new ClientDiagnosticPipeline();
            var readyPipeline = pipeline;
            capture = ClientLog4jCapture.installWithImportance(
                    (LoggerContext) LogManager.getContext(false),
                    (throwable, ignoredImportance) ->
                            readyPipeline.accept(throwable, ClientFailurePhase.LOGGED));
            var installed = new Installation(capture, pipeline);
            Runtime.getRuntime().addShutdownHook(new Thread(
                    installed::close, "stackframe-client-diagnostic-shutdown"));
            installation = installed;
        } catch (Throwable ignored) {
            if (capture != null) {
                try {
                    capture.close();
                } catch (Throwable alsoIgnored) {
                    // Never interfere with the original logger.
                }
            }
            if (pipeline != null) {
                try {
                    pipeline.close();
                } catch (Throwable alsoIgnored) {
                    // Never interfere with client initialization.
                }
            }
            System.err.println("[Stackframe] Client failure capture unavailable; original client behavior continues.");
        }
    }

    /** Called by narrow hooks; no installation, rendering, file I/O, or UI work occurs here. */
    public static void observe(Throwable throwable, ClientFailurePhase phase) {
        var installed = installation;
        if (installed == null) {
            return;
        }
        try {
            installed.pipeline().accept(throwable, phase);
        } catch (Throwable ignored) {
            // Original reload, connection, and crash handling continues unchanged.
        }
    }

    private record Installation(ClientLog4jCapture capture,
            ClientDiagnosticPipeline pipeline) {
        private void close() {
            try {
                capture.close();
            } finally {
                pipeline.close();
            }
        }
    }
}
