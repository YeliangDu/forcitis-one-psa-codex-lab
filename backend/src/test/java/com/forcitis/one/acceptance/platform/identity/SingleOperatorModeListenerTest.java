package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** The listener and its decision are package-private to the module, so the decision is reached by reflection. */
class SingleOperatorModeListenerTest {

    static boolean resolve(ConfigurableEnvironment environment) {
        try {
            Method decision = Class.forName("com.forcitis.one.platform.identity.infrastructure.startup.SingleOperatorModeListener").getDeclaredMethod("resolve",
                    ConfigurableEnvironment.class);
            decision.setAccessible(true);
            return (boolean) decision.invoke(null, environment);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(failure.getCause());
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError("the listener's decision method is not where the acceptance expects it", unexpected);
        }
    }

    static final String KEY = "forcitis.platform.identity.single-operator-mode";
    static final String ENV_NAME = "FORCITIS_PLATFORM_IDENTITY_SINGLE_OPERATOR_MODE";

    static StandardEnvironment environment(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.setActiveProfiles(profiles);
        return environment;
    }

    static StandardEnvironment withKey(String value, String... profiles) {
        StandardEnvironment environment = environment(profiles);
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(KEY, value)));
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    static StandardEnvironment withoutKey(String... profiles) {
        StandardEnvironment environment = environment(profiles);
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    static PropertySource<?> environmentVariable(String value) {
        return new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(ENV_NAME, value));
    }

    static PropertySource<?> commandLine(String name, String value) {
        return new SimpleCommandLinePropertySource("--" + name + "=" + value);
    }

    static StandardEnvironment fromSource(PropertySource<?> source, String... profiles) {
        StandardEnvironment environment = environment(profiles);
        environment.getPropertySources().addFirst(source);
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    static StandardEnvironment fromJson(String json, String... profiles) {
        StandardEnvironment environment = environment(profiles);
        environment.getPropertySources().addFirst(new MapPropertySource("json", Map.of("spring.application.json", json)));
        new SpringApplicationJsonEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    static String json(String value) {
        return "{\"forcitis\":{\"platform\":{\"identity\":{\"single-operator-mode\":\"" + value + "\"}}}}";
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("notProd")
    @DisplayName("SINGLE-OP P1-01 键未设置：无 profile、dev、dev+identity-operations 都不失败，模式为关闭")
    void p101_unsetIsOff(List<String> profiles) {
        assertThat(resolve(withoutKey(profiles.toArray(String[]::new)))).isFalse();
    }

    @Test
    @DisplayName("SINGLE-OP P1-01 键未设置：prod 同样不因此失败，模式为关闭")
    void p101_unsetUnderProdIsOff() {
        assertThat(resolve(withoutKey("prod"))).isFalse();
    }

    static Stream<List<String>> notProd() {
        return Stream.of(List.of(), List.of("dev"), List.of("dev", "identity-operations"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("falseProfiles")
    @DisplayName("SINGLE-OP P1-02 值恰为 false：dev、无 profile、非 dev 非 prod 的 profile 都通过，模式为关闭")
    void p102_falseIsOff(List<String> profiles) {
        assertThat(resolve(withKey("false", profiles.toArray(String[]::new)))).isFalse();
    }

    static Stream<List<String>> falseProfiles() {
        return Stream.of(List.of("dev"), List.of(), List.of("staging"), List.of("test"), List.of("dev", "identity-operations"));
    }

    @Test
    @DisplayName("SINGLE-OP P1-03 值恰为 true 且 profile 含 dev 不含 prod：通过，模式为开启（含 dev 与其他 profile 同时出现）")
    void p103_trueWithDevIsOn() {
        assertThat(resolve(withKey("true", "dev"))).isTrue();
        assertThat(resolve(withKey("true", "dev", "identity-operations"))).isTrue();
        assertThat(resolve(withKey("true", "dev", "test"))).isTrue();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "   ", " true", "true ", "TRUE", "True", "yes", "1", "on", "null", "FALSE", "False", "0", "no"})
    @DisplayName("SINGLE-OP P1-04 取值不是字面量 true 或 false 时启动失败（逐值）")
    void p104_notALiteralFails(String value) {
        assertThatThrownBy(() -> resolve(withKey(value, "dev"))).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"environment variable", "command line", "SPRING_APPLICATION_JSON", "camel case on the command line"})
    @DisplayName("SINGLE-OP P1-05 同一判定覆盖全部来源：字面量 true 在每个来源下都被识别为开启")
    void p105_everySourceIsRead(String source) {
        assertThat(resolve(bySource(source, "true"))).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"environment variable", "command line", "SPRING_APPLICATION_JSON", "camel case on the command line"})
    @DisplayName("SINGLE-OP P1-05 同一判定覆盖全部来源：空串在每个来源下都失败，不被当作未设置")
    void p105_emptyFailsInEverySource(String source) {
        assertThatThrownBy(() -> resolve(bySource(source, ""))).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"environment variable", "command line", "SPRING_APPLICATION_JSON", "camel case on the command line"})
    @DisplayName("SINGLE-OP P1-05 同一判定覆盖全部来源：乱码在每个来源下都失败")
    void p105_garbageFailsInEverySource(String source) {
        assertThatThrownBy(() -> resolve(bySource(source, "maybe"))).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"environment variable", "command line", "SPRING_APPLICATION_JSON", "camel case on the command line"})
    @DisplayName("SINGLE-OP P1-05 同一判定覆盖全部来源：在每个来源下，prod 加键即失败")
    void p105_prodRefusesInEverySource(String source) {
        StandardEnvironment environment = bySource(source, "false");
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> resolve(environment)).isInstanceOf(IllegalStateException.class);
    }

    static StandardEnvironment bySource(String source, String value) {
        return switch (source) {
            case "environment variable" -> fromSource(environmentVariable(value), "dev");
            case "command line" -> fromSource(commandLine(KEY, value), "dev");
            case "SPRING_APPLICATION_JSON" -> fromJson(json(value), "dev");
            case "camel case on the command line" -> fromSource(commandLine("forcitis.platform.identity.singleOperatorMode", value), "dev");
            default -> throw new IllegalArgumentException(source);
        };
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"true", "false", "", "garbage", " true", "true "})
    @DisplayName("SINGLE-OP P1-06 激活 profile 含 prod 时，键存在即启动失败（逐值）")
    void p106_prodWithKeyFails(String value) {
        assertThatThrownBy(() -> resolve(withKey(value, "prod"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolve(withKey(value, "prod", "identity-operations"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolve(withKey(value, "dev", "prod"))).isInstanceOf(IllegalStateException.class);
    }

    static Stream<List<String>> notDev() {
        return Stream.of(List.of("identity-operations"), List.of(), List.of("prod"), List.of("dev", "prod"), List.of("migrate"), List.of("test"),
                List.of("identity-operations", "prod"), List.of("staging"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("notDev")
    @DisplayName("SINGLE-OP P1-07 值为 true 时 profile 必须含 dev 且不含 prod，其余一律失败（逐组合）")
    void p107_trueNeedsDevWithoutProd(List<String> profiles) {
        assertThatThrownBy(() -> resolve(withKey("true", profiles.toArray(String[]::new)))).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "[{index}] key={0}")
    @ValueSource(strings = {"unset", "false", "true", "garbage"})
    @DisplayName("SINGLE-OP P1-08 identity-operations 不带 dev 或 prod 时启动失败，无论模式键是否设置")
    void p108_identityOperationsNeedsAnEnvironmentProfile(String key) {
        assertThatThrownBy(() -> resolve(key.equals("unset") ? withoutKey("identity-operations") : withKey(key, "identity-operations")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolve(key.equals("unset") ? withoutKey("identity-operations", "test") : withKey(key, "identity-operations", "test")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("SINGLE-OP P1-08 identity-operations+dev 与 identity-operations+prod 在键未设置时通过，模式为关闭")
    void p108_identityOperationsWithAnEnvironmentProfilePasses() {
        assertThat(resolve(withoutKey("identity-operations", "dev"))).isFalse();
        assertThat(resolve(withoutKey("identity-operations", "prod"))).isFalse();
    }

    @Test
    @DisplayName("SINGLE-OP P1-09 启动失败的信息指明配置键与 ADR-0030，且不回显取值之外的任何配置值（数据源口令、地址）")
    void p109_failureNamesKeyAndAdrAndLeaksNothing() {
        String fakePassword = "fake-datasource-password-p109";
        String fakeUrl = "jdbc:postgresql://fake-host-p109:5432/fake_db_p109";
        List<StandardEnvironment> failing = List.of(withKey("bogus", "dev"), withKey("true", "prod"), withKey("true", "dev", "prod"), withKey("true"));
        for (StandardEnvironment environment : failing) {
            environment.getPropertySources().addLast(new MapPropertySource("secrets", Map.of("spring.datasource.password", fakePassword, "spring.datasource.url", fakeUrl)));
            assertThatThrownBy(() -> resolve(environment)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(KEY).hasMessageContaining("ADR-0030").hasMessageNotContaining(fakePassword).hasMessageNotContaining(fakeUrl)
                    .hasMessageNotContaining("fake-host-p109");
        }
    }


    static StandardEnvironment byDefaultProfiles(String value, String... defaults) {
        StandardEnvironment environment = value == null ? withoutKey() : withKey(value);
        environment.setDefaultProfiles(defaults);
        return environment;
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"true", "false", "", "garbage", " true"})
    @DisplayName("SINGLE-OP P1-06 没有激活 profile 时 default profile 起作用：default 含 prod 且键存在（任何取值，含 false）即启动失败")
    void p106_defaultProfileProdWithKeyFails(String value) {
        assertThatThrownBy(() -> resolve(byDefaultProfiles(value, "prod"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolve(byDefaultProfiles(value, "dev", "prod"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("SINGLE-OP P1-06 default profile 含 prod 而键未设置：通过，模式为关闭")
    void p106_defaultProfileProdWithoutKeyPasses() {
        assertThat(resolve(byDefaultProfiles(null, "prod"))).isFalse();
    }

    @ParameterizedTest(name = "[{index}] key={0}")
    @ValueSource(strings = {"unset", "false"})
    @DisplayName("SINGLE-OP P1-08 没有激活 profile、default profile 含 identity-operations 而 default 不含 dev 或 prod：启动失败")
    void p108_defaultProfileIdentityOperationsAloneFails(String key) {
        String value = key.equals("unset") ? null : key;
        assertThatThrownBy(() -> resolve(byDefaultProfiles(value, "identity-operations"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolve(byDefaultProfiles(value, "identity-operations", "test"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("SINGLE-OP P1-08 没有激活 profile、default profile 含 identity-operations,dev（或 identity-operations,prod）在键未设置时通过，模式为关闭")
    void p108_defaultProfileIdentityOperationsWithEnvironmentPasses() {
        assertThat(resolve(byDefaultProfiles(null, "identity-operations", "dev"))).isFalse();
        assertThat(resolve(byDefaultProfiles(null, "identity-operations", "prod"))).isFalse();
    }

    @Test
    @DisplayName("SINGLE-OP P1-07 值为 true 时仍要求激活 profile 显式含 dev：default 里的 dev 不算开启")
    void p107_devInTheDefaultProfilesDoesNotEnable() {
        assertThatThrownBy(() -> resolve(byDefaultProfiles("true", "dev"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolve(byDefaultProfiles("true", "identity-operations", "dev"))).isInstanceOf(IllegalStateException.class);
    }
}
