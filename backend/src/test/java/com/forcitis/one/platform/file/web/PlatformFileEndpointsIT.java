package com.forcitis.one.platform.file.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.api.StoreRequest;
import com.forcitis.one.platform.file.api.StoreResult;
import com.forcitis.one.platform.file.application.GetUploadStatus;
import com.forcitis.one.platform.file.application.StoreContent;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR-0060 section 7 item 1 through the real filter chain, with the file service itself replaced: an upload is the raw request body, it
 * reaches the service byte for byte even when it is spooled to disk, and it is answered once per Idempotency-Key.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "forcitis.platform.web.idempotency.max-body-size=4MB",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
class PlatformFileEndpointsIT {
    static final String FILES = "/api/v1/platform/files";
    static final String NAME = "attachment; filename*=UTF-8''%E6%8A%A5%E5%91%8A%201.pdf";

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @MockitoBean
    StoreContent storeContent;

    @MockitoBean
    GetUploadStatus getUploadStatus;

    static final TestDatabase postgres = TestPostgres.database(PlatformFileEndpointsIT.class, TestPostgres.MIGRATED_TESTDATA).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    final List<StoreRequest> requests = new ArrayList<>();
    final List<String> received = new ArrayList<>();

    @BeforeEach
    void theServiceHashesWhatItReceives() {
        requests.clear();
        received.clear();
        when(storeContent.store(any())).thenAnswer(call -> {
            StoreRequest request = call.getArgument(0);
            requests.add(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            byte[] chunk = new byte[8192];
            int n;
            while ((n = request.content().read(chunk)) != -1) {
                digest.update(chunk, 0, n);
                size += n;
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            received.add(hash);
            return new StoreResult(new FileId(UUID.randomUUID()), new ContentHash(hash), size, "application/pdf", FileStatus.AVAILABLE, false);
        });
    }

    private static byte[] bytes(int length, long seed) {
        byte[] content = new byte[length];
        new Random(seed).nextBytes(content);
        return content;
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor member(String... permissions) {
        return user(TestAccounts.account(1).value().toString())
                .authorities(java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }

    private MockHttpServletRequestBuilder upload(String key, byte[] body) {
        return post(FILES).with(member(Permission.Codes.PLATFORM_FILE_FILE_CREATE)).with(csrf()).header("Idempotency-Key", key)
                .header("Content-Disposition", NAME).contentType("application/pdf").content(body);
    }

    @Test
    @DisplayName("a 3 MB upload (spooled to disk by the filter) reaches the service byte for byte with its name, media type and uploader; 201 with the Location of its status")
    void aLargeUploadArrivesUnchanged() throws Exception {
        byte[] body = bytes(3 * 1024 * 1024, 1);

        MvcResult answer = mvc.perform(upload("k-large", body)).andReturn();

        assertThat(answer.getResponse().getStatus()).isEqualTo(201);
        assertThat(received).containsExactly(sha256(body));
        StoreRequest request = requests.getFirst();
        assertThat(request.originalName()).isEqualTo("报告 1.pdf");
        assertThat(request.declaredMediaType()).isEqualTo("application/pdf");
        assertThat(request.uploader().kind()).isEqualTo(FileSubjectKind.USER);
        assertThat(request.uploader().id()).isEqualTo(TestAccounts.account(1).value().toString());
        assertThat(request.lineage()).isInstanceOf(Lineage.FirstVersion.class);
        var response = json.readTree(answer.getResponse().getContentAsString());
        assertThat(answer.getResponse().getHeader("Location")).isEqualTo(FILES + "/" + response.path("fileId").asString());
        assertThat(response.path("contentHash").asString()).isEqualTo(sha256(body));
        assertThat(response.path("byteCount").asLong()).isEqualTo(body.length);
        assertThat(response.has("originalName")).as("a file name can hold a person's name and is kept 24 hours under the key").isFalse();
    }

    @Test
    @DisplayName("the same key and the same file again runs the service once and replays the answer; the same key with another file is 422")
    void oneAnswerPerKey() throws Exception {
        byte[] body = bytes(200 * 1024, 2);
        MvcResult first = mvc.perform(upload("k-once", body)).andReturn();
        MvcResult again = mvc.perform(upload("k-once", body)).andReturn();
        MvcResult other = mvc.perform(upload("k-once", bytes(200 * 1024, 3))).andReturn();
        MvcResult otherName = mvc.perform(post(FILES).with(member(Permission.Codes.PLATFORM_FILE_FILE_CREATE)).with(csrf()).header("Idempotency-Key", "k-once")
                .header("Content-Disposition", "attachment; filename=\"other.pdf\"").contentType("application/pdf").content(body)).andReturn();
        MvcResult otherType = mvc.perform(post(FILES).with(member(Permission.Codes.PLATFORM_FILE_FILE_CREATE)).with(csrf()).header("Idempotency-Key", "k-once")
                .header("Content-Disposition", NAME).contentType("image/png").content(body)).andReturn();

        assertThat(requests).hasSize(1);
        assertThat(again.getResponse().getStatus()).isEqualTo(201);
        assertThat(again.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(again.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(again.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(other.getResponse().getStatus()).isEqualTo(422);
        assertThat(otherName.getResponse().getStatus()).isEqualTo(422);
        assertThat(otherType.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("a body above the idempotency limit is refused with 413 before the service sees it; one just within it goes through")
    void aBodyAboveTheLimitIsRefused() throws Exception {
        MvcResult tooLarge = mvc.perform(upload("k-big", bytes(4 * 1024 * 1024 + 1, 4))).andReturn();
        MvcResult atLimit = mvc.perform(upload("k-edge", bytes(4 * 1024 * 1024, 5))).andReturn();

        assertThat(tooLarge.getResponse().getStatus()).isEqualTo(413);
        assertThat(tooLarge.getResponse().getContentAsString()).contains("PLATFORM_WEB_REQUEST_TOO_LARGE");
        assertThat(atLimit.getResponse().getStatus()).isEqualTo(201);
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("without the permission, without a file name or with an unreadable one: 403 or 400, and the service is never called")
    void refusedBeforeTheService() throws Exception {
        byte[] body = bytes(1024, 6);
        assertThat(mvc.perform(post(FILES).with(member(Permission.Codes.PLATFORM_FILE_FILE_READ)).with(csrf()).header("Idempotency-Key", "k-403")
                .header("Content-Disposition", NAME).contentType("application/pdf").content(body)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(post(FILES).with(member(Permission.Codes.PLATFORM_FILE_FILE_CREATE)).with(csrf()).header("Idempotency-Key", "k-noname")
                .contentType("application/pdf").content(body)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(post(FILES).with(member(Permission.Codes.PLATFORM_FILE_FILE_CREATE)).with(csrf()).header("Idempotency-Key", "k-inline")
                .header("Content-Disposition", "inline").contentType("application/pdf").content(body)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("the status of an upload is read by its uploader; the service decides who may know a file, and a refusal is a 404")
    void statusOfAnUpload() throws Exception {
        UUID id = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(getUploadStatus.execute(new FileId(id), TestAccounts.account(1).value())).thenReturn(FileStatus.QUARANTINED);
        when(getUploadStatus.execute(new FileId(other), TestAccounts.account(1).value())).thenThrow(FileError.PLATFORM_FILE_NOT_FOUND.refusal("not yours"));

        MvcResult own = mvc.perform(get(FILES + "/" + id).with(member(Permission.Codes.PLATFORM_FILE_FILE_READ))).andReturn();
        MvcResult foreign = mvc.perform(get(FILES + "/" + other).with(member(Permission.Codes.PLATFORM_FILE_FILE_READ))).andReturn();
        MvcResult forbidden = mvc.perform(get(FILES + "/" + id).with(member(Permission.Codes.PLATFORM_FILE_FILE_CREATE))).andReturn();

        assertThat(own.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(own.getResponse().getContentAsString()).path("status").asString()).isEqualTo("QUARANTINED");
        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
    }
}
