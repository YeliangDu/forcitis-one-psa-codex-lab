package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SsoSwitchConfigurationTest {

    @Test
    @DisplayName("CROSSCHECK 7 the single sign-on switch comes from FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED with no default: a deployment that does not set it does not start")
    void theSwitchHasNoDefault() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(Path.of("src/main/resources/config/platform_identity.properties"))));

        assertThat(properties.getProperty("forcitis.platform.identity.sso.enabled")).isEqualTo("${FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED}");
    }
}
