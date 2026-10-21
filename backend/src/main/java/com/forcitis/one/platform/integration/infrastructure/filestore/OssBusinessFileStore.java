package com.forcitis.one.platform.integration.infrastructure.filestore;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.aliyun.oss.model.StorageClass;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.BusinessFileStore;
import com.forcitis.one.platform.integration.api.StoredObjectKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standard storage, never overwritten (x-oss-forbid-overwrite), every write read back against size, MD5 and SHA-256. */
final class OssBusinessFileStore implements BusinessFileStore, AutoCloseable {
    static final String SHA256 = "sha256";
    private static final String FORBID_OVERWRITE = "x-oss-forbid-overwrite";

    enum Area { STAGING, FILES, QUARANTINE }

    private final OSS writer;
    private final OSS signer;
    private final OSS quarantineReader;
    private final BusinessFileStoreProperties properties;
    private final Clock clock;
    private final Pattern keys;

    OssBusinessFileStore(OSS writer, OSS signer, OSS quarantineReader, BusinessFileStoreProperties properties, Clock clock) {
        this.writer = writer;
        this.signer = signer;
        this.quarantineReader = quarantineReader;
        this.properties = properties;
        this.clock = clock;
        this.keys = Pattern.compile(Pattern.quote(properties.environmentPrefix())
                + "/(?:(staging)/[a-z_]+|(files)/[0-9]{4}/[0-9]{2}|(quarantine))/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
    }

    @Override
    public void put(StoredObjectKey key, Path source, String sha256Hex, long size) {
        Area area = area(key);
        if (area == Area.FILES) {
            throw new IllegalArgumentException("content reaches a formal key by promote only");
        }
        try {
            if (Files.size(source) != size) {
                throw new IllegalArgumentException("the source file does not have the given size");
            }
            String md5 = md5(source);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentMD5(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(md5)));
            metadata.setHeader("x-oss-storage-class", StorageClass.Standard.toString());
            metadata.setHeader(FORBID_OVERWRITE, "true");
            metadata.addUserMetadata(SHA256, sha256Hex);
            try (InputStream in = Files.newInputStream(source)) {
                writer.putObject(new PutObjectRequest(properties.bucket(), key.value(), in, metadata));
            } catch (OSSException exists) {
                if (!"FileAlreadyExists".equals(exists.getErrorCode())) {
                    throw exists;
                }
            }
            verify(key, size, md5, sha256Hex);
        } catch (OSSException | ClientException | IOException failure) {
            throw new ExternalSystemUnavailableException("cannot store the business file object", failure);
        }
    }

    @Override
    public void promote(StoredObjectKey staging, StoredObjectKey formal, String sha256Hex, long size) {
        if (area(staging) != Area.STAGING || area(formal) != Area.FILES || !fileId(staging).equals(fileId(formal))) {
            throw new IllegalArgumentException("promote copies a staging key to the formal key of the same file");
        }
        try {
            ObjectMetadata source = writer.getObjectMetadata(properties.bucket(), staging.value());
            String md5 = etag(source);
            CopyObjectRequest copy = new CopyObjectRequest(properties.bucket(), staging.value(), properties.bucket(), formal.value());
            copy.addHeader(FORBID_OVERWRITE, "true");
            try {
                writer.copyObject(copy);
            } catch (OSSException exists) {
                if (!"FileAlreadyExists".equals(exists.getErrorCode())) {
                    throw exists;
                }
            }
            verify(formal, size, md5, sha256Hex);
        } catch (OSSException | ClientException failure) {
            throw new ExternalSystemUnavailableException("cannot promote the business file object", failure);
        }
    }

    @Override
    public URI presignDownload(StoredObjectKey formal, Duration validity, String fileName, String mediaType) {
        if (area(formal) != Area.FILES) {
            throw new IllegalArgumentException("only formal keys are presigned (ADR-0070 section 7)");
        }
        if (validity.isNegative() || validity.isZero() || validity.compareTo(properties.maxLinkValidity()) > 0) {
            throw new IllegalArgumentException("the link validity must be positive and at most " + properties.maxLinkValidity());
        }
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(properties.bucket(), formal.value(), HttpMethod.GET);
        request.setExpiration(Date.from(clock.instant().plus(validity)));
        ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
        headers.setContentDisposition(attachment(fileName));
        headers.setContentType(mediaType);
        request.setResponseHeaders(headers);
        try {
            return signer.generatePresignedUrl(request).toURI();
        } catch (OSSException | ClientException | URISyntaxException failure) {
            throw new ExternalSystemUnavailableException("cannot sign the download link", failure);
        }
    }

    @Override
    public void read(StoredObjectKey key, OutputStream sink) {
        OSS client = switch (area(key)) {
            case FILES -> writer;
            case QUARANTINE -> quarantineReader;
            case STAGING -> throw new IllegalArgumentException("staging keys are not read");
        };
        try (OSSObject object = client.getObject(properties.bucket(), key.value()); InputStream in = object.getObjectContent()) {
            in.transferTo(sink);
        } catch (OSSException | ClientException | IOException failure) {
            throw new ExternalSystemUnavailableException("cannot read the business file object", failure);
        }
    }

    @Override
    public void close() {
        writer.shutdown();
        signer.shutdown();
        quarantineReader.shutdown();
    }

    Area area(StoredObjectKey key) {
        Matcher m = keys.matcher(key.value());
        if (!m.matches()) {
            throw new IllegalArgumentException("not a business file key of this environment");
        }
        return m.group(1) != null ? Area.STAGING : m.group(2) != null ? Area.FILES : Area.QUARANTINE;
    }

    private String fileId(StoredObjectKey key) {
        Matcher m = keys.matcher(key.value());
        if (!m.matches()) {
            throw new IllegalArgumentException("not a business file key of this environment");
        }
        return m.group(4);
    }

    private void verify(StoredObjectKey key, long size, String md5, String sha256Hex) {
        ObjectMetadata stored = writer.getObjectMetadata(properties.bucket(), key.value());
        if (stored.getContentLength() != size || !etag(stored).equalsIgnoreCase(md5) || !sha256Hex.equals(stored.getUserMetadata().get(SHA256))) {
            throw new ExternalSystemUnavailableException("the business file object read back differs in size, MD5 or SHA-256");
        }
    }

    private static String etag(ObjectMetadata metadata) {
        return metadata.getETag() == null ? "" : metadata.getETag().replace("\"", "");
    }

    static String attachment(String fileName) {
        String ascii = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
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
