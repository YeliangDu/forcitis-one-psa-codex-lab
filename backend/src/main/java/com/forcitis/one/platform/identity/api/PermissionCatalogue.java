package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.PermissionContribution;
import com.forcitis.one.kernel.PermissionDeclaration;
import com.forcitis.one.kernel.PermissionGrantMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;
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
            // G6 keeps infrastructure providers package-private. Read the same
            // descriptor explicitly rather than requiring ServiceLoader's public constructor.
            Set<String> names = new HashSet<>();
            ClassLoader loader = PermissionCatalogueProvider.class.getClassLoader();
            String ownerPackage = PermissionCatalogueProvider.class.getPackageName().replaceFirst("\\.api$", ".infrastructure.permissions");
            try {
                var resources = loader.getResources("META-INF/services/" + PermissionCatalogueProvider.class.getName());
                while (resources.hasMoreElements()) {
                    try (var reader = new BufferedReader(new InputStreamReader(resources.nextElement().openStream(), StandardCharsets.UTF_8))) {
                        for (String line : reader.lines().toList()) {
                            int comment = line.indexOf('#');
                            String name = (comment < 0 ? line : line.substring(0, comment)).strip();
                            if (name.isEmpty()) { continue; }
                            if (!name.matches(java.util.regex.Pattern.quote(ownerPackage) + "\\.[A-Z][A-Za-z0-9]*")) {
                                throw new IllegalArgumentException("Permission catalogue provider is outside its owning package");
                            }
                            if (!names.add(name)) { throw new IllegalArgumentException("Duplicate permission catalogue provider"); }
                        }
                    }
                }
                // Finish validating every descriptor before any provider class is loaded.
                if (names.size() != 1) {
                    throw new IllegalArgumentException("Exactly one permission catalogue source is required");
                }
                var type = Class.forName(names.iterator().next(), false, loader).asSubclass(PermissionCatalogueProvider.class);
                if (type.isInterface() || Modifier.isAbstract(type.getModifiers())
                        || !type.getPackageName().equals(ownerPackage) || type.getClassLoader() != loader) {
                    throw new IllegalArgumentException("Invalid permission catalogue provider type or owner");
                }
                var constructor = type.getDeclaredConstructor();
                if (!constructor.trySetAccessible()) {
                    throw new IllegalArgumentException("Permission catalogue provider is inaccessible");
                }
                // This is the first operation permitted to initialize/execute the validated provider.
                return constructor.newInstance();
            } catch (IOException | ReflectiveOperationException | SecurityException error) {
                throw new IllegalArgumentException("Cannot construct permission catalogue provider", error);
            }
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

    /** Explicit expectation seam retained for independent compatibility/negative tests. */
    public static PermissionCatalogue assemble(List<PermissionDeclaration> legacy, List<PermissionContribution> contributions,
            Set<String> sensitive, Set<String> annotationReferences, Set<String> roleGrants, Set<String> administratorReferences) {
        PermissionCatalogue catalogue = assemble(legacy, contributions, annotationReferences, roleGrants, administratorReferences);
        if (!catalogue.twoPersonPermissions().equals(sensitive)) {
            throw new IllegalArgumentException("Sensitive permissions differ from declaration grant modes");
        }
        return catalogue;
    }

    /** Production assembly derives sensitivity only after validating every declaration. */
    public static PermissionCatalogue assemble(List<PermissionDeclaration> legacy, List<PermissionContribution> contributions,
            Set<String> annotationReferences, Set<String> roleGrants, Set<String> administratorReferences) {
        PermissionCatalogueProvider source = source();
        Map<String, PermissionDeclaration> mixed = new LinkedHashMap<>();
        for (PermissionDeclaration entry : legacy) {
            requireLegacy(entry);
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
                source.validateExisting(entry);
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
        for (Set<String> references : List.of(annotationReferences, roleGrants, administratorReferences)) {
            for (String code : references) {
                if (!catalogue.isLive(code)) {
                    throw new IllegalArgumentException("Uncatalogued or retired permission reference: " + code);
                }
            }
        }
        return catalogue;
    }

    public Set<String> twoPersonPermissions() {
        return declarations.stream()
                .filter(entry -> !entry.deprecated() && entry.grantMode() == PermissionGrantMode.TWO_PERSON_INDIVIDUAL)
                .map(entry -> entry.code().value()).collect(Collectors.toUnmodifiableSet());
    }

    private static void requireLegacy(PermissionDeclaration entry) {
        for (Permission permission : Permission.values()) {
            if (permission.code().equals(entry.code().value()) && permission.grantMode() == entry.grantMode()
                    && permission.deprecated() == entry.deprecated()) {
                return;
            }
        }
        throw new IllegalArgumentException("Legacy input is not an exact retained definition: " + entry.code().value());
    }
}
