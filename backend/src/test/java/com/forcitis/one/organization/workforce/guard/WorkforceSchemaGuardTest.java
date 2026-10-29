package com.forcitis.one.organization.workforce.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.GuardMessage;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** organization_workforce holds organisation and identity master data only (§9a): no money columns, no Money or decimal in api / events. */
class WorkforceSchemaGuardTest {
    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/organization_workforce");
    static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE\\s+([a-z0-9_.]+)\\s*\\((.*?)\\n\\);", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    static final Pattern MONEY_NAME = Pattern.compile("(?i)(^|_)(amount|price|cost|salary|wage|balance|revenue|total|tax|currency|money|rate)(_|$)");
    static final Pattern MONEY_TYPE = Pattern.compile("(?i)^(numeric|decimal|money|smallmoney|real|double|float)\\b.*");

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.forcitis.one");

    @Test
    @DisplayName("§9a organization_workforce has no amount, price, cost, salary, balance, currency or other money column, and no decimal type")
    void noMoneyColumns() throws IOException {
        List<String> problems = new ArrayList<>();
        int tables = 0;
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                Matcher t = CREATE_TABLE.matcher(Files.readString(file));
                while (t.find()) {
                    tables++;
                    for (String raw : t.group(2).split(",\\s*\\n")) {
                        String[] parts = raw.trim().split("\\s+", 2);
                        String column = parts[0].toLowerCase();
                        if (column.isEmpty() || List.of("primary", "constraint", "unique", "foreign", "check").contains(column)) {
                            continue;
                        }
                        if (MONEY_NAME.matcher(column).find()) {
                            problems.add(t.group(1) + "." + column + ": looks like a money column");
                        }
                        if (parts.length > 1 && MONEY_TYPE.matcher(parts[1].trim()).matches()) {
                            problems.add(t.group(1) + "." + column + ": decimal / money type " + parts[1].split("\\s+")[0]);
                        }
                    }
                }
            }
        }
        assertThat(tables).as(GuardMessage.of("§9a", "the guard saw fewer than 6 workforce tables", "check that the CREATE TABLE statements under db/migration/organization_workforce still match the guard pattern")).isGreaterThanOrEqualTo(6);
        assertThat(problems).as(GuardMessage.of("§9a", "organization_workforce has a money-like column or a decimal type", "remove the listed column; organisation master data holds no money, and money belongs in a cost or accounting module")).isEmpty();
    }

    @Test
    @DisplayName("RULE-362/370 synced people have no local credential: no password, secret or token column in the workforce schema")
    void noLocalCredentials() throws IOException {
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                Matcher t = CREATE_TABLE.matcher(Files.readString(file));
                while (t.find()) {
                    for (String raw : t.group(2).split(",\\s*\\n")) {
                        String column = raw.trim().split("\\s+")[0].toLowerCase();
                        assertThat(column).as(GuardMessage.of("RULE-362/370", "the table " + t.group(1) + " has a password, secret, token, credential, hash, salt or birth column", "drop the column; synced people carry no local credential")).doesNotContainPattern("password|secret|token|credential|hash|salt|birth");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("§9a the money pattern does catch money columns (the guard is not vacuous)")
    void patternCatchesMoney() {
        assertThat(MONEY_NAME.matcher("fee_amount").find()).isTrue();
        assertThat(MONEY_NAME.matcher("contract_amount").find()).isTrue();
        assertThat(MONEY_NAME.matcher("daily_rate").find()).isTrue();
        assertThat(MONEY_NAME.matcher("monthly_salary").find()).isTrue();
        assertThat(MONEY_NAME.matcher("is_fee_earner").find()).as("the fee-earner marker is an HR classification").isFalse();
        assertThat(MONEY_TYPE.matcher("numeric(12, 2) NOT NULL").matches()).isTrue();
        assertThat(MONEY_TYPE.matcher("bigint NOT NULL").matches()).isFalse();
    }

    @Test
    @DisplayName("§9a the workforce api and events expose no Money, AccountingPeriod, BigDecimal or Currency")
    void apiExposesNoMoney() {
        noClasses().that().resideInAnyPackage("com.forcitis.one.organization.workforce.api..", "com.forcitis.one.organization.workforce.events..")
                .should().dependOnClassesThat().haveFullyQualifiedName("com.forcitis.one.kernel.Money")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("com.forcitis.one.kernel.AccountingPeriod")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.math.BigDecimal")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Currency")
                .because(GuardMessage.of("§9a", "the workforce api or events depend on Money, AccountingPeriod, BigDecimal or Currency", "remove the money type from the api or event record; organisation master data exposes none"))
                .check(CLASSES);
    }

    @Test
    @DisplayName("§9a nothing in organization.workforce uses Money or AccountingPeriod at all")
    void moduleUsesNoMoney() {
        noClasses().that().resideInAPackage("com.forcitis.one.organization.workforce..")
                .should().dependOnClassesThat().haveFullyQualifiedName("com.forcitis.one.kernel.Money")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("com.forcitis.one.kernel.AccountingPeriod")
                .because(GuardMessage.of("§9a", "a class of organization.workforce uses Money or AccountingPeriod", "remove the use; organisation master data holds no money or accounting period"))
                .check(CLASSES);
    }
}
