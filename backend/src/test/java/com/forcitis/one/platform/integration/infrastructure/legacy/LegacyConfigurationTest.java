package com.forcitis.one.platform.integration.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.domain.ExternalConnections;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import com.forcitis.one.platform.integration.application.InMemorySourceReferences;
import com.forcitis.one.platform.integration.application.SourceReferences;
import com.forcitis.one.platform.integration.domain.ReadOnlyConnectionCheck;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LegacyConfigurationTest {

    private final List<String> rechecked = new ArrayList<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(LegacyConfiguration.class)
            .withBean(ExternalConnections.class, () -> (poolName, url, user, password, kind) -> new DriverManagerDataSource(url, user, password))
            .withBean(ReadOnlyConnectionCheck.class, () -> rechecked::add)
            .withBean(SourceReferences.class, InMemorySourceReferences::new);

    private static final String PREFIX = "forcitis.platform.integration.legacy.";

    @Test
    @DisplayName("by default no connection exists: a disabled stand-in answers")
    void offByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OrgShortNames.class)).isInstanceOf(DisabledOrgNames.class);
            assertThat(context).doesNotHaveBean(LegacyConfiguration.Connection.class);
            assertThatThrownBy(() -> context.getBean(OrgShortNames.class).readShortNames()).isInstanceOf(ExternalSystemUnavailableException.class);
        });
    }

    @Test
    @DisplayName("enabled wires the short-name read on its own read-only connection and the view name from configuration; each read re-checks the login first (ADR37-1)")
    void enabled() {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "jdbc-url=jdbc:sqlserver://127.0.0.1:1;loginTimeout=1",
                        PREFIX + "username=nobody", PREFIX + "password=not-a-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OrgShortNames.class)).isInstanceOf(LegacyOrgNames.class);
                    assertThat(context.getBean(LegacyProperties.class).viewName()).isEqualTo("dbo.v_one_legacy_org_short_name");
                    assertThatThrownBy(() -> context.getBean(OrgShortNames.class).readShortNames())
                            .isInstanceOf(ExternalSystemUnavailableException.class)
                            .hasMessageContaining("SQLState").hasNoCause()
                            .hasMessageNotContaining("127.0.0.1").hasMessageNotContaining("not-a-secret");
                    assertThat(rechecked).containsExactly("integration-legacy");
                });
    }

    @Test
    @DisplayName("ADR37-1 a login the connection factory refuses stops the application from starting; there is no switch to skip it")
    void refusedLoginStopsTheApplication() {
        new ApplicationContextRunner().withUserConfiguration(LegacyConfiguration.class)
                .withBean(ExternalConnections.class, () -> (poolName, url, user, password, kind) -> {
                    throw new IllegalStateException("The external connection " + poolName + " is refused: (c) db_datareader");
                })
                .withBean(ReadOnlyConnectionCheck.class, () -> name -> { })
                .withBean(SourceReferences.class, InMemorySourceReferences::new)
                .withPropertyValues(PREFIX + "enabled=true", PREFIX + "jdbc-url=jdbc:sqlserver://127.0.0.1:1;loginTimeout=1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("(c)");
                });
    }

    @Test
    @DisplayName("a view name that is not [schema.]name is refused when the configuration is bound")
    void viewNameIsValidated() {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "view-name=dbo.v; drop table x").run(context -> assertThat(context).hasFailed());
    }
}
