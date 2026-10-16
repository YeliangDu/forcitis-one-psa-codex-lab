package com.forcitis.one.acceptance.platform.identity;

import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.DEV_DATABASE;
import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.DEV_USER;
import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.OneApplication;
import com.forcitis.one.platform.identity.api.SingleOperatorMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

class SingleOperatorModeQueryIT {

    @BeforeAll
    static void container() {
        SingleOperatorModeEnvironment.prepare();
    }

    private static ConfigurableApplicationContext start(String mode) {
        List<String> arguments = SingleOperatorModeEnvironment.residentArguments("dev", DEV_DATABASE, DEV_USER, mode, 0, false);
        return new SpringApplicationBuilder(OneApplication.class).web(WebApplicationType.SERVLET).run(arguments.toArray(String[]::new));
    }

    private static HttpResponse<String> call(ConfigurableApplicationContext context, String method, String path) throws Exception {
        String port = context.getEnvironment().getProperty("local.server.port");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).method(method, HttpRequest.BodyPublishers.noBody()).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("SINGLE-OP P1-10 只在启动时读取：上下文启动后改环境属性源，模式查询的结果不变（true 改 false、false 改 true）")
    void p110_readOnlyAtStart() {
        try (ConfigurableApplicationContext on = start("true")) {
            assertThat(on.getBean(SingleOperatorMode.class).enabled()).isTrue();

            on.getEnvironment().getPropertySources().addFirst(new MapPropertySource("late", Map.of(KEY, "false")));

            assertThat(on.getBean(SingleOperatorMode.class).enabled()).isTrue();
        }
        try (ConfigurableApplicationContext off = start("false")) {
            assertThat(off.getBean(SingleOperatorMode.class).enabled()).isFalse();

            off.getEnvironment().getPropertySources().addFirst(new MapPropertySource("late", Map.of(KEY, "true")));

            assertThat(off.getBean(SingleOperatorMode.class).enabled()).isFalse();
        }
    }

    @Test
    @DisplayName("SINGLE-OP P1-10 模式未设置时查询为关闭；没有刷新入口，也没有对外 API 暴露模式：actuator 仍只暴露 health，env、configprops、refresh 不可达")
    void p110_noRefreshAndNoExposure() throws Exception {
        try (ConfigurableApplicationContext context = start("true")) {
            assertThat(context.getEnvironment().getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
            for (String path : List.of("/actuator/env", "/actuator/configprops", "/actuator/beans", "/actuator/refresh", "/actuator/restart")) {
                for (String method : List.of("GET", "POST")) {
                    HttpResponse<String> response = call(context, method, path);

                    assertThat(response.statusCode()).as("%s %s", method, path).isNotEqualTo(200);
                    assertThat(response.body()).as("%s %s", method, path).doesNotContain("single-operator-mode");
                }
            }
        }
        try (ConfigurableApplicationContext unset = start(null)) {
            assertThat(unset.getBean(SingleOperatorMode.class).enabled()).isFalse();
        }
    }
}
