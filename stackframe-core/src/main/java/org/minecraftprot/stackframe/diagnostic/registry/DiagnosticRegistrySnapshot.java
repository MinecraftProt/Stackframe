package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;

/** Deeply immutable, deterministic view of all allocated and available codes. */
public final class DiagnosticRegistrySnapshot {
    private static final DiagnosticCode GENERIC_FALLBACK = new DiagnosticCode("SF0001");

    private final List<RegistryEntry> entries;
    private final Map<DiagnosticCode, RegistryEntry> byCode;
    private final Map<String, RegistryEntry> bySymbolicKey;
    private final Map<String, RegistryEntry> byDocumentationAnchor;
    private final Map<DiagnosticLifecycle, List<RegistryEntry>> byLifecycle;
    private final List<ClassifierRegistration> classifiers;
    private final Map<String, ClassifierRegistration> byClassifierKey;

    private DiagnosticRegistrySnapshot(
            Collection<RegistryEntry> source,
            Collection<ClassifierRegistration> classifierSource) {
        RegistryValidation.noNullElements(source, "entries");
        RegistryValidation.noNullElements(classifierSource, "classifiers");
        var sorted = source.stream()
                .sorted(Comparator.comparing(entry -> entry.code().value()))
                .toList();
        var codes = new TreeMap<DiagnosticCode, RegistryEntry>(
                Comparator.comparing(DiagnosticCode::value));
        var keys = new TreeMap<String, RegistryEntry>();
        var anchors = new TreeMap<String, RegistryEntry>();
        var lifecycleEntries = new EnumMap<DiagnosticLifecycle, List<RegistryEntry>>(
                DiagnosticLifecycle.class);
        for (var lifecycle : DiagnosticLifecycle.values()) {
            lifecycleEntries.put(lifecycle, new ArrayList<>());
        }

        for (var entry : sorted) {
            putUnique(codes, entry.code(), entry, "code " + entry.code().value());
            putUnique(keys, entry.symbolicKey(), entry, "symbolic key " + entry.symbolicKey());
            putUnique(
                    anchors,
                    entry.documentationAnchor(),
                    entry,
                    "documentation anchor " + entry.documentationAnchor());
            lifecycleEntries.get(entry.lifecycle()).add(entry);
        }

        var fallback = codes.get(GENERIC_FALLBACK);
        if (fallback == null
                || fallback.lifecycle() != DiagnosticLifecycle.ACTIVE
                || fallback.area() != DiagnosticArea.GENERIC_INTERNAL
                || !fallback.fallback().fallbackCode().equals(GENERIC_FALLBACK)) {
            throw new RegistryValidationException(
                    "registry requires active generic fallback SF0001 in GENERIC_INTERNAL");
        }
        for (var entry : sorted) {
            entry.replacementCode().ifPresent(replacement -> {
                var replacementEntry = codes.get(replacement);
                if (replacementEntry == null
                        || replacementEntry.lifecycle() != DiagnosticLifecycle.ACTIVE) {
                    throw new RegistryValidationException(
                            entry.code().value()
                                    + " replacement must identify an active registered code");
                }
            });
        }

        entries = List.copyOf(sorted);
        byCode = Collections.unmodifiableMap(codes);
        bySymbolicKey = Collections.unmodifiableMap(keys);
        byDocumentationAnchor = Collections.unmodifiableMap(anchors);
        var immutableLifecycle = new EnumMap<DiagnosticLifecycle, List<RegistryEntry>>(
                DiagnosticLifecycle.class);
        lifecycleEntries.forEach((key, value) -> immutableLifecycle.put(key, List.copyOf(value)));
        byLifecycle = Collections.unmodifiableMap(immutableLifecycle);

        classifiers = classifierSource.stream()
                .sorted(Comparator.comparing(ClassifierRegistration::classifierKey))
                .toList();
        var classifierKeys = new TreeMap<String, ClassifierRegistration>();
        for (var classifier : classifiers) {
            var target = codes.get(classifier.diagnosticCode());
            if (target == null
                    || target.lifecycle() != DiagnosticLifecycle.ACTIVE
                    || classifier.diagnosticCode().equals(GENERIC_FALLBACK)) {
                throw new RegistryValidationException(
                        "classifier " + classifier.classifierKey()
                                + " must target an active specialized diagnostic");
            }
            if (classifierKeys.putIfAbsent(classifier.classifierKey(), classifier) != null) {
                throw new RegistryValidationException(
                        "duplicate classifier key " + classifier.classifierKey());
            }
        }
        byClassifierKey = Collections.unmodifiableMap(classifierKeys);
    }

    public static DiagnosticRegistrySnapshot of(Collection<RegistryEntry> entries) {
        return of(entries, List.of());
    }

    public static DiagnosticRegistrySnapshot of(
            Collection<RegistryEntry> entries,
            Collection<ClassifierRegistration> classifiers) {
        return new DiagnosticRegistrySnapshot(entries, classifiers);
    }

    public List<RegistryEntry> entries() {
        return entries;
    }

    public RegistryEntry genericFallback() {
        return byCode.get(GENERIC_FALLBACK);
    }

    public Optional<RegistryEntry> find(DiagnosticCode code) {
        DiagnosticArea.forCode(code);
        return Optional.ofNullable(byCode.get(code));
    }

    public Optional<RegistryEntry> find(String code) {
        return find(new DiagnosticCode(code));
    }

    public Optional<RegistryEntry> findBySymbolicKey(String symbolicKey) {
        RegistryValidation.required(symbolicKey, "symbolicKey");
        return Optional.ofNullable(bySymbolicKey.get(symbolicKey));
    }

    public Optional<RegistryEntry> findByDocumentationAnchor(String anchor) {
        RegistryValidation.required(anchor, "documentationAnchor");
        return Optional.ofNullable(byDocumentationAnchor.get(anchor));
    }

    public List<RegistryEntry> entries(DiagnosticLifecycle lifecycle) {
        return byLifecycle.get(RegistryValidation.required(lifecycle, "lifecycle"));
    }

    public List<ClassifierRegistration> classifiers() {
        return classifiers;
    }

    public Optional<ClassifierRegistration> findClassifier(String classifierKey) {
        RegistryValidation.required(classifierKey, "classifierKey");
        return Optional.ofNullable(byClassifierKey.get(classifierKey));
    }

    public List<DiagnosticCode> availableCodes(DiagnosticArea area) {
        RegistryValidation.required(area, "area");
        var available = new ArrayList<DiagnosticCode>();
        for (var suffix = area.minimumSuffix(); suffix <= area.maximumSuffix(); suffix++) {
            var code = area.code(suffix);
            if (!byCode.containsKey(code)) {
                available.add(code);
            }
        }
        return List.copyOf(available);
    }

    private static <K> void putUnique(
            Map<K, RegistryEntry> target,
            K key,
            RegistryEntry value,
            String description) {
        if (target.putIfAbsent(key, value) != null) {
            throw new RegistryValidationException("duplicate " + description);
        }
    }
}
