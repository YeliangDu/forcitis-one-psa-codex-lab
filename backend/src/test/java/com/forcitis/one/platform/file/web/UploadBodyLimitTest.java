package com.forcitis.one.platform.file.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.util.unit.DataSize;

class UploadBodyLimitTest {
    static final DataSize FILE_LIMIT_BY_USER_DECISION = DataSize.ofMegabytes(100);

    @Test
    @DisplayName("the body limit of the idempotency filter leaves room for a 100 MB file (the user's decision), or every large upload would be refused before the file service")
    void theFilterLimitFitsTheFileLimit() throws IOException {
        Properties web = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/config/platform_web.properties")) {
            web.load(in);
        }
        DataSize limit = ApplicationConversionService.getSharedInstance().convert(web.getProperty("forcitis.platform.web.idempotency.max-body-size"), DataSize.class);
        assertThat(limit).isGreaterThanOrEqualTo(FILE_LIMIT_BY_USER_DECISION);
    }
}
