package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.JavaCode;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D1-1 (principles audit A 3-H1, guard G-I1): the A8 and old-system connections are built in exactly one place, a class named
 * *ConnectionFactory, and that place sets the three timeouts. The live effect of the timeouts is pinned by
 * acceptance.platform.integration.StalledExternalSourceIT; this guard keeps a new adapter from opening a connection of its own that bypasses them.
 */
class ExternalConnectionFactoryGuardTest {

    static final String ROOT = "com.forcitis.one";
    static final Path MAIN_SOURCES = Path.of("src/main/java");
    static final String FACTORY_SUFFIX = "ConnectionFactory";

    /** Everything that can open a JDBC connection to a database of the application's own choosing. */
    static final Set<String> CONNECTION_BUILDERS = Set.of(
            "com.zaxxer.hikari.HikariConfig",
            "com.zaxxer.hikari.HikariDataSource",
            "java.sql.Driver",
            "java.sql.DriverManager",
            "org.springframework.boot.jdbc.DataSourceBuilder");

    static final List<String> CONNECTION_PACKAGES = List.of("com.microsoft.sqlserver", "org.postgresql", "org.springframework.jdbc.datasource");

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    static Set<String> classesThatBuildConnections() {
        return CLASSES.stream()
                .filter(c -> c.getDirectDependenciesFromSelf().stream().map(d -> d.getTargetClass())
                        .anyMatch(t -> CONNECTION_BUILDERS.contains(t.getName())
                                || CONNECTION_PACKAGES.stream().anyMatch(p -> t.getPackageName().equals(p) || t.getPackageName().startsWith(p + "."))))
                .map(ExternalConnectionFactoryGuardTest::topLevelName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static String topLevelName(JavaClass c) {
        String name = c.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    @Test
    @DisplayName("D1-1 外部连接都经工厂：主代码里只有一个 *ConnectionFactory 类可以建连接池或驱动连接")
    void onlyTheConnectionFactoryBuildsConnections() {
        Set<String> builders = classesThatBuildConnections();

        assertThat(builders)
                .as("the A8 and old-system connections must be built by one factory that forces the query, socket and lock-wait timeouts; found these builders")
                .hasSize(1)
                .allSatisfy(name -> assertThat(name).endsWith(FACTORY_SUFFIX));
    }

    @Test
    @DisplayName("D1-1 外部连接工厂的源码写明三种超时：queryTimeout、socketTimeout、LOCK_TIMEOUT")
    void theFactorySetsAllThreeTimeouts() throws IOException {
        Set<String> builders = classesThatBuildConnections();
        Optional<String> factory = builders.stream().filter(name -> name.endsWith(FACTORY_SUFFIX)).findFirst();
        assertThat(factory).as("a *ConnectionFactory class must exist; builders found: " + builders).isPresent();

        Path source = MAIN_SOURCES.resolve(factory.get().replace('.', '/') + ".java");
        assertThat(source).as("source of " + factory.get()).exists();
        String text = JavaCode.withoutComments(Files.readString(source)).toLowerCase(Locale.ROOT);

        assertThat(text).as("statement timeout (JDBC queryTimeout)").contains("\"querytimeout\"");
        assertThat(text).as("network read timeout (JDBC socketTimeout)").contains("\"sockettimeout\"");
        assertThat(text).as("lock wait limit (SET LOCK_TIMEOUT, SQL Server waits for ever by default)").contains("\"set lock_timeout ");
    }
}
