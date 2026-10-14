package com.forcitis.one.platform.integration.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LegacyConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(LegacyConfiguration.class);

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
    @DisplayName("enabled wires the short-name read on its own read-only connection and the view name from configuration")
    void enabled() {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "jdbc-url=jdbc:sqlserver://127.0.0.1:1;loginTimeout=1",
                        PREFIX + "username=nobody", PREFIX + "password=not-a-secret", PREFIX + "validate-on-startup=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OrgShortNames.class)).isInstanceOf(LegacyOrgNames.class);
                    assertThat(context.getBean(LegacyProperties.class).viewName()).isEqualTo("dbo.v_one_legacy_org_short_name");
                    assertThatThrownBy(() -> context.getBean(OrgShortNames.class).readShortNames())
                            .isInstanceOf(ExternalSystemUnavailableException.class)
                            .hasMessageNotContaining("127.0.0.1").hasMessageNotContaining("not-a-secret");
                });
    }

    @Test
    @DisplayName("a view name that is not [schema.]name is refused when the configuration is bound")
    void viewNameIsValidated() {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "view-name=dbo.v; drop table x").run(context -> assertThat(context).hasFailed());
    }
}
