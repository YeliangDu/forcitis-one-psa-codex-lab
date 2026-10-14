package com.forcitis.one.organization.workforce.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.ShortNameSource;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Wiring of the two external sources: off by default, bound from configuration, and the start-up check can stop the application. */
class ExternalSourcesConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ExternalSourcesConfiguration.class);

    private static final String[] A8_ON = {
            "forcitis.organization.workforce.a8.enabled=true",
            "forcitis.organization.workforce.a8.jdbc-url=jdbc:sqlserver://127.0.0.1:1;databaseName=nothing;loginTimeout=1",
            "forcitis.organization.workforce.a8.username=nobody",
            "forcitis.organization.workforce.a8.password=not-a-secret",
            "forcitis.organization.workforce.a8.head-relationship-type-id=-1726439953579618944",
            "forcitis.organization.workforce.a8.regional-receivables-manager-relationship-type-id=-8187907605834545337",
            "forcitis.organization.workforce.a8.delivery-unit-yes-enum-id=-2682590999914657055",
            "forcitis.organization.workforce.a8.fee-earner-yes-enum-id=-3267712510147735998"};

    @Test
    @DisplayName("by default no external connection exists: A8 and the old system are both disabled stand-ins")
    void offByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OrgSource.class)).isInstanceOf(DisabledOrgSource.class);
            assertThat(context.getBean(ShortNameSource.class)).isInstanceOf(DisabledShortNameSource.class);
            assertThat(context).doesNotHaveBean(ReadOnlyConnection.class);
            assertThat(context.getBean(OrgSource.class).fetchMembersByWecomUserId(new WecomUserId("wx-1"))).isEmpty();
            assertThatThrownBy(() -> context.getBean(OrgSource.class).fetchAll()).isInstanceOf(SourceUnavailableException.class);
            assertThatThrownBy(() -> context.getBean(ShortNameSource.class).fetchShortNames()).isInstanceOf(SourceUnavailableException.class);
        });
    }

    @Test
    @DisplayName("a8.enabled binds forcitis.organization.workforce.a8.* (ids are configuration) and wires the qualified read-only connection")
    void a8Enabled() {
        runner.withPropertyValues(A8_ON).withPropertyValues("forcitis.organization.workforce.a8.validate-on-startup=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OrgSource.class)).isInstanceOf(A8OrgSource.class);
            assertThat(context.getBeansOfType(ReadOnlyConnection.class)).containsOnlyKeys("a8Connection");
            A8Properties properties = context.getBean(A8Properties.class);
            assertThat(properties.headRelationshipTypeId()).isEqualTo(-1726439953579618944L);
            assertThat(properties.regionalReceivablesManagerRelationshipTypeId()).isEqualTo(-8187907605834545337L);
            assertThat(properties.deliveryUnitYesEnumId()).isEqualTo("-2682590999914657055");
            assertThat(properties.mapping().configurationProblems()).isEmpty();
            assertThatThrownBy(() -> context.getBean(OrgSource.class).fetchAll())
                    .isInstanceOf(SourceUnavailableException.class)
                    .hasMessageStartingWith("A8 views cannot be read")
                    .hasMessageNotContaining("127.0.0.1").hasMessageNotContaining("not-a-secret");
        });
    }

    @Test
    @DisplayName("§9a with the check on (default) an A8 that cannot be verified stops the application from starting")
    void startupCheckStopsTheApplication() {
        runner.withPropertyValues(A8_ON).withPropertyValues("forcitis.organization.workforce.a8.validate-on-startup=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("A8 view check failed, refusing to start").hasMessageContaining("A8 cannot be checked");
        });
    }

    @Test
    @DisplayName("RULE-356 a missing relationship type id or enum id is reported by name at start-up")
    void missingIdsAreNamed() {
        runner.withPropertyValues("forcitis.organization.workforce.a8.enabled=true",
                        "forcitis.organization.workforce.a8.jdbc-url=jdbc:sqlserver://127.0.0.1:1;loginTimeout=1",
                        "forcitis.organization.workforce.a8.validate-on-startup=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("head-relationship-type-id is not configured")
                            .hasMessageContaining("delivery-unit-yes-enum-id is not configured")
                            .hasMessageContaining("fee-earner-yes-enum-id is not configured");
                });
    }

    @Test
    @DisplayName("legacy.enabled wires the short-name source on the connection qualified 'legacy'")
    void legacyEnabled() {
        runner.withPropertyValues("forcitis.organization.workforce.legacy.enabled=true",
                        "forcitis.organization.workforce.legacy.jdbc-url=jdbc:sqlserver://127.0.0.1:1;loginTimeout=1",
                        "forcitis.organization.workforce.legacy.username=nobody", "forcitis.organization.workforce.legacy.password=not-a-secret",
                        "forcitis.organization.workforce.legacy.validate-on-startup=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ShortNameSource.class)).isInstanceOf(LegacyShortNameSource.class);
                    assertThat(context.getBeansOfType(ReadOnlyConnection.class)).containsOnlyKeys("legacyConnection");
                    assertThat(context.getBean(OrgSource.class)).isInstanceOf(DisabledOrgSource.class);
                });
    }
}
