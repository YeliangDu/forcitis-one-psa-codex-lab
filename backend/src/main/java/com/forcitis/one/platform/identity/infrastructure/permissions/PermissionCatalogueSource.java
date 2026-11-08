package com.forcitis.one.platform.identity.infrastructure.permissions;

import com.forcitis.one.kernel.PermissionCode;
import com.forcitis.one.kernel.PermissionContribution;
import com.forcitis.one.kernel.PermissionDeclaration;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.PermissionCatalogue;
import com.forcitis.one.platform.identity.api.PermissionCatalogueProvider;
import com.forcitis.one.platform.identity.domain.AdministratorMode;
import com.forcitis.one.platform.identity.domain.SystemRoleDeclaration;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;

/** Service-loaded bridge keeps the API free of internal dependencies and Spring startup. */
final class PermissionCatalogueSource implements PermissionCatalogueProvider {
    private static final Pattern AUTHORITY = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");

    private static final class Discovery {
        private static final List<Class<?>> TYPES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one").stream().<Class<?>>map(c -> c.reflect()).toList();
        private static final Set<String> REFERENCES = references();

        private static Set<String> references() {
            Set<String> result = new HashSet<>();
            for (Class<?> type : TYPES) {
                readAuthority(type.getAnnotation(PreAuthorize.class), result);
                for (var method : type.getDeclaredMethods()) {
                    readAuthority(method.getAnnotation(PreAuthorize.class), result);
                }
            }
            return Set.copyOf(result);
        }
    }

    public List<PermissionContribution> contributions() {
        List<PermissionContribution> result = new ArrayList<>();
        for (Class<?> type : Discovery.TYPES) {
            if (PermissionContribution.class.isAssignableFrom(type) && !type.isInterface()
                    && !Modifier.isAbstract(type.getModifiers())) {
                try {
                    var constructor = type.getDeclaredConstructor();
                    if (!constructor.trySetAccessible()) {
                        throw new IllegalArgumentException("Permission contribution is inaccessible: " + type.getName());
                    }
                    result.add(PermissionContribution.class.cast(constructor.newInstance()));
                } catch (ReflectiveOperationException error) {
                    throw new IllegalArgumentException("Cannot read permission contribution: " + type.getName(), error);
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void validate(String code) {
        PermissionVocabulary.validate(new PermissionCode(code));
    }

    @Override
    public void validateExisting(PermissionDeclaration declaration) {
        // Existing metadata is immutable. A genuinely new code instead requires
        // an exact resource registered for its owner; use sites never authorize it.
        for (Permission permission : Permission.values()) {
            if (permission.code().equals(declaration.code().value())) {
                if (permission.grantMode() == declaration.grantMode()
                        && permission.deprecated() == declaration.deprecated()) {
                    return;
                }
                throw new IllegalArgumentException("Contribution changes legacy permission metadata: " + permission.code());
            }
        }
        PermissionVocabulary.validateResource(declaration.code());
    }

    @Override
    public PermissionCatalogue catalogue() {
        return catalogue(contributions(), SystemRoleDeclaration.CURRENT);
    }

    public PermissionCatalogue catalogue(List<PermissionContribution> contributions, SystemRoleDeclaration roles) {
        List<PermissionDeclaration> legacy = java.util.Arrays.stream(Permission.values())
                .map(p -> new PermissionDeclaration(new PermissionCode(p.code()), p.grantMode(), p.deprecated())).toList();
        Set<String> references = new HashSet<>(Discovery.REFERENCES);
        // Compatibility constants are references, never another declaration owner.
        for (var field : Permission.Codes.class.getFields()) {
            try {
                references.add((String) field.get(null));
            } catch (IllegalAccessException error) {
                throw new IllegalArgumentException("Cannot read legacy permission constant", error);
            }
        }
        Set<String> grants = new HashSet<>();
        roles.roles().forEach(role -> grants.addAll(role.definition().permissionCodes()));
        Set<String> administrators = new HashSet<>(AdministratorMode.PERMISSIONS);
        administrators.addAll(AdministratorMode.PLATFORM_ONLY);
        return PermissionCatalogue.assemble(legacy, contributions, references, grants, administrators);
    }

    public void requireSameContributions(List<PermissionContribution> runtime) {
        if (!snapshot(runtime).equals(snapshot(contributions()))) {
            throw new IllegalArgumentException("Spring permission contributions differ from plain production discovery");
        }
    }

    private static Map<String, List<PermissionDeclaration>> snapshot(List<PermissionContribution> contributions) {
        Map<String, List<PermissionDeclaration>> result = new TreeMap<>();
        for (PermissionContribution contribution : contributions) {
            if (result.putIfAbsent(contribution.owner(), List.copyOf(contribution.permissions())) != null) {
                throw new IllegalArgumentException("Multiple permission contributions for one owner");
            }
        }
        return result;
    }

    private static void readAuthority(@org.jspecify.annotations.Nullable PreAuthorize annotation, Set<String> references) {
        if (annotation == null) { return; }
        var matcher = AUTHORITY.matcher(annotation.value());
        while (matcher.find()) {
            for (String raw : matcher.group(1).split(",")) {
                references.add(raw.trim().replaceAll("^'|'$", ""));
            }
        }
    }
}
