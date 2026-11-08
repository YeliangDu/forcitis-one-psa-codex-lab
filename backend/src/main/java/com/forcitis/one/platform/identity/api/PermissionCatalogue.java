package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.PermissionContribution;
import com.forcitis.one.kernel.PermissionDeclaration;
import com.forcitis.one.kernel.PermissionGrantMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/** Validated migration catalogue; the legacy facade is an alias, not a second owner. */
public record PermissionCatalogue(List<PermissionDeclaration> declarations) {
    public PermissionCatalogue {
        declarations = List.copyOf(declarations);
    }

    private enum Provider {
        INSTANCE;
        private final PermissionCatalogueProvider source = load();

        private static PermissionCatalogueProvider load() {
            List<PermissionCatalogueProvider> sources = ServiceLoader.load(PermissionCatalogueProvider.class).stream().map(ServiceLoader.Provider::get).toList();
            if (sources.size() != 1) {
                throw new IllegalArgumentException("Exactly one permission catalogue source is required");
            }
            return sources.getFirst();
        }
    }

    public static PermissionCatalogueProvider source() { return Provider.INSTANCE.source; }

    /** Shared production-only discovery for plain G43 and startup verification. */
    public static PermissionCatalogue fromClasspath() {
        return source().catalogue();
    }

    public Map<String, PermissionDeclaration> entries() {
        return declarations.stream().collect(Collectors.toUnmodifiableMap(e -> e.code().value(), e -> e));
    }

    public boolean isLive(String code) {
        return declarations.stream().anyMatch(entry -> entry.code().value().equals(code) && !entry.deprecated());
    }

    public static PermissionCatalogue assemble(List<PermissionDeclaration> legacy, List<PermissionContribution> contributions,
            Set<String> sensitive, Set<String> annotationReferences, Set<String> roleGrants, Set<String> administratorReferences) {
        PermissionCatalogueProvider source = source();
        Map<String, PermissionDeclaration> mixed = new LinkedHashMap<>();
        for (PermissionDeclaration entry : legacy) {
            source.validate(entry.code().value());
            if (mixed.putIfAbsent(entry.code().value(), entry) != null) {
                throw new IllegalArgumentException("Duplicate legacy permission: " + entry.code().value());
            }
        }
        Set<String> declared = new HashSet<>();
        for (PermissionContribution contribution : contributions) {
            String owner = contribution.owner();
            if (owner == null || !owner.matches("[a-z]+\\.[a-z]+")) {
                throw new IllegalArgumentException("Invalid permission owner");
            }
            for (PermissionDeclaration entry : contribution.permissions()) {
                String code = entry.code().value();
                source.validate(code);
                if (!code.startsWith(owner + ":") || !declared.add(code)) {
                    throw new IllegalArgumentException("Duplicate or foreign permission declaration: " + code);
                }
                PermissionDeclaration old = mixed.putIfAbsent(code, entry);
                if (old != null && !old.equals(entry)) {
                    throw new IllegalArgumentException("Legacy permission metadata differs: " + code);
                }
            }
        }
        PermissionCatalogue catalogue = new PermissionCatalogue(List.copyOf(mixed.values()));
        Set<String> twoPerson = mixed.values().stream()
                .filter(e -> !e.deprecated() && e.grantMode() == PermissionGrantMode.TWO_PERSON_INDIVIDUAL)
                .map(e -> e.code().value()).collect(Collectors.toSet());
        if (!twoPerson.equals(sensitive)) {
            throw new IllegalArgumentException("Sensitive permissions differ from declaration grant modes");
        }
        for (Set<String> references : List.of(sensitive, annotationReferences, roleGrants, administratorReferences)) {
            for (String code : references) {
                if (!catalogue.isLive(code)) {
                    throw new IllegalArgumentException("Uncatalogued or retired permission reference: " + code);
                }
            }
        }
        return catalogue;
    }
}
