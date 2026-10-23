package com.forcitis.one.platform.file.web;

import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.api.StoreRequest;
import com.forcitis.one.platform.file.application.GetUploadStatus;
import com.forcitis.one.platform.file.application.StoreContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The upload is the raw body of the request, not a multipart form: the Idempotency-Key filter reads the body once and gives it to the
 * handler again, which a multipart parse cannot do; the name travels in Content-Disposition and the declared media type is the
 * Content-Type. While the file service is switched off (no malware gate yet) every call answers "unavailable"; nothing is accepted unscanned.
 */
@RestController
@RequestMapping("/api/v1/platform/files")
class PlatformFileController {
    static final int MAX_NAME_LENGTH = 255;

    private final ObjectProvider<StoreContent> store;
    private final ObjectProvider<GetUploadStatus> status;

    PlatformFileController(ObjectProvider<StoreContent> store, ObjectProvider<GetUploadStatus> status) {
        this.store = store;
        this.status = status;
    }

    @PostMapping
    @PreAuthorize(FilePermissions.CREATE)
    @Operation(summary = "Stores an upload behind the malware scan; the body is the file, its name is in Content-Disposition (filename*=UTF-8''...)",
            requestBody = @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary"))))
    ResponseEntity<StoredFileResponse> store(HttpServletRequest request,
            @RequestHeader(HttpHeaders.CONTENT_DISPOSITION) String disposition, UserId caller) throws IOException {
        StoreContent storeContent = store.getIfAvailable();
        if (storeContent == null) {
            throw FileError.PLATFORM_FILE_SCAN_UNAVAILABLE.refusal("file upload is not switched on");
        }
        String name = originalName(disposition);
        String mediaType = declaredMediaType(request.getContentType());
        try (InputStream content = request.getInputStream()) {
            StoredFileResponse stored = StoredFileResponse.of(storeContent.store(new StoreRequest(content, name, mediaType,
                    new FileSubject(FileSubjectKind.USER, caller.value().toString()), new Lineage.FirstVersion())));
            return ResponseEntity.created(URI.create("/api/v1/platform/files/" + stored.fileId())).body(stored);
        }
    }

    @GetMapping("/{fileId}")
    @PreAuthorize(FilePermissions.READ)
    FileStatusResponse status(@PathVariable UUID fileId, UserId caller) {
        GetUploadStatus getStatus = status.getIfAvailable();
        if (getStatus == null) {
            throw FileError.PLATFORM_FILE_NOT_FOUND.refusal("file service is not switched on");
        }
        FileId id = new FileId(fileId);
        return new FileStatusResponse(fileId, getStatus.execute(id, caller.value()));
    }

    /** The last path segment only: a client may send a full path, and the name is used again when the file is downloaded. */
    static String originalName(String disposition) {
        String filename;
        try {
            filename = ContentDisposition.parse(disposition).getFilename();
        } catch (IllegalArgumentException malformed) {
            throw new InvalidRequestException("Content-Disposition cannot be read; send filename*=UTF-8''<percent-encoded name>");
        }
        if (filename == null) {
            throw new InvalidRequestException("Content-Disposition needs the file name (filename*=UTF-8''...)");
        }
        String name = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1).strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(c -> Character.isISOControl(c) || changesDirection(c))) {
            throw new InvalidRequestException("The file name must be 1 to " + MAX_NAME_LENGTH + " characters without control or direction-changing characters");
        }
        return name;
    }

    /** Bidirectional marks and overrides (U+202E would show "gpj.fdp" as "pdf.jpg"); joiners such as U+200D stay, real names need them. */
    private static boolean changesDirection(int c) {
        return c == 0x061C || c == 0x200E || c == 0x200F || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069);
    }

    /** Type and subtype only; the registered type is judged from the content, this is a hint. */
    static String declaredMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            MediaType parsed = MediaType.parseMediaType(contentType);
            if (parsed.getType().equals("multipart") || parsed.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)) {
                throw new InvalidRequestException("The body is the file itself, not a form; send it with the file's own media type");
            }
            return parsed.getType() + "/" + parsed.getSubtype();
        } catch (IllegalArgumentException malformed) {
            throw new InvalidRequestException("Content-Type cannot be read");
        }
    }
}
