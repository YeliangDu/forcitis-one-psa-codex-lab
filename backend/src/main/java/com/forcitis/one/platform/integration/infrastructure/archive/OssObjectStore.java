package com.forcitis.one.platform.integration.infrastructure.archive;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.GetBucketWormResult;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.StorageClass;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.integration.api.ArchivedObject;
import com.forcitis.one.platform.integration.domain.ObjectStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * One archive object is written once: archive storage class, no overwrite, Content-MD5 checked by OSS and the SHA-256 kept as user
 * metadata, then read back from the object's metadata. The probe starts and aborts a multipart upload, so it needs write permission
 * and leaves nothing behind (a WORM bucket cannot delete what was written), and checks that the bucket is in the configured region and
 * under a locked WORM policy of exactly the configured number of days.
 */
final class OssObjectStore implements ObjectStore {
    static final String SHA256 = "sha256";

    private final OSS client;
    private final ArchiveStoreProperties properties;
    private final Clock clock;

    OssObjectStore(OSS client, ArchiveStoreProperties properties, Clock clock) {
        this.clock = clock;
        this.client = client;
        this.properties = properties;
    }

    @Override
    public ArchivedObject put(String key, Path file, String sha256) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(Files.size(file));
            String md5 = md5(file);
            metadata.setContentMD5(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(md5)));
            metadata.setHeader("x-oss-storage-class", StorageClass.Archive.toString());
            metadata.setHeader("x-oss-forbid-overwrite", "true");
            metadata.addUserMetadata(SHA256, sha256);
            try (InputStream in = Files.newInputStream(file)) {
                client.putObject(new PutObjectRequest(properties.bucket(), key, in, metadata));
            } catch (OSSException exists) {
                if (!"FileAlreadyExists".equals(exists.getErrorCode())) {
                    throw exists;
                }
            }
            ObjectMetadata stored = client.getObjectMetadata(properties.bucket(), key);
            if (stored.getObjectStorageClass() != StorageClass.Archive) {
                throw new ExternalSystemUnavailableException("object " + key + " is stored as " + stored.getObjectStorageClass() + ", not Archive");
            }
            String etag = stored.getETag() == null ? "" : stored.getETag().replace("\"", "");
            if (!etag.equalsIgnoreCase(md5)) {
                throw new ExternalSystemUnavailableException("object " + key + " has ETag " + etag + ", the local MD5 is " + md5);
            }
            return new ArchivedObject(key, stored.getContentLength(), stored.getUserMetadata().get(SHA256));
        } catch (OSSException | ClientException | IOException failure) {
            throw new ExternalSystemUnavailableException("cannot store the archive object " + key, failure);
        }
    }

    @Override
    public void probe() {
        try {
            String location = client.getBucketLocation(properties.bucket());
            if (!("oss-" + properties.region()).equals(location)) {
                throw new ExternalSystemUnavailableException("the archive bucket is in " + location + ", not oss-" + properties.region());
            }
            GetBucketWormResult worm = client.getBucketWorm(properties.bucket());
            if (!"Locked".equals(worm.getWormState()) || worm.getRetentionPeriodInDays() != properties.wormLockDays()) {
                throw new ExternalSystemUnavailableException("the archive bucket has WORM " + worm.getWormState() + " for " + worm.getRetentionPeriodInDays()
                        + " days; Locked and exactly " + properties.wormLockDays() + " days are required: change worm-lock-days and total-keep-years together with the bucket");
            }
            String probeKey = properties.prefix() + "/probe/" + Uuids.timeOrdered(clock);
            String uploadId = client.initiateMultipartUpload(new InitiateMultipartUploadRequest(properties.bucket(), probeKey)).getUploadId();
            client.abortMultipartUpload(new AbortMultipartUploadRequest(properties.bucket(), probeKey, uploadId));
        } catch (OSSException | ClientException failure) {
            throw new ExternalSystemUnavailableException("the archive bucket cannot be used", failure);
        }
    }

    private static String md5(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
