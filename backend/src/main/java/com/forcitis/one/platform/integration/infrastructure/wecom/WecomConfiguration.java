package com.forcitis.one.platform.integration.infrastructure.wecom;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.domain.RecipientPolicy;
import com.forcitis.one.platform.integration.domain.WecomClient;
import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(WecomProperties.class)
class WecomConfiguration {

    @Bean
    WecomClient wecomClient(WecomProperties properties, ObjectProvider<JsonMapper> json, Clock clock) {
        if (!properties.enabled()) {
            return new DisabledWecomClient();
        }
        require(properties.corpId(), "forcitis.platform.integration.wecom.corp-id");
        require(properties.secret(), "forcitis.platform.integration.wecom.secret");
        require(properties.agentId(), "forcitis.platform.integration.wecom.agent-id");
        if (!properties.agentId().matches("\\d{1,18}")) {
            throw new IllegalStateException("forcitis.platform.integration.wecom.agent-id must be a number");
        }
        if (!properties.baseUrl().startsWith("https://") && !properties.baseUrl().startsWith("http://")) {
            throw new IllegalStateException("forcitis.platform.integration.wecom.base-url must be an http(s) URL");
        }
        return new HttpWecomClient(properties, json.getIfAvailable(() -> JsonMapper.builder().build()), clock);
    }

    @Bean
    RecipientPolicy recipientPolicy(WecomProperties properties) {
        Set<WecomUserId> allowed = properties.recipients().allowed().stream().map(String::trim).filter(id -> !id.isEmpty())
                .map(WecomUserId::new).collect(Collectors.toSet());
        return new RecipientPolicy(properties.recipients().restricted(), allowed);
    }

    private static void require(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required when forcitis.platform.integration.wecom.enabled=true (set it from the environment)");
        }
    }
}
