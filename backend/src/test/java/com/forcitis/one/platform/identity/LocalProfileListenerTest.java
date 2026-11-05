package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

/** Card #1478: the profile local starts only on a developer's own machine, and nothing shipped can switch it on. */
class LocalProfileListenerTest {

    static final String MIGRATE_LISTENER = "com.forcitis.one.platform.integration.infrastructure.transport.MigrateOnlyListener";
    static final String LISTENER = "com.forcitis.one.platform.identity.infrastructure.startup.LocalProfileListener";
    static final Path RESOURCES = Path.of("src/main/resources");
    static final Path JAVA = Path.of("src/main/java");

    /** The listener is package-private: its check is called by reflection, the answer is the problems joined, or null when there are none. */
    private static String refusal(MockEnvironment environment) throws Exception {
        var check = Class.forName(LISTENER).getDeclaredMethod("problems", org.springframework.core.env.ConfigurableEnvironment.class);
        check.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> problems = (List<String>) check.invoke(null, environment);
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    private static MockEnvironment local(String url) {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", url);
        environment.setActiveProfiles("local");
        return environment;
    }

    @Test
    @DisplayName("#1478 the profile local starts with a database on this machine")
    void startsOnThisMachine() throws Exception {
        for (String url : List.of("jdbc:postgresql://localhost:55432/forcitis_local?logServerErrorDetail=false", "jdbc:postgresql://127.0.0.1/forcitis_local",
                "jdbc:postgresql://[::1]:5432/forcitis_local")) {
            assertThat(refusal(local(url))).as(url).isNull();
        }
    }

    @Test
    @DisplayName("#1478 the profile local refuses a database that is not on this machine, however the address is written")
    void refusesOtherDatabases() throws Exception {
        for (String url : List.of("jdbc:postgresql://db.example.internal:5432/forcitis_local", "jdbc:postgresql://10.0.0.5/forcitis_local",
                "jdbc:postgresql://localhost.example.com/forcitis_local", "jdbc:postgresql://localhost,10.0.0.5/forcitis_local",
                "jdbc:postgresql://localhost:5432,10.0.0.5:5432/forcitis_local", "jdbc:postgresql://localhost/forcitis_local?host=10.0.0.5",
                "jdbc:postgresql://localhost/forcitis_local?PGHOST=10.0.0.5", "jdbc:postgresql://user@10.0.0.5/forcitis_local", "jdbc:mysql://localhost/x", "localhost")) {
            assertThat(refusal(local(url))).as(url).contains("does not name a database on this machine").doesNotContain("10.0.0.5").doesNotContain("example");
        }
        MockEnvironment noUrl = new MockEnvironment();
        noUrl.setActiveProfiles("local");
        assertThat(refusal(noUrl)).contains("spring.datasource.url is not set");
    }

    @Test
    @DisplayName("#1478 the migration url is checked too, and dev or prod next to local is refused")
    void refusesFlywayUrlAndSharedProfiles() throws Exception {
        MockEnvironment migration = local("jdbc:postgresql://localhost/forcitis_local").withProperty("spring.flyway.url", "jdbc:postgresql://10.1.1.1/forcitis_local");
        assertThat(refusal(migration)).contains("spring.flyway.url");
        for (String shared : List.of("dev", "prod")) {
            MockEnvironment environment = local("jdbc:postgresql://localhost/forcitis_local");
            environment.setActiveProfiles("local", shared);
            assertThat(refusal(environment)).as(shared).contains("the profile " + shared + " is active too");
        }
    }

    @Test
    @DisplayName("#1478 any other profile is none of its business")
    void ignoresOtherProfiles() throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db.example.internal/x");
        environment.setActiveProfiles("prod");
        assertThat(refusal(environment)).isNull();
    }

    @Test
    @DisplayName("#1478 the listener is registered, so it runs before any bean exists and before migrate")
    void registered() {
        assertThat(SpringFactoriesLoader.forResourceLocation("META-INF/spring.factories").load(ApplicationListener.class).stream().map(l -> l.getClass().getName()))
                .contains(LISTENER);
    }

    @Test
    @DisplayName("#1478 no shipped file switches the profile local on, and only the local classes name it")
    void nothingShippedEnablesLocal() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".properties") && !f.getFileName().toString().equals("application-local.properties")).toList()) {
                Properties properties = new Properties();
                properties.load(new StringReader(Files.readString(file)));
                for (String key : properties.stringPropertyNames()) {
                    if (key.startsWith("spring.profiles.") && properties.getProperty(key).matches("(?s).*\\blocal\\b.*")) {
                        problems.add(file + ": " + key);
                    }
                }
            }
        }
        List<String> namingIt = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).matches("(?s).*(@Profile\\s*\\(\\s*\"local\"|PROFILE\\s*=\\s*\"local\").*")) {
                    namingIt.add(file.getFileName().toString());
                }
            }
        }
        assertThat(problems).isEmpty();
        assertThat(namingIt).containsExactlyInAnyOrder("LocalProfileListener.java", "LoginAsLocalTestUser.java", "LocalTestLoginController.java", "KernelConfiguration.java", "LocalClockController.java", "WorkforceLocalDueJobs.java", "LocalAuditPartitions.java");
    }

    @Test
    @DisplayName("#1478 the listener itself stops the start: it throws for the profile local next to another machine's database, and for nothing else")
    void listenerThrows() throws Exception {
        ApplicationListener<ApplicationEnvironmentPreparedEvent> listener = factoryListener(LISTENER);
        assertThatThrownBy(() -> listener.onApplicationEvent(prepared(local("jdbc:postgresql://db.example.com/forcitis"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("spring.datasource.url").hasMessageNotContaining("example.com");
        listener.onApplicationEvent(prepared(local("jdbc:postgresql://localhost/forcitis_local")));
        MockEnvironment other = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db.example.com/forcitis");
        other.setActiveProfiles("dev");
        listener.onApplicationEvent(prepared(other));
    }

    @Test
    @DisplayName("#1478 a pool url or a data source class that replaces spring.datasource.url is checked like it: the url actually used")
    void poolSettingsAreChecked() throws Exception {
        MockEnvironment pool = local("jdbc:postgresql://localhost/forcitis_local").withProperty("spring.datasource.hikari.jdbc-url", "jdbc:postgresql://db.example.com/forcitis");
        assertThat(refusal(pool)).contains("spring.datasource.hikari.jdbc-url");
        MockEnvironment clazz = local("jdbc:postgresql://localhost/forcitis_local").withProperty("spring.datasource.hikari.data-source-class-name", "org.postgresql.ds.PGSimpleDataSource");
        assertThat(refusal(clazz)).contains("data-source-class-name");
        assertThat(refusal(local("jdbc:postgresql://localhost/forcitis_local").withProperty("spring.datasource.hikari.jdbc-url", "jdbc:postgresql://127.0.0.1/forcitis_local"))).isNull();
    }

    @Test
    @DisplayName("#1478 the profile local that is only the default profile is checked like an active one")
    void defaultProfileIsChecked() throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db.example.com/forcitis");
        environment.setDefaultProfiles("local");
        assertThat(refusal(environment)).contains("spring.datasource.url");
    }

    @Test
    @DisplayName("#1478 the check runs before the migrate listener, which ends the process: in the order Spring sorts the registered listeners")
    void runsBeforeMigrate() {
        List<String> order = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader()).load(ApplicationListener.class).stream()
                .sorted(AnnotationAwareOrderComparator.INSTANCE).map(listener -> listener.getClass().getName()).toList();
        assertThat(order).contains(LISTENER, MIGRATE_LISTENER);
        assertThat(order.indexOf(LISTENER)).isLessThan(order.indexOf(MIGRATE_LISTENER));
    }

    @SuppressWarnings("unchecked")
    private static ApplicationListener<ApplicationEnvironmentPreparedEvent> factoryListener(String name) {
        return SpringFactoriesLoader.forDefaultResourceLocation(LocalProfileListenerTest.class.getClassLoader()).load(ApplicationListener.class).stream()
                .filter(listener -> listener.getClass().getName().equals(name)).findFirst().orElseThrow();
    }

    private static ApplicationEnvironmentPreparedEvent prepared(MockEnvironment environment) {
        return new ApplicationEnvironmentPreparedEvent(new DefaultBootstrapContext(), new SpringApplication(), new String[0], environment);
    }
}
