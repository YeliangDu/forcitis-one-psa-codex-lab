package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Card #1478: under the profile local the clock can be read, set to a golden date and reset through its endpoint. */
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class LocalClockIT extends IdentityIntegrationTest {

    static final String CLOCK = "/api/v1/platform/local-clocks";

    @Test
    @DisplayName("#1478 the clock endpoint reads, sets a golden date and resets")
    void setAndReset() throws Exception {
        var before = body(mvc.perform(get(CLOCK)).andReturn());
        assertThat(before.path("adjusted").asBoolean()).isFalse();

        var set = body(mvc.perform(post(CLOCK + ":set").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{\"at\":\"2026-03-15T08:00:00Z\"}")).andReturn());
        assertThat(set.path("adjusted").asBoolean()).isTrue();
        assertThat(set.path("instant").asString()).startsWith("2026-03-15T08:00");

        var reset = body(mvc.perform(post(CLOCK + ":reset").with(xsrf())).andReturn());
        assertThat(reset.path("adjusted").asBoolean()).isFalse();
    }
}
