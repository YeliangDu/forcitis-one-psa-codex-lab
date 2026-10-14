package com.forcitis.one.platform.integration.infrastructure.wecom;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Off by default: no WeCom call, no delivery job; credentials come from environment variables only. */
@ConfigurationProperties("forcitis.platform.integration.wecom")
record WecomProperties(@DefaultValue("false") boolean enabled, @DefaultValue("https://qyapi.weixin.qq.com") String baseUrl,
                       @DefaultValue("") String corpId, @DefaultValue("") String secret, @DefaultValue("") String agentId,
                       @DefaultValue("5s") Duration connectTimeout, @DefaultValue("5s") Duration readTimeout,
                       @DefaultValue Recipients recipients, @DefaultValue Delivery delivery) {

    /** Restricted by default; production sets restricted=false explicitly. */
    record Recipients(@DefaultValue("true") boolean restricted, @DefaultValue List<String> allowed) {
    }

    /** duplicateTaskErrorCodes: WeCom errcodes that mean a card with this task_id was already sent; the public error list names none, so verify before use. */
    record Delivery(@DefaultValue("PT5S") Duration interval, @DefaultValue List<Integer> duplicateTaskErrorCodes) {
    }
}
