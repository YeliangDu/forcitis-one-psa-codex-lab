package com.forcitis.one.platform.integration.infrastructure.seeyon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.OrgSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SeeyonConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SeeyonConfiguration.class);

    private static final String PREFIX = "forcitis.platform.integration.seeyon.views.";
    private static final String[] VIEWS_ON = {
            PREFIX + "enabled=true",
            PREFIX + "jdbc-url=jdbc:sqlserver://127.0.0.1:1;databaseName=nothing;loginTimeout=1",
            PREFIX + "username=nobody",
            PREFIX + "password=not-a-secret",
            PREFIX + "head-relationship-type-id=-1726439953579618944",
            PREFIX + "regional-receivables-manager-relationship-type-id=-8187907605834545337",
            PREFIX + "delivery-unit-yes-enum-id=-2682590999914657055",
            PREFIX + "fee-earner-yes-enum-id=-3267712510147735998",
            PREFIX + "root-path=00000001"};

    @Test
    @DisplayName("by default no connection exists: the organisation source is a disabled stand-in and answers nothing")
    void offByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OrgSource.class)).isInstanceOf(DisabledOrgSource.class);
            assertThat(context).doesNotHaveBean(ReadOnlyConnection.class);
            assertThat(context.getBean(OrgSource.class).findStaffByWecomUserId(new WecomUserId("wx-1"))).isEmpty();
            assertThat(context.getBean(OrgSource.class).findStaffByLoginName("someone")).isEmpty();
            assertThatThrownBy(() -> context.getBean(OrgSource.class).readSnapshot()).isInstanceOf(ExternalSystemUnavailableException.class);
        });
    }

    @Test
    @DisplayName("views.enabled binds forcitis.platform.integration.seeyon.views.* with @ConfigurationProperties (ids are configuration) and wires the qualified read-only connection")
    void viewsEnabled() {
        runner.withPropertyValues(VIEWS_ON).withPropertyValues(PREFIX + "validate-on-startup=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OrgSource.class)).isInstanceOf(SeeyonOrgSource.class);
            assertThat(context.getBeansOfType(ReadOnlyConnection.class)).containsOnlyKeys("seeyonConnection");
            SeeyonProperties.Views views = context.getBean(SeeyonProperties.class).views();
            assertThat(views.headRelationshipTypeId()).isEqualTo(-1726439953579618944L);
            assertThat(views.regionalReceivablesManagerRelationshipTypeId()).isEqualTo(-8187907605834545337L);
            assertThat(views.deliveryUnitYesEnumId()).isEqualTo("-2682590999914657055");
            assertThat(views.rootPath()).isEqualTo("00000001");
            assertThat(views.mapping().configurationProblems()).isEmpty();
            assertThatThrownBy(() -> context.getBean(OrgSource.class).readSnapshot())
                    .isInstanceOf(ExternalSystemUnavailableException.class)
                    .hasMessageStartingWith("Seeyon views cannot be read")
                    .hasMessageNotContaining("127.0.0.1").hasMessageNotContaining("not-a-secret");
        });
    }

    @Test
    @DisplayName("§9a with the check on an organisation database that cannot be verified stops the application from starting")
    void startupCheckStopsTheApplication() {
        runner.withPropertyValues(VIEWS_ON).withPropertyValues(PREFIX + "validate-on-startup=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Seeyon view check failed, refusing to start").hasMessageContaining("Seeyon cannot be checked");
        });
    }

    @Test
    @DisplayName("RULE-356 a missing relationship type id or enum id is reported by key at start-up")
    void missingIdsAreNamed() {
        runner.withPropertyValues(PREFIX + "enabled=true",
                        PREFIX + "jdbc-url=jdbc:sqlserver://127.0.0.1:1;loginTimeout=1",
                        PREFIX + "validate-on-startup=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("head-relationship-type-id is not configured")
                            .hasMessageContaining("root-path must be digits")
                            .hasMessageContaining("delivery-unit-yes-enum-id is not configured")
                            .hasMessageContaining("fee-earner-yes-enum-id is not configured");
                });
    }
}
