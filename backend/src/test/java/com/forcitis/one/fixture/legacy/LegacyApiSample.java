package com.forcitis.one.fixture.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.web.client.RestTemplate;

/** Deliberately wrong: one use of each legacy API LegacyApiGuardTest bans. Only LegacyApiGuardTest loads it. */
public final class LegacyApiSample {
    Icon outsideTheJdkAllowList;
    RestTemplate http;
    TestRestTemplate testHttp;
    ObjectMapper jackson2;
    LocalDateTime stamp;
    List<LocalDateTime> stamps;
    @SuppressWarnings("deprecation")
    @org.springframework.lang.Nullable String springNullable;

    public void at(LocalDateTime moment) {}

    public LocalDateTime made() {
        return null;
    }

    public Optional<LocalDateTime> later() {
        return Optional.empty();
    }
}
