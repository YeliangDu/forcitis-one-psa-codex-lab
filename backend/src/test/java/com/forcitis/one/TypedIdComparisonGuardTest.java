package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.typedid.CrossTypeIdComparisons;
import com.forcitis.one.fixture.typedid.RawValueComparisons;
import com.forcitis.one.kernel.IdRecords;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0033 section 3, guard 14: employee ids and user ids are told apart by type, so a comparison must be made on the typed ids and
 * never on a raw value taken out of one. The compiler side (Error Prone) cannot see a mismatch between two records, so this and the
 * reverse tests of the "own person" gates carry that part.
 */
class TypedIdComparisonGuardTest {
    private static final JavaClass[] ALL = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.forcitis.one").stream().toArray(JavaClass[]::new);

    private static final Set<String> TYPED_IDS = java.util.Arrays.stream(ALL).map(JavaClass::reflect).filter(IdRecords::isIdRecord)
            .map(type -> type.getName().replace('.', '/')).collect(Collectors.toSet());

    private static List<TypedIdComparisons.Violation> inspect(Class<?> type) throws IOException {
        try (InputStream bytes = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return TypedIdComparisons.inspect(bytes, TYPED_IDS::contains);
        }
    }

    @Test
    @DisplayName("production code never compares the raw value of a typed id")
    void productionCodeComparesTypedIds() throws IOException {
        assertThat(TYPED_IDS).as("the guard knows the typed ids").hasSizeGreaterThan(25).contains("com/forcitis/one/kernel/UserId",
                "com/forcitis/one/kernel/EmployeeId");
        List<String> problems = new ArrayList<>();
        for (JavaClass javaClass : ALL) {
            try (InputStream bytes = javaClass.getSource().orElseThrow().getUri().toURL().openStream()) {
                TypedIdComparisons.inspect(bytes, TYPED_IDS::contains).forEach(violation -> problems.add(violation.toString()));
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("injected: raw-value comparisons of typed ids are caught, comparisons on the ids, with a constant, by order or of other values pass")
    void theGuardSeesRawValueComparisons() throws IOException {
        List<String> caught = inspect(RawValueComparisons.class).stream().map(TypedIdComparisons.Violation::where)
                .map(where -> where.substring(where.lastIndexOf('.') + 1)).distinct().toList();

        assertThat(caught).containsExactlyInAnyOrder("twoRawLongs", "twoRawLongsAsNotEqual", "boxedThroughObjects", "uuidValuesByEquals",
                "plainLongAgainstTheId", "theIdAgainstAPlainLong");
    }

    @Test
    @DisplayName("RULING-P1-1 injected: an id compared with, or looked up in a collection of, another kind of id is caught; the same kind, other types and what the scan cannot see pass")
    void theGuardSeesCrossTypeComparisons() throws IOException {
        List<TypedIdComparisons.Violation> found = inspect(CrossTypeIdComparisons.class);

        List<String> caught = found.stream().map(TypedIdComparisons.Violation::where).map(where -> where.substring(where.lastIndexOf('.') + 1))
                .distinct().toList();
        assertThat(caught).containsExactlyInAnyOrder("wrongEqualsOnTheIds", "wrongObjectsEquals", "wrongContainsOnAParameter",
                "wrongContainsOnAField", "wrongContainsOnALocal", "wrongListLookup", "wrongMapGet", "wrongContainsKey", "wrongGetOrDefault",
                "wrongRemoveFromAKeySet", "wrongThroughAnAccessor", "wrongWithAWildcard");
        assertThat(found.stream().filter(violation -> violation.where().endsWith("wrongThroughAnAccessor")).count())
                .as("both lookups of the accessor").isEqualTo(2);
        assertThat(found.stream().filter(violation -> violation.where().endsWith("wrongListLookup")).count()).as("indexOf and remove").isEqualTo(2);
    }

    @Test
    @DisplayName("ADR-0033 guard 14 the build keeps Error Prone's two incompatible-type checks at error")
    void errorProneChecksStayOn() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom).contains("-Xplugin:ErrorProne").contains("-Xep:CollectionIncompatibleType:ERROR").contains("-Xep:EqualsIncompatibleType:ERROR");
    }
}
