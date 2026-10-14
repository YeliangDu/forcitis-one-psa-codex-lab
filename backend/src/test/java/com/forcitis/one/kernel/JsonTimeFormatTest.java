package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/** The JSON the application sends is built by the mapper Spring Boot configures from application.properties, whatever zone the machine is in. */
class JsonTimeFormatTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));
    private final TimeZone original = TimeZone.getDefault();

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(original);
    }

    record Moments(Instant instant, OffsetDateTime offset, Date legacy, LocalDate businessDay, Duration duration) {
    }

    private String json(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        String[] out = new String[1];
        runner.run(context -> out[0] = context.getBean(JsonMapper.class).writeValueAsString(new Moments(Instant.parse("2026-10-14T04:00:00Z"),
                OffsetDateTime.of(2026, 10, 14, 12, 0, 0, 0, ZoneOffset.ofHours(8)), Date.from(Instant.parse("2026-10-14T04:00:00Z")),
                LocalDate.of(2026, 10, 14), Duration.ofMinutes(90))));
        return out[0];
    }

    @Test
    @DisplayName("every moment is written in UTC as ISO-8601 with its offset (Z), durations are ISO-8601, a business day is a plain date, and none of it moves with the machine's zone")
    void instantsCarryTheirOffsetWhateverTheMachineZoneIs() {
        for (String zone : new String[] {"UTC", "Asia/Shanghai", "America/New_York"}) {
            String json = json(zone);

            assertThat(json).as(zone).contains("\"instant\":\"2026-10-14T04:00:00Z\"")
                    .contains("\"offset\":\"2026-10-14T04:00:00Z\"")
                    .contains("\"legacy\":\"2026-10-14T04:00:00.000Z\"")
                    .contains("\"businessDay\":\"2026-10-14\"")
                    .contains("\"duration\":\"PT1H30M\"");
        }
    }

    @Test
    @DisplayName("the mapper is configured for UTC explicitly, not by default")
    void zoneIsConfigured() {
        runner.run(context -> assertThat(context.getBean(JsonMapper.class).serializationConfig().getTimeZone()).isEqualTo(TimeZone.getTimeZone(ZoneId.of("UTC"))));
    }
}
