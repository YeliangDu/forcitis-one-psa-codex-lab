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
import java.util.Map;
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

    /** Classes that may build one kind of id from the raw value of another; an entry that no longer builds anything fails, so it is removed. */
    private static final Map<String, String> MAY_BUILD_FROM_ANOTHER_ID = Map.of(
            "com.forcitis.one.cost.timesheet.domain.Timesheet", "the id of a timesheet is the id of the document it is, so its document reference wraps the value unchanged (R-1b P4)",
            "com.forcitis.one.cost.timesheet.application.ApplyApprovalResult", "流程标识为工时审批时，单据 ID 即工时单 ID，值不变（R-1b P4）",
            "com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition", "the id of a requisition is the id of the document it is, so its document reference wraps the value unchanged (#1451)",
            "com.forcitis.one.organization.recruitment.application.SubmitRequisition", "the approval start names the requisition as a document, whose id is the requisition id unchanged (#1451)",
            "com.forcitis.one.organization.recruitment.application.ApplyRequisitionApprovalResult", "for the requisition approval process the document id is the requisition id, value unchanged (#1451)");

    /**
     * Classes that convert one kind of id into another in a way the scan cannot see (through a method that takes the raw number), kept on
     * record with the reason; an entry whose class is gone fails.
     */
    private static final Map<String, String> CONVERTS_OUT_OF_SIGHT = Map.of(
            "com.forcitis.one.platform.approval.infrastructure.replay.ExportedAccounts",
            "AccountEmployees of the offline replay: an exported person number becomes an account id through account(long) and back through person(UserId)");

    @Test
    @DisplayName("P2-L7 every conversion the scan cannot see is on record and its class still exists")
    void conversionsOutOfSightAreOnRecord() {
        CONVERTS_OUT_OF_SIGHT.keySet().forEach(name -> assertThat(java.util.Arrays.stream(ALL).map(JavaClass::getName))
                .as(GuardMessage.of("ADR-0033", "CONVERTS_OUT_OF_SIGHT lists " + name + " but that class no longer exists",
                        "delete the entry from CONVERTS_OUT_OF_SIGHT in TypedIdComparisonGuardTest")).contains(name));
    }

    private static List<TypedIdComparisons.Violation> inspect(Class<?> type) throws IOException {
        try (InputStream bytes = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return TypedIdComparisons.inspect(bytes, TYPED_IDS::contains);
        }
    }

    @Test
    @DisplayName("production code never compares the raw value of a typed id")
    void productionCodeComparesTypedIds() throws IOException {
        assertThat(TYPED_IDS).as(GuardMessage.of("ADR-0033", "the guard knows 25 or fewer typed ids, so it is scanning the wrong classes",
                "check how TYPED_IDS is built in TypedIdComparisonGuardTest")).hasSizeGreaterThan(25).contains("com/forcitis/one/kernel/UserId",
                "com/forcitis/one/kernel/EmployeeId");
        List<String> problems = new ArrayList<>();
        Set<String> builders = new java.util.TreeSet<>();
        for (JavaClass javaClass : ALL) {
            try (InputStream bytes = javaClass.getSource().orElseThrow().getUri().toURL().openStream()) {
                TypedIdComparisons.inspect(bytes, TYPED_IDS::contains).forEach(violation -> {
                    String builder = MAY_BUILD_FROM_ANOTHER_ID.keySet().stream().filter(name -> violation.where().startsWith(name + ".")).findFirst().orElse(null);
                    if (builder != null && violation.what().startsWith("builds")) {
                        builders.add(builder);
                    } else {
                        problems.add(violation.toString());
                    }
                });
            }
        }

        assertThat(problems).as(GuardMessage.of("ADR-0033",
                "code compares or looks up the raw value of a typed id, compares ids of different kinds, or builds one kind of id from the raw value of another",
                "compare the typed ids themselves (equals on the id records) and convert between id kinds only through a mapped lookup; a reviewed exception goes in MAY_BUILD_FROM_ANOTHER_ID with a reason"))
                .isEmpty();
        assertThat(builders).as(GuardMessage.of("ADR-0033",
                "an entry of MAY_BUILD_FROM_ANOTHER_ID no longer builds an id from another kind of id",
                "remove that entry from MAY_BUILD_FROM_ANOTHER_ID in TypedIdComparisonGuardTest"))
                .isEqualTo(MAY_BUILD_FROM_ANOTHER_ID.keySet());
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
    @DisplayName("injected: an id compared with, or looked up in a collection of, another kind of id is caught; the same kind, other types and what the scan cannot see pass")
    void theGuardSeesCrossTypeComparisons() throws IOException {
        List<TypedIdComparisons.Violation> found = inspect(CrossTypeIdComparisons.class);

        List<String> caught = found.stream().filter(violation -> !violation.what().startsWith("builds")).map(TypedIdComparisons.Violation::where)
                .map(where -> where.substring(where.lastIndexOf('.') + 1)).distinct().toList();
        assertThat(caught).containsExactlyInAnyOrder("wrongEqualsOnTheIds", "wrongObjectsEquals", "wrongContainsOnAParameter",
                "wrongContainsOnAField", "wrongContainsOnALocal", "wrongListLookup", "wrongMapGet", "wrongContainsKey", "wrongGetOrDefault",
                "wrongRemoveFromAKeySet", "wrongThroughAnAccessor", "wrongWithAWildcard", "wrongReferenceEquals", "wrongReferenceContains",
                "wrongReferenceContainsKey");
        assertThat(found.stream().filter(violation -> violation.where().endsWith("wrongThroughAnAccessor")).count())
                .as("both lookups of the accessor").isEqualTo(2);
        assertThat(found.stream().filter(violation -> violation.where().endsWith("wrongListLookup")).count()).as("indexOf and remove").isEqualTo(2);
    }

    @Test
    @DisplayName("injected: an id built from the raw value of another kind of id is caught, one built from anything else is not")
    void theGuardSeesCrossTypeConstruction() throws IOException {
        List<TypedIdComparisons.Violation> found = inspect(CrossTypeIdComparisons.class);

        List<String> built = found.stream().filter(violation -> violation.what().startsWith("builds")).map(TypedIdComparisons.Violation::where)
                .map(where -> where.substring(where.lastIndexOf('.') + 1)).distinct().toList();
        assertThat(built).containsExactlyInAnyOrder("wrongBuiltFromAnotherId", "lambda$wrongBuiltFromAnotherIdInAnExpression$0");
    }

    @Test
    @DisplayName("ADR-0033 guard 14 the build keeps Error Prone's two incompatible-type checks at error")
    void errorProneChecksStayOn() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom).as(GuardMessage.of("ADR-0033",
                "backend/pom.xml no longer keeps Error Prone's CollectionIncompatibleType and EqualsIncompatibleType checks at ERROR",
                "restore -Xplugin:ErrorProne, -Xep:CollectionIncompatibleType:ERROR and -Xep:EqualsIncompatibleType:ERROR in the compiler arguments of backend/pom.xml"))
                .contains("-Xplugin:ErrorProne").contains("-Xep:CollectionIncompatibleType:ERROR").contains("-Xep:EqualsIncompatibleType:ERROR");
    }
}
