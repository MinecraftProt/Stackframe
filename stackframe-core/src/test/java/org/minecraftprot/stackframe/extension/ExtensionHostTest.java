package org.minecraftprot.stackframe.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.diagnostic.CandidateText;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.EvidenceKind;
import org.minecraftprot.stackframe.diagnostic.RedactionMarker;
import org.minecraftprot.stackframe.diagnostic.Sensitivity;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;
import org.minecraftprot.stackframe.diagnostic.TextDisposition;
import org.minecraftprot.stackframe.diagnostic.registry.EvidenceCapability;

class ExtensionHostTest {
    @Test
    void referenceExtensionReceivesSafeContextAndReturnsTypedFinding() {
        try (var host = new ExtensionHost()) {
            assertEquals(ExtensionHost.RegistrationStatus.REGISTERED,
                    host.register(ReferenceExtension.registration()));
            host.freeze();

            var result = host.inspect(event());
            assertEquals(ExtensionHost.Status.ACCEPTED, result.outcomes().getFirst().status());
            assertEquals("example_mod:illegal-state-context",
                    result.findings().getFirst().code().qualified());
            assertEquals(2, result.findings().getFirst().evidence().size());
            assertEquals(TextDisposition.OMITTED,
                    result.findings().getFirst().evidence().getFirst().value().disposition());
            assertEquals(ExtensionHost.Status.EMPTY,
                    host.inspect(new ExtensionEvent(
                            DisplayText.visible("java.lang.NullPointerException", TextOrigin.EXTERNAL),
                            Map.of())).outcomes().getFirst().status());
        }
    }

    @Test
    void duplicateNamespaceDisablesEveryClaimant() {
        var calls = new AtomicInteger();
        var ns = new ExtensionNamespace("same_mod");
        var code = new ExtensionCode(ns, "finding");
        try (var host = new ExtensionHost()) {
            assertEquals(ExtensionHost.RegistrationStatus.REGISTERED,
                    host.register(new ExtensionRegistration(ns, ExtensionRegistration.API_MAJOR,
                            Set.of(code), event -> {
                        calls.incrementAndGet();
                        return List.of(finding(code));
                    })));
            assertEquals(ExtensionHost.RegistrationStatus.NAMESPACE_COLLISION,
                    host.register(new ExtensionRegistration(ns, ExtensionRegistration.API_MAJOR,
                            Set.of(code), event -> {
                        calls.incrementAndGet();
                        return List.of(finding(code));
                    })));
            host.freeze();

            var result = host.inspect(event());
            assertEquals(ExtensionHost.Status.NAMESPACE_COLLISION,
                    result.outcomes().getFirst().status());
            assertTrue(result.findings().isEmpty());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void faultsAndUndeclaredCodesAreQuarantinedWithoutBlockingOtherExtensions() {
        var bad = new ExtensionNamespace("bad_mod");
        var wrong = new ExtensionNamespace("wrong_mod");
        var good = new ExtensionNamespace("z_good_mod");
        var badCode = new ExtensionCode(bad, "declared");
        var wrongCode = new ExtensionCode(wrong, "other");
        var goodCode = new ExtensionCode(good, "finding");
        try (var host = new ExtensionHost()) {
            host.register(new ExtensionRegistration(bad, ExtensionRegistration.API_MAJOR,
                    Set.of(badCode), event -> {
                throw new IllegalStateException("secret in exception message");
            }));
            host.register(new ExtensionRegistration(wrong, ExtensionRegistration.API_MAJOR,
                    Set.of(new ExtensionCode(wrong, "declared")),
                    event -> List.of(finding(wrongCode))));
            host.register(new ExtensionRegistration(good, ExtensionRegistration.API_MAJOR,
                    Set.of(goodCode),
                    event -> List.of(finding(goodCode))));
            host.freeze();

            var first = host.inspect(event());
            assertEquals(List.of(
                    ExtensionHost.Status.FAILED,
                    ExtensionHost.Status.MALFORMED,
                    ExtensionHost.Status.ACCEPTED),
                    first.outcomes().stream().map(ExtensionHost.Outcome::status).toList());
            assertEquals(1, first.findings().size());
            assertFalse(first.toString().contains("secret in exception message"));

            var second = host.inspect(event());
            assertEquals(List.of(
                    ExtensionHost.Status.QUARANTINED,
                    ExtensionHost.Status.QUARANTINED,
                    ExtensionHost.Status.ACCEPTED),
                    second.outcomes().stream().map(ExtensionHost.Outcome::status).toList());
        }
    }

    @Test
    void slowExtensionTimesOutAndLaterEventsSkipIt() {
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var ns = new ExtensionNamespace("slow_mod");
        var code = new ExtensionCode(ns, "finding");
        var limits = new ExtensionHost.Limits(
                2, 2, 2, 4_096, 1, Duration.ofMillis(100), Duration.ofMillis(300));
        try (var host = new ExtensionHost(limits)) {
            host.register(new ExtensionRegistration(ns, ExtensionRegistration.API_MAJOR,
                    Set.of(code), event -> {
                started.countDown();
                while (true) {
                    try {
                        release.await();
                        return List.of(finding(code));
                    } catch (InterruptedException ignored) {
                        // Deliberately model an uncooperative in-process extension.
                    }
                }
            }));
            host.freeze();
            var before = System.nanoTime();
            var first = host.inspect(event());
            var elapsed = Duration.ofNanos(System.nanoTime() - before);

            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertEquals(ExtensionHost.Status.TIMED_OUT, first.outcomes().getFirst().status());
            assertTrue(elapsed.compareTo(Duration.ofSeconds(2)) < 0);
            assertEquals(ExtensionHost.Status.QUARANTINED,
                    host.inspect(event()).outcomes().getFirst().status());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        } finally {
            release.countDown();
        }
    }

    @Test
    void hostileCandidateTextCannotBecomeDirectOutputOrLeakThroughStatuses() {
        var secret = "private-token\u001B[31m";
        var seenInput = new AtomicReference<String>();
        var ns = new ExtensionNamespace("hostile_mod");
        var code = new ExtensionCode(ns, "finding");
        try (var host = new ExtensionHost()) {
            host.register(new ExtensionRegistration(ns, ExtensionRegistration.API_MAJOR,
                    Set.of(code), event -> {
                seenInput.set(event.failureType().value());
                return List.of(new ExtensionFinding(code, List.of(new ExtensionEvidence(
                            "raw-value", EvidenceKind.OTHER,
                            ExtensionEvidence.Strength.HEURISTIC,
                            Set.of(EvidenceCapability.FACT), new CandidateText(secret)))));
            }));
            host.freeze();
            var result = host.inspect(new ExtensionEvent(
                    DisplayText.redacted(TextOrigin.EXTERNAL, Sensitivity.SECRET,
                            new RedactionMarker("TOKEN")), Map.of()));

            assertEquals(ExtensionHost.Status.ACCEPTED, result.outcomes().getFirst().status());
            assertEquals("<redacted:token>", seenInput.get());
            assertEquals("<omitted:extension_data>",
                    result.findings().getFirst().evidence().getFirst().value().value());
            assertFalse(result.toString().contains(secret));
            assertFalse(result.toString().contains("\u001B"));
        }
    }

    @Test
    void lifecycleAndEvidenceContractsRejectInvalidUsage() {
        var ns = new ExtensionNamespace("sample_mod");
        var code = new ExtensionCode(ns, "finding");
        assertThrows(IllegalArgumentException.class, () -> new ExtensionNamespace("stackframe"));
        assertThrows(IllegalArgumentException.class,
                () -> new ExtensionRegistration(ns, 2, Set.of(code), event -> List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ExtensionRegistration(ns, ExtensionRegistration.API_MAJOR,
                        Set.of(new ExtensionCode(new ExtensionNamespace("other_mod"), "finding")),
                        event -> List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ExtensionEvidence("message", EvidenceKind.MESSAGE_PATTERN,
                        ExtensionEvidence.Strength.DIRECT, Set.of(EvidenceCapability.OWNERSHIP),
                        new CandidateText("unverified message")));
        assertThrows(IllegalArgumentException.class,
                () -> new ExtensionFinding(code, List.of(evidence(), evidence())));

        try (var host = new ExtensionHost()) {
            assertThrows(IllegalStateException.class, () -> host.inspect(event()));
            host.register(new ExtensionRegistration(ns, ExtensionRegistration.API_MAJOR,
                    Set.of(code), event -> List.of()));
            host.freeze();
            assertThrows(IllegalStateException.class,
                    () -> host.register(new ExtensionRegistration(ns, ExtensionRegistration.API_MAJOR,
                            Set.of(code),
                            event -> List.of())));
            host.close();
            assertThrows(IllegalStateException.class, () -> host.inspect(event()));
        }
    }

    @Test
    void evidenceBudgetAndNullReturnQuarantineOnlyTheirOwnExtensions() {
        var longNs = new ExtensionNamespace("long_mod");
        var nullNs = new ExtensionNamespace("null_mod");
        var longCode = new ExtensionCode(longNs, "finding");
        var nullCode = new ExtensionCode(nullNs, "finding");
        var limits = new ExtensionHost.Limits(
                2, 1, 1, 4, 2, Duration.ofMillis(100), Duration.ofMillis(300));
        try (var host = new ExtensionHost(limits)) {
            host.register(new ExtensionRegistration(longNs, ExtensionRegistration.API_MAJOR,
                    Set.of(longCode), event -> List.of(finding(longCode))));
            host.register(new ExtensionRegistration(nullNs, ExtensionRegistration.API_MAJOR,
                    Set.of(nullCode), event -> null));
            host.freeze();

            assertEquals(List.of(ExtensionHost.Status.MALFORMED, ExtensionHost.Status.MALFORMED),
                    host.inspect(event()).outcomes().stream()
                            .map(ExtensionHost.Outcome::status).toList());
            assertEquals(List.of(
                    ExtensionHost.Status.QUARANTINED, ExtensionHost.Status.QUARANTINED),
                    host.inspect(event()).outcomes().stream()
                            .map(ExtensionHost.Outcome::status).toList());
        }
    }

    @Test
    void eventContextIsAnImmutableSnapshot() {
        var context = new HashMap<String, DisplayText>();
        context.put("trace-state", DisplayText.visible("present", TextOrigin.GENERATED));
        var event = new ExtensionEvent(
                DisplayText.visible("java.lang.IllegalStateException", TextOrigin.EXTERNAL),
                context);
        context.put("trace-state", DisplayText.visible("changed", TextOrigin.GENERATED));

        assertEquals("present", event.context().get("trace-state").value());
        assertThrows(UnsupportedOperationException.class,
                () -> event.context().put("new-value",
                        DisplayText.visible("new", TextOrigin.GENERATED)));
    }

    private static ExtensionEvent event() {
        return new ExtensionEvent(
                DisplayText.visible("java.lang.IllegalStateException", TextOrigin.EXTERNAL),
                Map.of("trace-state", DisplayText.visible("present", TextOrigin.GENERATED)));
    }

    private static ExtensionFinding finding(ExtensionCode code) {
        return new ExtensionFinding(code, List.of(evidence()));
    }

    private static ExtensionEvidence evidence() {
        return new ExtensionEvidence("failure-type", EvidenceKind.OTHER,
                ExtensionEvidence.Strength.HEURISTIC, Set.of(EvidenceCapability.FACT),
                new CandidateText("java.lang.IllegalStateException"));
    }
}
