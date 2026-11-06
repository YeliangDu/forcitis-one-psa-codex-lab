package com.forcitis.one.platform.integration.infrastructure.wecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.domain.RecipientPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tools.jackson.databind.json.JsonMapper;

class WecomConfigurationTest {

    static final String PREFIX = "forcitis.platform.integration.wecom";
    static final StaticListableBeanFactory NO_BEANS = new StaticListableBeanFactory();

    private static WecomProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source)).bind(PREFIX, WecomProperties.class).orElseGet(
                () -> new Binder(new MapConfigurationPropertySource(Map.of(PREFIX + ".enabled", "false"))).bind(PREFIX, WecomProperties.class).get());
    }

    @Test
    @DisplayName("ARCH 3.7 connect and read timeouts are 5 s unless configured otherwise")
    void timeoutsDefaultToFiveSeconds() {
        WecomProperties properties = bind(Map.of(PREFIX + ".enabled", "true"));

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("the shipped configuration keeps WeCom off, restricts recipients, and holds the 5 s timeouts and no credential value")
    void shippedDefaultsAreSafe() throws Exception {
        Properties shipped = new Properties();
        shipped.load(Files.newBufferedReader(Path.of("src/main/resources/config/platform_integration.properties")));

        assertThat(shipped.getProperty(PREFIX + ".enabled")).isEqualTo("${WECOM_ENABLED:false}");
        assertThat(shipped.getProperty(PREFIX + ".recipients.restricted")).isEqualTo("${WECOM_RESTRICT_RECIPIENTS:true}");
        assertThat(shipped.getProperty(PREFIX + ".connect-timeout")).isEqualTo("5s");
        assertThat(shipped.getProperty(PREFIX + ".read-timeout")).isEqualTo("5s");
        for (String credential : List.of("corp-id", "secret", "agent-id")) {
            assertThat(shipped.getProperty(PREFIX + "." + credential)).matches("\\$\\{WECOM_[A-Z_]+:}");
        }
    }

    @Test
    @DisplayName("while WeCom is off no call is made: sending and code exchange are unavailable")
    void disabledClientRefusesEverything() {
        var client = new WecomConfiguration().wecomClient(bind(Map.of(PREFIX + ".enabled", "false")),
                NO_BEANS.getBeanProvider(JsonMapper.class), com.forcitis.one.BusinessDay.fixedClock());

        assertThat(client).isInstanceOf(DisabledWecomClient.class);
        assertThatThrownBy(() -> client.userIdOfCode("code", WecomEntry.WORKBENCH)).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    @DisplayName("switching WeCom on without corp id, secret and agent id stops the application")
    void enabledNeedsCredentials() {
        WecomProperties missing = bind(Map.of(PREFIX + ".enabled", "true"));

        assertThatThrownBy(() -> new WecomConfiguration().wecomClient(missing, NO_BEANS.getBeanProvider(JsonMapper.class),
                com.forcitis.one.BusinessDay.fixedClock())).isInstanceOf(IllegalStateException.class).hasMessageContaining("corp-id");

        WecomProperties notNumeric = bind(Map.of(PREFIX + ".enabled", "true", PREFIX + ".corp-id", "c-" + UUID.randomUUID(),
                PREFIX + ".secret", "s-" + UUID.randomUUID(), PREFIX + ".agent-id", "abc"));
        assertThatThrownBy(() -> new WecomConfiguration().wecomClient(notNumeric, NO_BEANS.getBeanProvider(JsonMapper.class),
                com.forcitis.one.BusinessDay.fixedClock())).isInstanceOf(IllegalStateException.class).hasMessageContaining("agent-id");
    }

    @Test
    @DisplayName("RULE-375 recipients are restricted unless production switches that off; the allow list ignores blanks")
    void recipientPolicyFollowsConfiguration() {
        WecomConfiguration configuration = new WecomConfiguration();

        RecipientPolicy byDefault = configuration.recipientPolicy(bind(Map.of(PREFIX + ".recipients.allowed[0]", "alice",
                PREFIX + ".recipients.allowed[1]", " ")));
        assertThat(byDefault.allowListOnly()).isTrue();
        assertThat(byDefault.allowed()).containsExactly(new WecomUserId("alice"));

        RecipientPolicy production = configuration.recipientPolicy(bind(Map.of(PREFIX + ".recipients.restricted", "false")));
        assertThat(production.allowListOnly()).isFalse();
    }
}
