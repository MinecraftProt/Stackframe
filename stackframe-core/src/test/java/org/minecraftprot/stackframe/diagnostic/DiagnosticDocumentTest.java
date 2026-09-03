package org.minecraftprot.stackframe.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DiagnosticDocumentTest {
    @Test
    void constructsMinimumDocumentWithPresentEmptyCollections() {
        var document = ModelFixtures.document(ModelFixtures.minimalDiagnostic());

        assertEquals(SchemaVersion.CURRENT, document.schemaVersion());
        assertTrue(document.root().locations().items().isEmpty());
        assertTrue(document.root().notes().items().isEmpty());
        assertTrue(document.root().help().items().isEmpty());
        assertTrue(document.root().children().items().isEmpty());
        assertTrue(document.redactions().items().isEmpty());
        assertTrue(document.omissions().items().isEmpty());
    }

    @Test
    void constructsCompleteDocumentAndPreservesNestedMeaning() {
        var document = ModelFixtures.fullDocument();

        assertEquals("server-port", document.root().locations().items().getFirst().id().value());
        assertEquals(10, document.root().locations().items().getFirst()
                .excerpt().orElseThrow().startLine());
        assertEquals(Relation.CAUSE, document.root().children().items().getFirst().relation());
        assertEquals(TraceState.PRESERVED, document.root().trace().state());
        assertEquals(TextDisposition.GENERALIZED,
                document.redactions().items().getFirst().transformation());
    }

    @Test
    void valuesHaveDeterministicStructuralEquality() {
        var first = ModelFixtures.fullDocument();
        var second = ModelFixtures.fullDocument();

        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void boundedListsDefensivelyCopyAndExposeNoMutableView() {
        var source = new ArrayList<>(List.of(new LocationId("first")));
        var bounded = BoundedList.of(source);
        source.add(new LocationId("second"));

        assertEquals(List.of(new LocationId("first")), bounded.items());
        assertThrows(UnsupportedOperationException.class,
                () -> bounded.items().add(new LocationId("third")));
    }

    @Test
    void producerOrderIsPreservedWithoutSorting() {
        var evidence = BoundedList.of(List.of(ModelFixtures.evidence()));
        var second = location("second");
        var first = location("first");
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.of(List.of(second, first)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                evidence,
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());

        var document = ModelFixtures.document(root);

        assertEquals(List.of("second", "first"), document.root().locations().items().stream()
                .map(location -> location.id().value())
                .toList());
    }

    @Test
    void completedGraphTypesExcludeCandidateAndPlatformObjects() {
        assertCompletedGraphTypes(DiagnosticDocument.class);
    }

    @Test
    void typeGraphCheckRejectsForbiddenTypesHiddenBehindContainers() {
        assertThrows(AssertionError.class, () -> assertCompletedGraphTypes(
                PlatformRetentionFixture.class));
        assertThrows(AssertionError.class, () -> assertCompletedGraphTypes(
                ArrayRetentionFixture.class));
        assertThrows(AssertionError.class, () -> assertCompletedGraphTypes(
                CallbackRetentionFixture.class));
        assertThrows(AssertionError.class, () -> assertCompletedGraphTypes(
                MutableRetentionFixture.class));
        assertThrows(AssertionError.class, () -> assertCompletedGraphTypes(
                CandidateRetentionFixture.class));
    }

    private static void assertCompletedGraphTypes(Type rootType) {
        var visited = new HashSet<Type>();
        var pending = new ArrayDeque<Type>();
        pending.add(rootType);

        while (!pending.isEmpty()) {
            var type = pending.removeFirst();
            if (!visited.add(type)) {
                continue;
            }
            if (type instanceof ParameterizedType parameterized) {
                pending.add(parameterized.getRawType());
                for (var argument : parameterized.getActualTypeArguments()) {
                    pending.add(argument);
                }
                continue;
            }
            if (!(type instanceof Class<?> modelType)) {
                continue;
            }
            assertAllowedJavaType(modelType);
            if (modelType.isEnum()
                    || modelType.isPrimitive()
                    || modelType == String.class
                    || modelType == Integer.class
                    || modelType == List.class
                    || modelType == Optional.class) {
                continue;
            }
            assertTrue(modelType.isRecord() || Modifier.isFinal(modelType.getModifiers()));
            if (modelType.isRecord()) {
                for (var component : modelType.getRecordComponents()) {
                    pending.add(component.getGenericType());
                }
            } else {
                for (var field : modelType.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        pending.add(field.getGenericType());
                    }
                }
            }
        }
    }

    private static void assertAllowedJavaType(Class<?> type) {
        assertFalse(type.isArray(), () -> "completed graph retains array type " + type);
        assertFalse(type == CandidateText.class, "completed graph retains pre-redaction CandidateText");
        assertFalse(type == Object.class, "completed graph retains untyped Object");
        assertFalse(Throwable.class.isAssignableFrom(type),
                () -> "completed graph retains throwable type " + type);
        assertFalse(ClassLoader.class.isAssignableFrom(type),
                () -> "completed graph retains class-loader type " + type);
        assertFalse(Path.class.isAssignableFrom(type),
                () -> "completed graph retains path type " + type);
        assertFalse(
                type == Runnable.class
                        || Callable.class.isAssignableFrom(type)
                        || Supplier.class.isAssignableFrom(type)
                        || type.getPackageName().equals("java.util.function"),
                () -> "completed graph retains callback type " + type);
        assertFalse(
                Collection.class.isAssignableFrom(type) && type != List.class,
                () -> "completed graph retains unsupported mutable collection type " + type);
        if (type.getPackageName().startsWith("java.")) {
            assertTrue(
                    type.isPrimitive()
                            || type == String.class
                            || type == Integer.class
                            || type == List.class
                            || type == Optional.class,
                    () -> "completed graph retains unsupported Java type " + type);
        }
    }

    private record PlatformRetentionFixture(Optional<Path> path, Object platform) {
    }

    private record ArrayRetentionFixture(String[] values) {
    }

    private record CallbackRetentionFixture(Supplier<String> callback) {
    }

    private record MutableRetentionFixture(Set<String> values) {
    }

    private record CandidateRetentionFixture(Optional<CandidateText> candidate) {
    }

    @Test
    void transformedGraphDoesNotRetainProtectedOriginal() {
        var protectedOriginal = "secret-token-value";
        var safe = DisplayText.redacted(
                TextOrigin.EXTERNAL,
                Sensitivity.SECRET,
                new RedactionMarker("TOKEN"));
        var evidence = new EvidenceReference(
                ModelFixtures.EVIDENCE_ID,
                EvidenceKind.OTHER,
                safe,
                java.util.Optional.empty());
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(List.of(evidence)),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());

        var completed = new DiagnosticDocument(
                SchemaVersion.CURRENT,
                new DiagnosticId("diag0001"),
                new CorrelationId("ABC123"),
                root,
                BoundedList.of(List.of(new RedactionNotice(
                        new RedactionMarker("TOKEN"),
                        TextDisposition.REDACTED,
                        1))),
                BoundedList.empty());

        assertFalse(completed.toString().contains(protectedOriginal));
        assertTrue(completed.toString().contains("<redacted:token>"));
    }

    @Test
    void everyRetainedTransformationRequiresACoveringNotice() {
        var safe = DisplayText.redacted(
                TextOrigin.EXTERNAL,
                Sensitivity.SECRET,
                new RedactionMarker("TOKEN"));
        var evidence = new EvidenceReference(
                ModelFixtures.EVIDENCE_ID,
                EvidenceKind.OTHER,
                safe,
                java.util.Optional.empty());
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(List.of(evidence)),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());

        assertThrows(RedactionValidationException.class, () -> ModelFixtures.document(root));
    }

    private static Location location(String id) {
        return new Location(
                new LocationId(id),
                LocationKind.OTHER,
                ModelFixtures.text(id),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                BoundedList.of(List.of(ModelFixtures.EVIDENCE_ID)));
    }
}
