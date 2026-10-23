package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Ground of the R-1b P4 acceptance tests that start timesheet approvals through the approval api (as the timesheet module does) and read
 * the approval schema: an initiator, a supervisor, two project managers and an org unit head, each with an account; helpers for the
 * document id (a UUID in the final shape), plain SQL on platform_approval, and the task endpoints.
 */
public abstract class ApprovalP4Support extends AccountsAcceptanceSupport {

    protected static final String TASKS = "/api/v1/platform/approval-tasks/";

    /**
     * Makes ids that fall: every id is a version 7 UUID of an earlier millisecond than the one before, so the order of the ids is the
     * reverse of the order they were made in. Remembers who asked for each (class names on the stack) to show where an id came from.
     */
    public static final class DescendingIds implements IdGenerator {

        public record Made(UUID id, List<String> callers) {
        }

        private final List<Made> made = new ArrayList<>();
        private long count;

        @Override
        public synchronized UUID next() {
            count++;
            long millis = NOW.toEpochMilli() - count;
            UUID id = new UUID((millis << 16) | 0x7000L, 0x8000000000000000L | count);
            List<String> callers = StackWalker.getInstance().walk(frames -> frames.map(frame -> frame.getClassName()).toList());
            made.add(new Made(id, callers));
            return id;
        }

        public synchronized List<Made> made() {
            return List.copyOf(made);
        }

        public synchronized Made madeAs(UUID id) {
            return made.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
        }
    }

    @Autowired
    protected ApprovalProcesses approvals;

    protected LegalEntityId legalEntity;
    protected UUID unit;
    protected UUID supervisor;
    protected UUID initiator;
    protected UUID projectManager;
    protected UUID secondProjectManager;
    protected UUID orgUnitHead;
    protected SignedIn initiatorAccount;
    protected SignedIn projectManagerAccount;
    protected SignedIn secondProjectManagerAccount;
    protected SignedIn orgUnitHeadAccount;

    @BeforeEach
    void peopleWithAccounts() throws Exception {
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID maintainer = newEmployeeId();
        employee(maintainer, unit, null);
        grantByOperations(maintainer, "org_data_admin");
        SignedIn maintainerSession = signIn(maintainer);
        maintainWorkCalendar(maintainerSession);
        legalEntity = new LegalEntityId(createLegalEntity(maintainerSession, newLegalEntityCode("PQ"), "Synthetic approval ids"));
        supervisor = newEmployeeId();
        initiator = newEmployeeId();
        projectManager = newEmployeeId();
        secondProjectManager = newEmployeeId();
        orgUnitHead = newEmployeeId();
        employee(supervisor, unit, null);
        employee(initiator, unit, supervisor);
        employee(projectManager, unit, null);
        employee(secondProjectManager, unit, null);
        employee(orgUnitHead, unit, null);
        signIn(supervisor);
        initiatorAccount = signIn(initiator);
        projectManagerAccount = signIn(projectManager);
        secondProjectManagerAccount = signIn(secondProjectManager);
        orgUnitHeadAccount = signIn(orgUnitHead);
    }

    /** The document id of the final shape (a UUID); before P4 DocumentId wraps text and gets the text of the same UUID. */
    protected static DocumentId documentIdOf(UUID id) {
        try {
            Class<?> type = DocumentId.class.getRecordComponents()[0].getType();
            return DocumentId.class.getDeclaredConstructor(type).newInstance(type == UUID.class ? id : id.toString());
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    protected static DocumentReference newDocument() {
        return new DocumentReference(new DocumentTypeCode("TIMESHEET"), documentIdOf(UUID.randomUUID()));
    }

    protected static UUID uuidOf(DocumentReference document) {
        return UUID.fromString(document.documentId().toString());
    }

    protected ApprovalStartRequest startRequest(DocumentReference document, Set<String> flags, ApprovalField... fields) {
        return new ApprovalStartRequest(TimesheetApprovalProcess.KEY, document, legalEntity, employeeIdOf(initiator), new RoundNumber(1),
                UUID.randomUUID(), flags, List.of(fields), new ContentHash("synthetic-content"), new ApprovalSummary("Synthetic timesheet " + document.documentId()));
    }

    protected void start(DocumentReference document, Set<String> flags, ApprovalField... fields) {
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "acceptance.timesheet-submission")) {
            approvals.start(startRequest(document, flags, fields));
        }
    }

    protected static ApprovalField projectManagers(UUID... people) {
        return new ApprovalField(P4Names.PROJECT_MANAGER_FIELD, List.of(people).stream().map(AccountsAcceptanceSupport::text).toList());
    }

    protected DocumentReference startProjectHours(UUID... managers) {
        DocumentReference document = newDocument();
        start(document, Set.of(P4Names.PROJECT_HOURS_FLAG), projectManagers(managers));
        return document;
    }

    /** Rows of one query on platform_approval as column name to value; a uuid column comes back as UUID. */
    protected List<Map<String, Object>> rows(String sql, Object... parameters) throws SQLException {
        return query(P4Names.APPROVAL_SCHEMA, sql, parameters);
    }

    protected List<Map<String, Object>> query(String schema, String sql, Object... parameters) throws SQLException {
        try (Connection connection = schemaConnection(schema); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= meta.getColumnCount(); column++) {
                        row.put(meta.getColumnLabel(column), rs.getObject(column));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    protected void execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = schemaConnection(P4Names.APPROVAL_SCHEMA); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    protected UUID requestIdOf(DocumentReference document) throws SQLException {
        List<Map<String, Object>> found = rows("select id from approval_requests where " + P4Names.DOCUMENT_ID + " = ?", uuidOf(document));
        assertThat(found).as("the approval request of %s", document).hasSize(1);
        return (UUID) found.getFirst().get("id");
    }

    protected List<UUID> openTaskIds(DocumentReference document) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        for (Map<String, Object> row : rows("select t.task_id from approval_tasks t join approval_requests r on r.id = t." + P4Names.APPROVAL_REQUEST_ID
                + " where r." + P4Names.DOCUMENT_ID + " = ? and t.status in ('PENDING', 'CLAIMED') order by t.task_id", uuidOf(document))) {
            ids.add((UUID) row.get("task_id"));
        }
        return ids;
    }

    protected MockHttpServletResponse detail(Object taskId, SignedIn caller) throws Exception {
        return mvc.perform(get(TASKS + taskId).with(session(caller))).andReturn().getResponse();
    }

    protected String versionOf(Object taskId, SignedIn caller) throws Exception {
        MockHttpServletResponse seen = detail(taskId, caller);
        assertThat(seen.getStatus()).as(seen.getContentAsString()).isEqualTo(200);
        return seen.getHeader("ETag");
    }

    /** A write on the task with the version the caller just read. */
    protected MockHttpServletResponse act(Object taskId, SignedIn caller, String operation, String body) throws Exception {
        var request = post(TASKS + taskId + ":" + operation).header("If-Match", versionOf(taskId, caller)).with(csrf()).with(session(caller));
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    protected void actOk(Object taskId, SignedIn caller, String operation, String body) throws Exception {
        MockHttpServletResponse done = act(taskId, caller, operation, body);
        assertThat(done.getStatus()).as("%s: %s", operation, done.getContentAsString()).isIn(200, 204);
    }

    protected List<String> historyActions(Object taskId, SignedIn caller) throws Exception {
        MockHttpServletResponse seen = detail(taskId, caller);
        assertThat(seen.getStatus()).as(seen.getContentAsString()).isEqualTo(200);
        return JsonPath.read(seen.getContentAsString(), "$.history[*].action");
    }

    protected List<UUID> historyRecordIds(Object taskId, SignedIn caller) throws Exception {
        MockHttpServletResponse seen = detail(taskId, caller);
        List<String> ids = JsonPath.read(seen.getContentAsString(), "$.history[*].recordId");
        return ids.stream().map(UUID::fromString).toList();
    }

    protected static Instant millisOf(Instant instant) {
        return Instant.ofEpochMilli(instant.toEpochMilli());
    }
}
