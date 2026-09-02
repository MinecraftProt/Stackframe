package org.minecraftprot.stackframe.diagnostic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierAndSchemaTest {
    @ParameterizedTest
    @ValueSource(strings = {"SF0001", "SF9999"})
    void acceptsDiagnosticCodeGrammar(String value) {
        assertEquals(value, new DiagnosticCode(value).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sf0001", "SF001", "SF00001", "SG0001", "SF00A1", "SF０００１"})
    void rejectsInvalidDiagnosticCodes(String value) {
        assertThrows(IdentifierValidationException.class, () -> new DiagnosticCode(value));
    }

    @Test
    void acceptsExactDiagnosticIdBounds() {
        assertDoesNotThrow(() -> new DiagnosticId("a".repeat(8)));
        assertDoesNotThrow(() -> new DiagnosticId("a".repeat(64)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaaaaaa", "with space", "-leading", "é1234567"})
    void rejectsInvalidDiagnosticIds(String value) {
        assertThrows(IdentifierValidationException.class, () -> new DiagnosticId(value));
    }

    @Test
    void rejectsDiagnosticIdAboveMaximum() {
        assertThrows(IdentifierValidationException.class, () -> new DiagnosticId("a".repeat(65)));
    }

    @Test
    void acceptsExactCorrelationBounds() {
        assertDoesNotThrow(() -> new CorrelationId("A1B2C3"));
        assertDoesNotThrow(() -> new CorrelationId("A".repeat(26)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC12", "A23456789012345678901234567", "ABCI23", "ABCL23", "ABCO23",
            "ABCU23", "abc123", "ABC-23"})
    void rejectsInvalidCorrelationIds(String value) {
        assertThrows(IdentifierValidationException.class, () -> new CorrelationId(value));
    }

    @ParameterizedTest
    @MethodSource("localIdentifiers")
    void enforcesLocalIdentifierBounds(String value, boolean valid) {
        if (valid) {
            assertDoesNotThrow(() -> new LocationId(value));
            assertDoesNotThrow(() -> new EvidenceId(value));
        } else {
            assertThrows(IdentifierValidationException.class, () -> new LocationId(value));
            assertThrows(IdentifierValidationException.class, () -> new EvidenceId(value));
        }
    }

    @Test
    void enforcesOpaqueIdentifierBoundsAndAscii() {
        assertDoesNotThrow(() -> new OpaqueIdentifier("x"));
        assertDoesNotThrow(() -> new OpaqueIdentifier("x".repeat(96)));
        assertThrows(IdentifierValidationException.class, () -> new OpaqueIdentifier(""));
        assertThrows(IdentifierValidationException.class, () -> new OpaqueIdentifier("x".repeat(97)));
        assertThrows(IdentifierValidationException.class, () -> new OpaqueIdentifier("opaque key"));
        assertThrows(IdentifierValidationException.class, () -> new OpaqueIdentifier("é"));
    }

    @Test
    void enforcesCatalogKeyAndMarkerGrammars() {
        assertDoesNotThrow(() -> new CatalogText("a", "value"));
        assertDoesNotThrow(() -> new CatalogText("a".repeat(96), "value"));
        assertThrows(IdentifierValidationException.class,
                () -> new CatalogText("A.key", "value"));
        assertThrows(IdentifierValidationException.class,
                () -> new CatalogText("a".repeat(97), "value"));
        assertDoesNotThrow(() -> new RedactionMarker("WORLD_DATA"));
        assertThrows(IdentifierValidationException.class, () -> new RedactionMarker("world-data"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"root.locations", "$", "$.root.unknown", "$.root.children[0]",
            "$.root.children.items[01].diagnostic.locations"})
    void rejectsMalformedOrUnknownModelPaths(String value) {
        assertThrows(OmissionValidationException.class, () -> new ModelPath(value));
    }

    @Test
    void acceptsResolvedPathSyntax() {
        assertEquals(
                "$.root.children.items[0].diagnostic.locations",
                new ModelPath("$.root.children.items[0].diagnostic.locations").value());
    }

    @Test
    void schemaVersionHasIndependentAsciiRepresentation() {
        assertEquals("1.0", SchemaVersion.CURRENT.value());
        assertEquals("1.0", SchemaVersion.CURRENT.toString());
        assertThrows(SchemaValidationException.class, () -> new SchemaVersion(-1, 0));
    }

    @Test
    void completedDocumentsRejectUnsupportedSchema() {
        var root = ModelFixtures.minimalDiagnostic();
        assertThrows(SchemaValidationException.class, () -> new DiagnosticDocument(
                new SchemaVersion(1, 1),
                new DiagnosticId("diag0001"),
                new CorrelationId("ABC123"),
                root,
                BoundedList.empty(),
                BoundedList.empty()));
        assertThrows(SchemaValidationException.class, () -> new DiagnosticDocument(
                new SchemaVersion(2, 0),
                new DiagnosticId("diag0001"),
                new CorrelationId("ABC123"),
                root,
                BoundedList.empty(),
                BoundedList.empty()));
    }

    private static Stream<Arguments> localIdentifiers() {
        return Stream.of(
                Arguments.of("a", true),
                Arguments.of("a".repeat(32), true),
                Arguments.of("", false),
                Arguments.of("a".repeat(33), false),
                Arguments.of("A", false),
                Arguments.of("1a", false),
                Arguments.of("a_b", false));
    }
}
