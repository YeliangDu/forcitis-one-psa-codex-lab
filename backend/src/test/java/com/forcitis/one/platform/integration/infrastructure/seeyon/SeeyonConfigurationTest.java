package com.forcitis.one.platform.integration.infrastructure.seeyon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.domain.ExternalConnections;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.application.InMemorySourceReferences;
import com.forcitis.one.platform.integration.application.SourceReferences;
import com.forcitis.one.platform.integration.domain.ReadOnlyConnectionCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SeeyonConfigurationTest {

    private final List<String> rechecked = new ArrayList<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SeeyonConfiguration.class)
            .withBean(ExternalConnections.class, () -> (poolName, url, user, password, kind) -> new DriverManagerDataSource(url, user, password))
            .withBean(ReadOnlyConnectionCheck.class, () -> rechecked::add)
            .withBean(SourceReferences.class, InMemorySourceReferences::new);

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
            assertThat(context).doesNotHaveBean(SeeyonConnections.class);
            assertThat(context.getBean(OrgSource.class).findStaffByWecomUser(new WecomUserId("wx-1"))).isEmpty();
            assertThat(context.getBean(OrgSource.class).findStaffByLoginName("someone")).isEmpty();
            assertThatThrownBy(() -> context.getBean(OrgSource.class).readSnapshot()).isInstanceOf(ExternalSystemUnavailableException.class);
        });
    }

    @Test
    @DisplayName("forcitis.platform.integration.seeyon.views.* binds with @ConfigurationProperties: the ids are configuration")
    void viewsBind() {
        Map<String, String> values = new java.util.HashMap<>();
        for (String pair : VIEWS_ON) {
            values.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
        }

        SeeyonProperties.Views views = new Binder(new MapConfigurationPropertySource(values))
                .bind("forcitis.platform.integration.seeyon", SeeyonProperties.class).get().views();

        assertThat(views.enabled()).isTrue();
        assertThat(views.headRelationshipTypeId()).isEqualTo(-1726439953579618944L);
        assertThat(views.regionalReceivablesManagerRelationshipTypeId()).isEqualTo(-8187907605834545337L);
        assertThat(views.deliveryUnitYesEnumId()).isEqualTo("-2682590999914657055");
        assertThat(views.rootPath()).isEqualTo("00000001");
        assertThat(views.mapping().configurationProblems()).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 a snapshot re-checks the login first; an unreadable view is 'unavailable' without address, password or database message")
    void snapshotRechecksAndHidesTheFailure() {
        JdbcClient unreachable = JdbcClient.create(new DriverManagerDataSource("jdbc:sqlserver://127.0.0.1:1;databaseName=nothing;loginTimeout=1",
                "nobody", "not-a-secret"));
        List<String> order = new ArrayList<>();
        SeeyonOrgSource source = new SeeyonOrgSource(unreachable, unreachable, new SeeyonMapping(1, 2, "3", "4", "00000001"), () -> order.add("recheck"),
                new SourceIdTranslation(new InMemorySourceReferences()));

        assertThatThrownBy(source::readSnapshot).isInstanceOf(ExternalSystemUnavailableException.class)
                .hasMessageStartingWith("Seeyon views cannot be read").hasMessageContaining("SQLState")
                .hasMessageNotContaining("127.0.0.1").hasMessageNotContaining("not-a-secret").hasNoCause();
        assertThat(order).containsExactly("recheck");
    }

    @Test
    @DisplayName("ADR37-1 a failed re-check stops the snapshot before anything is read")
    void failedRecheckReadsNothing() {
        SeeyonOrgSource source = new SeeyonOrgSource(null, null, new SeeyonMapping(1, 2, "3", "4", "00000001"), () -> {
            throw new ExternalSystemUnavailableException("the external connection integration-seeyon-background is stopped: (b)");
        }, new SourceIdTranslation(new InMemorySourceReferences()));

        assertThatThrownBy(source::readSnapshot).isInstanceOf(ExternalSystemUnavailableException.class).hasMessageContaining("(b)");
    }

    @Test
    @DisplayName("§9a ADR37-1 there is no switch: with views enabled an organisation database that cannot be verified stops the application from starting")
    void startupCheckStopsTheApplication() {
        runner.withPropertyValues(VIEWS_ON).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Seeyon view check failed, refusing to start").hasMessageContaining("Seeyon cannot be checked")
                    .hasMessageNotContaining("not-a-secret");
        });
    }

    @Test
    @DisplayName("ADR37-1 a login the connection factory refuses stops the application from starting")
    void refusedLoginStopsTheApplication() {
        new ApplicationContextRunner().withUserConfiguration(SeeyonConfiguration.class)
                .withBean(ExternalConnections.class, () -> (poolName, url, user, password, kind) -> {
                    throw new IllegalStateException("The external connection " + poolName + " is refused: (e) owns 1 schema(s)");
                })
                .withBean(ReadOnlyConnectionCheck.class, () -> name -> { })
                .withBean(SourceReferences.class, InMemorySourceReferences::new)
                .withPropertyValues(VIEWS_ON)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("(e)");
                });
    }

    @Test
    @DisplayName("RULE-356 a missing relationship type id or enum id is reported by key at start-up")
    void missingIdsAreNamed() {
        runner.withPropertyValues(PREFIX + "enabled=true",
                        PREFIX + "jdbc-url=jdbc:sqlserver://127.0.0.1:1;loginTimeout=1")
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
