package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.ApprovalP4Support;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #730 S4, acceptance A5 (issue #730 comment 6597359528): every {@code SELF} line of backend/architecture/data-scope-read-paths.txt answers with the
 * caller's own data only. Two project managers each own a task, a handling record, a file upload and their login rows; whatever one of them reads
 * never carries the other's, and the other's id is a 404 with the body of an id that does not exist. A SELF line without an adapter here fails
 * {@link #everySelfLineHasAnAdapter()}, as does an adapter whose line left the register.
 */
class SelfReadBehaviourIT extends ApprovalP4Support {

    static final TestDatabase postgres = TestPostgres.database(SelfReadBehaviourIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final Path REGISTER = Path.of("architecture/data-scope-read-paths.txt");
    static final String INBOX = "/api/v1/platform/approval-tasks";
    static final String RECORDS = "/api/v1/platform/approval-task-records";
    static final String OWN_LOGINS = "/api/v1/platform/own-login-records";
    static final String CURRENT = "/api/v1/platform/sessions:current";

    private static final Set<String> ADAPTED = Set.of("GET /api/v1/platform/approval-task-records", "GET /api/v1/platform/approval-tasks",
            "GET /api/v1/platform/approval-tasks/{taskId}", "GET /api/v1/platform/own-login-records", "GET /api/v1/platform/sessions:current");

    /**
     * The file service is switched off in every test context (it starts only with a malware scanner and the business bucket, ADR-0060 section 2), so an
     * upload cannot be made over HTTP here; its read path answers a file of someone else as not found by this unit test of the use case.
     */
    private static final Map<String, Path> PROVEN_ELSEWHERE = Map.of("GET /api/v1/platform/files/{fileId}",
            Path.of("src/test/java/com/forcitis/one/platform/file/application/GetUploadStatusTest.java"),
            "GET /api/v1/organization/bonus-input-approvals/{taskId}",
            Path.of("src/test/java/com/forcitis/one/acceptance/organization/payroll/BonusInputApproverReadIT.java"));

    @Test
    @DisplayName("#730 A5: every SELF line of the register has an adapter here, and every adapter is a SELF line")
    void everySelfLineHasAnAdapter() throws IOException {
        Set<String> lines = new TreeSet<>();
        for (String line : Files.readAllLines(REGISTER)) {
            String[] parts = line.split("\\|");
            if (line.startsWith("GET ") && parts[1].trim().equals("SELF")) {
                lines.add(parts[0].trim());
            }
        }
        Set<String> covered = new TreeSet<>(ADAPTED);
        covered.addAll(PROVEN_ELSEWHERE.keySet());
        assertThat(covered).containsExactlyInAnyOrderElementsOf(lines);
        PROVEN_ELSEWHERE.forEach((line, proof) -> assertThat(proof).as("the proof of " + line).exists());
    }

    // ---- calls --------------------------------------------------------------------------------------------------------------------

    private MockHttpServletResponse read(SignedIn caller, String url) throws Exception {
        return mvc.perform(get(url).with(session(caller))).andReturn().getResponse();
    }

    private List<String> taskIdsOf(SignedIn caller) throws Exception {
        MockHttpServletResponse inbox = read(caller, INBOX);
        require(inbox, 200, "the inbox");
        return JsonPath.read(inbox.getContentAsString(), "$.items[*].taskId");
    }

    private static void require(MockHttpServletResponse answer, int status, String what) {
        assertThat(answer.getStatus()).as(what + ": " + contentOf(answer)).isEqualTo(status);
    }

    private static String contentOf(MockHttpServletResponse answer) {
        try {
            return answer.getContentAsString();
        } catch (java.io.UnsupportedEncodingException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    /** The body of a 404 without the id the request named and the per-request correlation id, so a row of someone else cannot be told from none. */
    private static String refusalBody(MockHttpServletResponse answer, String id) {
        Map<String, Object> body = new LinkedHashMap<>(JsonPath.parse(contentOf(answer)).<Map<String, Object>>read("$"));
        body.remove("correlationId");
        return body.toString().replace(id, "<id>");
    }

    private void assertNotFoundLikeAbsent(SignedIn caller, String urlBefore, String id, String urlAfter, String what) throws Exception {
        String absent = UUID.randomUUID().toString();
        MockHttpServletResponse nothing = read(caller, urlBefore + absent + urlAfter);
        MockHttpServletResponse refused = read(caller, urlBefore + id + urlAfter);
        require(nothing, 404, "an id that does not exist");
        require(refused, 404, what);
        assertThat(refusalBody(refused, id)).as(what + " answers like an id that does not exist").isEqualTo(refusalBody(nothing, absent));
    }

    /** A task for each project manager, and each one claims their own so that a handling record exists for each. */
    private Map<SignedIn, String> oneTaskEach() throws Exception {
        startProjectHours(projectManager);
        startProjectHours(secondProjectManager);
        Map<SignedIn, String> tasks = new LinkedHashMap<>();
        for (SignedIn manager : List.of(projectManagerAccount, secondProjectManagerAccount)) {
            List<String> own = taskIdsOf(manager);
            assertThat(own).as("the seed: one open task for each manager").hasSize(1);
            tasks.put(manager, own.getFirst());
        }
        return tasks;
    }

    private void claim(SignedIn caller, String task) throws Exception {
        MockHttpServletResponse seen = read(caller, TASKS + task);
        require(seen, 200, "reading the own task");
        MockHttpServletResponse claimed = mvc.perform(post(TASKS + task + ":claim").header("If-Match", seen.getHeader("ETag")).with(csrf()).with(session(caller))).andReturn().getResponse();
        require(claimed, 204, "claiming the own task");
    }

    // ---- A5 -----------------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("#730 A5: the inbox and one task of it are the caller's own; the task of a second account is a 404 like an id that does not exist")
    void theInboxIsTheCallersOwn() throws Exception {
        Map<SignedIn, String> tasks = oneTaskEach();
        String first = tasks.get(projectManagerAccount);
        String second = tasks.get(secondProjectManagerAccount);

        assertThat(taskIdsOf(projectManagerAccount)).containsExactly(first);
        assertThat(taskIdsOf(secondProjectManagerAccount)).containsExactly(second);
        require(read(projectManagerAccount, TASKS + first), 200, "the own task");
        assertNotFoundLikeAbsent(projectManagerAccount, TASKS, second, "", "the task of the second account");
        assertNotFoundLikeAbsent(secondProjectManagerAccount, TASKS, first, "", "the task of the first account");
    }

    @Test
    @DisplayName("#730 A5: the handling records are the caller's own; the other account's task never appears in them")
    void theRecordsAreTheCallersOwn() throws Exception {
        Map<SignedIn, String> tasks = oneTaskEach();
        String first = tasks.get(projectManagerAccount);
        String second = tasks.get(secondProjectManagerAccount);
        claim(projectManagerAccount, first);
        claim(secondProjectManagerAccount, second);

        String firstRecords = contentOf(read(projectManagerAccount, RECORDS));
        String secondRecords = contentOf(read(secondProjectManagerAccount, RECORDS));

        assertThat(firstRecords).as("the records of the first account").contains(first).doesNotContain(second);
        assertThat(secondRecords).as("the records of the second account").contains(second).doesNotContain(first);
    }

    @Test
    @DisplayName("#730 A5: the own login records count the caller's logins only; a second account's logins change nothing in them")
    void theLoginRecordsAreTheCallersOwn() throws Exception {
        int first = JsonPath.<List<Object>>read(contentOf(read(projectManagerAccount, OWN_LOGINS)), "$.items").size();
        int second = JsonPath.<List<Object>>read(contentOf(read(secondProjectManagerAccount, OWN_LOGINS)), "$.items").size();
        assertThat(first).as("the seed: the first account has logged in").isPositive();

        signIn(secondProjectManager);
        signIn(secondProjectManager);

        assertThat(JsonPath.<List<Object>>read(contentOf(read(projectManagerAccount, OWN_LOGINS)), "$.items")).as("the first account's records").hasSize(first);
        assertThat(JsonPath.<List<Object>>read(contentOf(read(secondProjectManagerAccount, OWN_LOGINS)), "$.items")).as("the second account's records").hasSize(second + 2);
    }

    @Test
    @DisplayName("#730 A5: the current session answers with the caller's own account, whoever else is signed in")
    void theCurrentSessionIsTheCallersOwn() throws Exception {
        for (SignedIn caller : List.of(projectManagerAccount, secondProjectManagerAccount)) {
            MockHttpServletResponse current = read(caller, CURRENT);
            require(current, 200, "the current session");
            assertThat(JsonPath.<Object>read(contentOf(current), "$.userId").toString()).isEqualTo(caller.userId().toString());
        }
    }
}
