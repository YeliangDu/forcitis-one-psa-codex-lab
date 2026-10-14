package com.forcitis.one.platform.integration.infrastructure.archive;

import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyuncs.exceptions.ClientException;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where archives go: an OSS bucket of the archive storage class in a mainland China region (the archives hold personal data and
 * must not leave the country), under WORM compliance retention. Nothing here is defaulted in code except the endpoint kind; a missing
 * value stops the start-up. The bucket and the RAM identity are created by the user and never appear in the repository.
 * Credentials: an AccessKey pair when both halves are given; otherwise the RAM role of the ECS instance when ecsRoleName is given (temporary
 * STS credentials, the recommended way); otherwise the default chain, which reads OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET from the environment.
 */
@ConfigurationProperties("forcitis.platform.integration.archive-store")
record ArchiveStoreProperties(String bucket, String region, String prefix, Path localDirectory, int wormLockDays, int totalKeepYears,
                              @DefaultValue("true") boolean internalEndpoint, String accessKeyId, String accessKeySecret, String ecsRoleName) {
    static final Set<String> MAINLAND_REGIONS = Set.of("cn-qingdao", "cn-beijing", "cn-zhangjiakou", "cn-huhehaote", "cn-wulanchabu",
            "cn-hangzhou", "cn-shanghai", "cn-nanjing", "cn-fuzhou", "cn-shenzhen", "cn-heyuan", "cn-guangzhou", "cn-chengdu");

    ArchiveStoreProperties {
        require(bucket, "bucket");
        require(prefix, "prefix");
        if (region == null || !MAINLAND_REGIONS.contains(region)) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store.region must be a mainland China region " + MAINLAND_REGIONS + ", was " + region);
        }
        if (prefix.startsWith("/") || prefix.endsWith("/")) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store.prefix has no leading or trailing slash");
        }
        if (localDirectory == null || localDirectory.toString().isBlank() || !localDirectory.isAbsolute()) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store.local-directory is required and must be an absolute path");
        }
        if (wormLockDays < 1 || totalKeepYears < 1) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store.worm-lock-days and total-keep-years are required");
        }
        if (blank(accessKeyId) != blank(accessKeySecret)) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store.access-key-id and access-key-secret are given together or not at all");
        }
    }

    String endpoint() {
        return "https://oss-" + region + (internalEndpoint ? "-internal" : "") + ".aliyuncs.com";
    }

    CredentialsProvider credentials() {
        try {
            if (!blank(accessKeyId)) {
                return CredentialsProviderFactory.newDefaultCredentialProvider(accessKeyId, accessKeySecret);
            }
            if (!blank(ecsRoleName)) {
                return CredentialsProviderFactory.newInstanceProfileCredentialsProvider(ecsRoleName);
            }
            return CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
        } catch (ClientException e) {
            throw new IllegalStateException("no credentials for the archive store: give an AccessKey, an ECS RAM role name, or OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET", e);
        }
    }

    @Override
    public String toString() {
        return "ArchiveStoreProperties[bucket=" + bucket + ", region=" + region + ", prefix=" + prefix + ", localDirectory=" + localDirectory
                + ", wormLockDays=" + wormLockDays + ", totalKeepYears=" + totalKeepYears + ", internalEndpoint=" + internalEndpoint
                + ", accessKeyId=" + (blank(accessKeyId) ? "none" : "***") + ", accessKeySecret=" + (blank(accessKeySecret) ? "none" : "***")
                + ", ecsRoleName=" + ecsRoleName + "]";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void require(String value, String key) {
        if (blank(value)) {
            throw new IllegalArgumentException("forcitis.platform.integration.archive-store." + key + " is required");
        }
    }
}
