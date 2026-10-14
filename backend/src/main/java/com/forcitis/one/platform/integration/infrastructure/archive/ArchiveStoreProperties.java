package com.forcitis.one.platform.integration.infrastructure.archive;

import java.nio.file.Path;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where archives go: an OSS bucket of the archive storage class in a mainland China region (the archives hold personal data and
 * must not leave the country), under WORM compliance retention. Nothing here has a default except the endpoint kind; a missing value
 * stops the start-up. The bucket, the RAM sub-account and its keys are created by the user and never appear in the repository.
 */
@ConfigurationProperties("forcitis.platform.integration.archive-store")
record ArchiveStoreProperties(String bucket, String region, String prefix, Path localDirectory, int wormLockDays,
                              @DefaultValue("true") boolean internalEndpoint, String accessKeyId, String accessKeySecret) {
    static final Set<String> MAINLAND_REGIONS = Set.of("cn-qingdao", "cn-beijing", "cn-zhangjiakou", "cn-huhehaote", "cn-wulanchabu",
            "cn-hangzhou", "cn-shanghai", "cn-nanjing", "cn-fuzhou", "cn-shenzhen", "cn-heyuan", "cn-guangzhou", "cn-chengdu");

    ArchiveStoreProperties {
        require(bucket, "bucket");
        require(prefix, "prefix");
        require(accessKeyId, "access-key-id");
        require(accessKeySecret, "access-key-secret");
        if (region == null || !MAINLAND_REGIONS.contains(region)) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store.region must be a mainland China region " + MAINLAND_REGIONS + ", was " + region);
        }
        if (prefix.startsWith("/") || prefix.endsWith("/")) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store.prefix has no leading or trailing slash");
        }
        if (localDirectory == null || localDirectory.toString().isBlank() || !localDirectory.isAbsolute()) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store.local-directory is required and must be an absolute path");
        }
        if (wormLockDays < 1) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store.worm-lock-days is required: the WORM period the bucket must have");
        }
    }

    String endpoint() {
        return "https://oss-" + region + (internalEndpoint ? "-internal" : "") + ".aliyuncs.com";
    }

    private static void require(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store." + key + " is required");
        }
    }
}
