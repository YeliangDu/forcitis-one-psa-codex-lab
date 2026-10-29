package com.forcitis.one.fixture.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import javax.swing.Icon;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.web.client.RestTemplate;

/** Deliberately wrong: one use of each legacy API LegacyApiGuardTest bans. Only LegacyApiGuardTest loads it. */
public final class LegacyApiSample {
    Icon outsideTheJdkAllowList;
    RestTemplate http;
    ObjectMapper jackson2;
    LocalDateTime stamp;
    TestRestTemplate testHttp;

    public LocalDateTime when(LocalDateTime at) {
        return at;
    }
}
