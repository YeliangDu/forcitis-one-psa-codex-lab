package com.forcitis.one.platform.file.infrastructure;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Off until the malware gate is built and accepted (ADR-0070 migration item 2: no uploads before); once on, every value is required and a
 * missing one stops the start-up. maxFileSize is 100 MB by the user's decision and never above 2 GB, which ClamAV cannot scan.
 */
@ConfigurationProperties("forcitis.platform.file")
record FileServiceProperties(@DefaultValue("false") boolean enabled, String environmentPrefix, DataSize maxFileSize, List<String> allowedMediaTypes,
                             Path stagingRoot, Duration stagingSweepAge, Duration stagingSweepInterval) {

    static final String PREFIX = "forcitis.platform.file.";
    static final DataSize SCANNABLE = DataSize.ofGigabytes(2);

    FileServiceProperties {
        if (enabled) {
            if (environmentPrefix == null || !environmentPrefix.matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException(PREFIX + "environment-prefix is required: lower-case letters, digits and hyphens");
            }
            if (maxFileSize == null || maxFileSize.toBytes() < 1 || maxFileSize.compareTo(SCANNABLE) > 0) {
                throw new IllegalArgumentException(PREFIX + "max-file-size is required and at most 2GB");
            }
            if (allowedMediaTypes == null || allowedMediaTypes.isEmpty()) {
                throw new IllegalArgumentException(PREFIX + "allowed-media-types is required");
            }
            if (stagingRoot == null || !stagingRoot.isAbsolute()) {
                throw new IllegalArgumentException(PREFIX + "staging-root is required and must be absolute");
            }
            positive(stagingSweepAge, "staging-sweep-age");
            positive(stagingSweepInterval, "staging-sweep-interval");
        }
    }

    private static void positive(Duration value, String key) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(PREFIX + key + " is required and positive");
        }
    }
}
