package com.forcitis.one.acceptance.organization.administration;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.postgresql.util.PSQLException;

/**
 * Black-box probe of the organization_administration schema on the migrated database (the application's own migrations, no Spring context).
 * One transaction that is never committed: setup rows stay for the rest of the test, every probe statement runs under a savepoint that is rolled
 * back, so a refused statement does not poison the next. Ids come from a counter and times from fixed constants, so a test reads the same every run;
 * the only live value is the database's own transaction day, which the confirmation rule itself reads (asked with {@link #today()}).
 */
final class AdministrationDb implements AutoCloseable {

    static final String SCHEMA = "organization_administration.";
    static final String ACTIVITY_TYPES = "EMPLOYEE_ACTIVITY_TYPES";
    static final String SEAL_APPROVERS = "SEAL_APPROVERS";
    static final String OWNER_ROLE = "probe_owner_role";

    static final UUID AUTHOR = UUID.fromString("01900000-0000-7000-8000-00000000a001");
    static final UUID CONFIRMER = UUID.fromString("01900000-0000-7000-8000-00000000a002");
    static final UUID WITHDRAWER = UUID.fromString("01900000-0000-7000-8000-00000000a003");
    static final UUID DECISION = UUID.fromString("01900000-0000-7000-8000-00000000d001");
    static final UUID CORRELATION = UUID.fromString("01900000-0000-7000-8000-00000000c001");

    static final Timestamp T0 = Timestamp.from(Instant.parse("2026-11-02T02:00:00Z"));
    static final Timestamp T1 = Timestamp.from(Instant.parse("2026-11-02T03:00:00Z"));
    static final Timestamp T2 = Timestamp.from(Instant.parse("2026-11-02T04:00:00Z"));

    static final String CHECK_VIOLATION = "23514";
    static final String RESTRICT_VIOLATION = "23001";
    static final String UNIQUE_VIOLATION = "23505";
    static final String FOREIGN_KEY_VIOLATION = "23503";
    static final String EXCLUSION_VIOLATION = "23P01";

    static final String CONFIRM = "update " + SCHEMA + "config_set_versions set confirmation_status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?,"
            + " decision_audit_entry_id = ?, updated_at = ?, version = version + 1 where config_set_version_id = ?";
    static final String WITHDRAW = "update " + SCHEMA + "config_set_versions set confirmation_status = 'WITHDRAWN', withdrawn_by = ?, withdrawn_at = ?,"
            + " withdraw_reason_text = ?, updated_at = ?, version = version + 1 where config_set_version_id = ?";

    private static final String INSERT_CONFIG_SET = "insert into " + SCHEMA + "config_sets (config_set_id, config_set_code, owner_role_code, created_at,"
            + " updated_at, version) values (?, ?, ?, ?, ?, 0)";
    private static final String INSERT_ITEM = "insert into " + SCHEMA + "employee_activity_type_items (employee_activity_type_item_id, config_set_version_id,"
            + " config_set_code, activity_type_code, activity_type_name, is_enabled, created_at, updated_at) values (?, ?, 'EMPLOYEE_ACTIVITY_TYPES', ?, ?, true, ?, ?)";

    /** TestPostgres hands out one database per class and purpose; the class's tests share it and each rolls back. */
    private static final Map<Class<?>, TestDatabase> DATABASES = new ConcurrentHashMap<>();

    /** A statement and its parameters. */
    record Sql(String text, Object... args) {
    }

    static Sql sql(String text, Object... args) {
        return new Sql(text, args);
    }

    private final Connection connection;
    private int next = 1;

    AdministrationDb(Class<?> testClass) throws SQLException {
        TestDatabase database = DATABASES.computeIfAbsent(testClass, c -> TestPostgres.database(c, TestPostgres.MIGRATED));
        connection = DriverManager.getConnection(database.url(), database.username(), database.password());
        connection.setAutoCommit(false);
    }

    UUID id() {
        return UUID.fromString(String.format("01900000-0000-7000-8000-%012x", next++));
    }

    /** The business day (Asia/Shanghai) of the database's transaction time: the day the confirmation rule compares against. */
    LocalDate today() throws SQLException {
        return value(LocalDate.class, "select (transaction_timestamp() at time zone 'Asia/Shanghai')::date");
    }

    // ---- running statements ----

    void exec(Sql statement) throws SQLException {
        run(statement.text(), statement.args());
    }

    void exec(String text, Object... args) throws SQLException {
        run(text, args);
    }

    /** The statement runs and is undone; fails loudly when the database refuses it. */
    void accepted(Sql statement) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            run(statement.text(), statement.args());
        } finally {
            connection.rollback(savepoint);
        }
    }

    void refusedByConstraint(String sqlState, String constraint, Sql statement) throws SQLException {
        PSQLException refusal = attempt(statement);
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(sqlState);
        String actual = refusal.getServerErrorMessage() == null ? null : refusal.getServerErrorMessage().getConstraint();
        assertThat(actual).as(refusal.getMessage()).isEqualTo(constraint);
    }

    /** A rule raised by a trigger or function: SQLSTATE and a fragment of the message. */
    void refusedByRule(String sqlState, String messagePart, Sql statement) throws SQLException {
        PSQLException refusal = attempt(statement);
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(sqlState);
        assertThat(refusal.getMessage()).contains(messagePart);
    }

    /** Several constraints break at once, so only the SQLSTATE is pinned. */
    void refusedWith(String sqlState, Sql statement) throws SQLException {
        PSQLException refusal = attempt(statement);
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(sqlState);
    }

    private PSQLException attempt(Sql statement) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            run(statement.text(), statement.args());
        } catch (PSQLException e) {
            return e;
        } finally {
            connection.rollback(savepoint);
        }
        throw new AssertionError("the statement was not refused by the database: " + statement.text());
    }

    <T> T value(Class<T> type, String text, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(text)) {
            bind(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).as("a row for: " + text).isTrue();
                return rs.getObject(1, type);
            }
        }
    }

    long count(String text, Object... args) throws SQLException {
        return value(Long.class, text, args);
    }

    List<String> strings(String text, Object... args) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(text)) {
            bind(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
            }
        }
        return values;
    }

    private void run(String text, Object[] args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(text)) {
            bind(statement, args);
            statement.execute();
        }
    }

    private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.rollback();
        connection.close();
    }

    // ---- fixtures ----

    /** The configuration set of the code: the migration's seed row when there is one, otherwise a row made here. */
    UUID configSet(String code) throws SQLException {
        List<String> existing = strings("select config_set_id::text from " + SCHEMA + "config_sets where config_set_code = ?", code);
        if (!existing.isEmpty()) {
            return UUID.fromString(existing.getFirst());
        }
        UUID id = id();
        run(INSERT_CONFIG_SET, new Object[] {id, code, OWNER_ROLE, T0, T0});
        return id;
    }

    /** A PENDING version at version 1, written by AUTHOR. */
    UUID draft(UUID configSetId, String code, int number, LocalDate from) throws SQLException {
        VersionRow row = new VersionRow(id(), configSetId, code, number, from);
        exec(row.statement());
        return row.id;
    }

    Sql itemStatement(UUID versionId, String activityTypeCode) {
        return sql(INSERT_ITEM, id(), versionId, activityTypeCode, "Activity " + activityTypeCode, T0, T0);
    }

    void item(UUID versionId, String activityTypeCode) throws SQLException {
        exec(itemStatement(versionId, activityTypeCode));
    }

    Sql confirmation(UUID versionId, Timestamp confirmedAt, Timestamp updatedAt) {
        return sql(CONFIRM, CONFIRMER, confirmedAt, DECISION, updatedAt, versionId);
    }

    void confirm(UUID versionId, Timestamp at) throws SQLException {
        exec(confirmation(versionId, at, at));
    }

    /** Draft with one item, confirmed by CONFIRMER at the given time. */
    UUID confirmedVersion(UUID configSetId, int number, LocalDate from, Timestamp at) throws SQLException {
        UUID version = draft(configSetId, ACTIVITY_TYPES, number, from);
        item(version, "TYPE_" + number);
        confirm(version, at);
        return version;
    }

    /** One column of the version row. */
    <T> T versionColumn(Class<T> type, String column, UUID versionId) throws SQLException {
        return value(type, "select " + column + " from " + SCHEMA + "config_set_versions where config_set_version_id = ?", versionId);
    }

    /** A PENDING version row; every column is set by the test or has the value of a plain draft. */
    static final class VersionRow {
        UUID id;
        UUID configSetId;
        String code;
        int number;
        LocalDate from;
        LocalDate to;
        String status = "PENDING";
        UUID author = AUTHOR;
        UUID confirmedBy;
        Timestamp confirmedAt;
        UUID decision;
        UUID withdrawnBy;
        Timestamp withdrawnAt;
        String reason;
        long version = 1;

        VersionRow(UUID id, UUID configSetId, String code, int number, LocalDate from) {
            this.id = id;
            this.configSetId = configSetId;
            this.code = code;
            this.number = number;
            this.from = from;
        }

        VersionRow to(LocalDate to) {
            this.to = to;
            return this;
        }

        VersionRow status(String status) {
            this.status = status;
            return this;
        }

        VersionRow confirmed(UUID by, Timestamp at, UUID decision) {
            this.confirmedBy = by;
            this.confirmedAt = at;
            this.decision = decision;
            return this;
        }

        VersionRow withdrawn(UUID by, Timestamp at, String reason) {
            this.withdrawnBy = by;
            this.withdrawnAt = at;
            this.reason = reason;
            return this;
        }

        VersionRow version(long version) {
            this.version = version;
            return this;
        }

        VersionRow number(int number) {
            this.number = number;
            return this;
        }

        VersionRow id(UUID id) {
            this.id = id;
            return this;
        }

        Sql statement() {
            return sql("insert into " + SCHEMA + "config_set_versions (config_set_version_id, config_set_id, config_set_code, version_number,"
                    + " effective_from_date, effective_to_date, confirmation_status, author_id, authored_at, confirmed_by, confirmed_at, decision_audit_entry_id,"
                    + " withdrawn_by, withdrawn_at, withdraw_reason_text, created_at, updated_at, version)"
                    + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, configSetId, code, number, from, to, status, author, T0, confirmedBy, confirmedAt, decision, withdrawnBy, withdrawnAt, reason, T0, T0, version);
        }
    }

    /** A state transition row; the defaults are a plain command-caused ConfigSetVersion start (nothing to PENDING, sequence 1). */
    static final class TransitionRow {
        UUID id;
        String type = "ConfigSetVersion";
        UUID aggregateId;
        long sequence = 1;
        String from;
        String to = "PENDING";
        String actorType = "USER";
        String cause = "COMMAND";
        UUID commandId;
        UUID eventId;
        UUID reverses;
        Timestamp recordedAt = T0;
        Timestamp createdAt = T0;
        Timestamp updatedAt = T0;

        TransitionRow(UUID id, UUID aggregateId, UUID commandId) {
            this.id = id;
            this.aggregateId = aggregateId;
            this.commandId = commandId;
        }

        TransitionRow type(String type) {
            this.type = type;
            return this;
        }

        TransitionRow sequence(long sequence) {
            this.sequence = sequence;
            return this;
        }

        TransitionRow move(String from, String to) {
            this.from = from;
            this.to = to;
            return this;
        }

        TransitionRow actorType(String actorType) {
            this.actorType = actorType;
            return this;
        }

        /** Cause with its trigger columns spelled out. */
        TransitionRow cause(String cause, UUID commandId, UUID eventId, UUID reverses) {
            this.cause = cause;
            this.commandId = commandId;
            this.eventId = eventId;
            this.reverses = reverses;
            return this;
        }

        TransitionRow times(Timestamp recordedAt, Timestamp createdAt, Timestamp updatedAt) {
            this.recordedAt = recordedAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            return this;
        }

        Sql statement() {
            return sql("insert into " + SCHEMA + "state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status,"
                    + " occurred_on_date, recorded_at, legal_entity_id, actor_type, actor_id, cause_kind, command_id, event_id, correlation_id,"
                    + " reverses_transition_id, created_at, updated_at) values (?, ?, ?, ?, ?, ?, DATE '2026-11-02', ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, type, aggregateId, sequence, from, to, recordedAt, actorType, AUTHOR, cause, commandId, eventId, CORRELATION, reverses, createdAt, updatedAt);
        }
    }

    TransitionRow transition(UUID aggregateId) {
        return new TransitionRow(id(), aggregateId, id());
    }
}
