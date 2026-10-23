package com.forcitis.one.platform.file.infrastructure;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * uploadDeadline is the request timeout plus the scan total time, set by operations with nginx and clamd.
 * Off until the malware gate is built and accepted (ADR-0070 migration item 2: no uploads before); once on, every value is required and a
 * missing one stops the start-up. maxFileSize is 100 MB by the user's decision and never above 2 GB, which ClamAV cannot scan.
 */
@ConfigurationProperties("forcitis.platform.file.store")
record FileServiceProperties(@DefaultValue("false") boolean enabled, String environmentPrefix, DataSize maxFileSize, List<String> allowedMediaTypes,
                             Path stagingRoot, Duration uploadDeadline, Duration stagingSweepAge,
                             Duration stagingSweepInterval, Duration downloadLinkValidity, Duration downloadLinkMaximumValidity,
                             Duration unboundExpiryAge, Duration oneTimeLinkUnboundExpiryAge, Duration unboundExpiryInterval) {

    static final String PREFIX = "forcitis.platform.file.store.";
    static final DataSize SCANNABLE = DataSize.ofGigabytes(2);
    static final Duration LINK_CEILING = Duration.ofMinutes(15);
    /** An external draft link is valid for up to 14 days; its staging copy is kept two days longer (ADR-0060 section 5 item 2). */
    static final Duration ONE_TIME_LINK_FLOOR = Duration.ofDays(16);

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
            positive(uploadDeadline, "upload-deadline");
            positive(stagingSweepAge, "staging-sweep-age");
            if (stagingSweepAge.compareTo(uploadDeadline) <= 0) {
                throw new IllegalArgumentException(PREFIX + "staging-sweep-age must exceed upload-deadline (request timeout plus scan total time), "
                        + "or the sweep could remove an upload still being written or scanned (ADR-0070 section 3 item 3)");
            }
            positive(stagingSweepInterval, "staging-sweep-interval");
            positive(downloadLinkValidity, "download-link-validity");
            positive(downloadLinkMaximumValidity, "download-link-maximum-validity");
            if (downloadLinkMaximumValidity.compareTo(LINK_CEILING) > 0 || downloadLinkValidity.compareTo(downloadLinkMaximumValidity) > 0) {
                throw new IllegalArgumentException(PREFIX + "download-link-maximum-validity is at most 15 minutes and download-link-validity at most that "
                        + "(ADR-0060 section 2)");
            }
            positive(unboundExpiryAge, "unbound-expiry-age");
            positive(oneTimeLinkUnboundExpiryAge, "one-time-link-unbound-expiry-age");
            if (oneTimeLinkUnboundExpiryAge.compareTo(ONE_TIME_LINK_FLOOR) < 0) {
                throw new IllegalArgumentException(PREFIX + "one-time-link-unbound-expiry-age is at least 16 days: the link is valid for up to 14 days and "
                        + "a file handed in on the last day must still be linkable for two more (ADR-0060 section 5)");
            }
            positive(unboundExpiryInterval, "unbound-expiry-interval");
        }
    }

    private static void positive(Duration value, String key) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(PREFIX + key + " is required and positive");
        }
    }
}
