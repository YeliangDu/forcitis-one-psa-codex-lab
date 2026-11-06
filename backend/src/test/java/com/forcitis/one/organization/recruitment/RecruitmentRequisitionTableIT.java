package com.forcitis.one.organization.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.numbering.api.DocumentTypes;
import java.time.LocalDate;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1450 R1: the signed table refuses every row and every write the record does not allow. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class RecruitmentRequisitionTableIT {
    private static final String TABLE = "organization_recruitment.recruitment_requisitions";

    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IdGenerator ids;

    @Autowired
    DocumentTypes documentTypes;

    private UUID insertRaw(String status, String reason, boolean approved) {
        return insertRaw(status, reason, approved, null, null);
    }

    private UUID insertRaw(String status, String reason, boolean approved, String number, UUID approval) {
        UUID id = ids.next();
        boolean draft = status.equals("DRAFT");
        jdbc.sql("insert into " + TABLE + """
                 (id, requisition_number, position_id, org_unit_id, legal_entity_id, requested_by_employee_id, requested_count, expected_start_date, description_text,
                  status, close_reason_kind, approval_instance_id, submitted_at, approved_at, closed_at, created_at, updated_at)
                 values (:id, :number, :position, :unit, :entity, :by, 2, date '2026-12-01', 'one analyst',
                  :status, :reason, :approval, :submitted, :approvedAt, :closedAt, timestamptz '2026-11-01 08:00+00', timestamptz '2026-11-01 08:00+00')""")
                .param("id", id).param("number", draft ? null : number == null ? "REQ-" + id : number).param("position", ids.next()).param("unit", ids.next()).param("entity", ids.next())
                .param("by", ids.next()).param("status", status).param("reason", reason).param("approval", draft ? null : approval == null ? ids.next() : approval)
                .param("submitted", draft ? null : at("2026-11-02 08:00+00"), java.sql.Types.OTHER)
                .param("approvedAt", approved ? at("2026-11-03 08:00+00") : null, java.sql.Types.OTHER)
                .param("closedAt", reason == null ? null : at("2026-11-04 08:00+00"), java.sql.Types.OTHER).update();
        return id;
    }

    /** A legal history: the row is created DRAFT or SUBMITTED and reaches the wanted state through the signed edges. */
    private UUID insert(String status, String reason, boolean approved) {
        if (status.equals("DRAFT") || status.equals("SUBMITTED")) {
            return insertRaw(status, reason, approved);
        }
        UUID id = insertRaw("SUBMITTED", null, false);
        if (approved) {
            update(id, "status = 'APPROVED', approved_at = timestamptz '2026-11-03 08:00+00'");
        }
        if (status.equals("CLOSED")) {
            update(id, "status = 'CLOSED', close_reason_kind = '" + reason + "', closed_at = timestamptz '2026-11-04 08:00+00'");
        }
        return id;
    }

    private static java.time.OffsetDateTime at(String text) {
        return java.time.OffsetDateTime.parse(text.replace(' ', 'T').replace("+00", "+00:00"));
    }

    private void update(UUID id, String set) {
        jdbc.sql("update " + TABLE + " set " + set + " where id = :id").param("id", id).update();
    }

    private static void refused(Runnable write) {
        assertThatThrownBy(write::run).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("#1450 the legal rows exist: draft, direct submitted, approved, and each closed form")
    void legalRowsAreAccepted() {
        insert("DRAFT", null, false);
        insert("SUBMITTED", null, false);
        insert("APPROVED", null, true);
        insert("CLOSED", "REJECTED", false);
        insert("CLOSED", "WITHDRAWN", false);
        insert("CLOSED", "CANCELLED", true);
        insert("CLOSED", "FILLED", true);
        assertThat(jdbc.sql("select count(*) from " + TABLE).query(Integer.class).single()).isGreaterThanOrEqualTo(7);
    }

    @Test
    @DisplayName("#1450 state_columns: a row that pairs status, number, approval, close and time columns wrongly is refused")
    void impossibleRowsAreRefused() {
        refused(() -> insertRaw("SUBMITTED", null, true));
        refused(() -> insertRaw("CLOSED", "REJECTED", false));
        UUID approvedRow = insert("APPROVED", null, true);
        refused(() -> update(approvedRow, "approved_at = null"));
        UUID rejectedRow = insert("CLOSED", "REJECTED", false);
        refused(() -> update(rejectedRow, "approved_at = timestamptz '2026-11-03 08:00+00'"));
        UUID cancelledRow = insert("CLOSED", "CANCELLED", true);
        refused(() -> update(cancelledRow, "close_reason_kind = null"));
        UUID draft = insert("DRAFT", null, false);
        refused(() -> update(draft, "requisition_number = 'REQ-X'"));
        refused(() -> update(draft, "approval_instance_id = gen_random_uuid()"));
        UUID submitted = insert("SUBMITTED", null, false);
        refused(() -> update(submitted, "requisition_number = null"));
        refused(() -> update(submitted, "submitted_at = null"));
    }

    @Test
    @DisplayName("#1450 state_columns: the time order submitted, approved, closed is enforced")
    void timeOrderIsEnforced() {
        UUID submitted = insert("SUBMITTED", null, false);
        refused(() -> update(submitted, "status = 'APPROVED', approved_at = timestamptz '2026-11-01 00:00+00'"));
        UUID approved = insert("APPROVED", null, true);
        refused(() -> update(approved, "status = 'CLOSED', close_reason_kind = 'FILLED', closed_at = timestamptz '2026-11-02 00:00+00'"));
    }

    @Test
    @DisplayName("#1450 column checks: count 1 to 999, description 1 to 500 and not blank, enums, unique number and approval")
    void columnChecksHold() {
        UUID draft = insert("DRAFT", null, false);
        refused(() -> update(draft, "requested_count = 0"));
        refused(() -> update(draft, "requested_count = 1000"));
        refused(() -> update(draft, "description_text = '   '"));
        refused(() -> update(draft, "description_text = repeat('x', 501)"));
        refused(() -> update(draft, "status = 'REJECTED'"));
        UUID a = insert("SUBMITTED", null, false);
        String number = jdbc.sql("select requisition_number from " + TABLE + " where id = :id").param("id", a).query(String.class).single();
        UUID approval = jdbc.sql("select approval_instance_id from " + TABLE + " where id = :id").param("id", a).query(UUID.class).single();
        refused(() -> insertRaw("SUBMITTED", null, false, number, null));
        refused(() -> insertRaw("SUBMITTED", null, false, null, approval));
    }

    @Test
    @DisplayName("#1450 state_columns holds on its own: with the trigger switched off the database still refuses a closed row without a reason and a reason without a closed row")
    void stateColumnsHoldWithoutTheTrigger() {
        jdbc.sql("alter table " + TABLE + " disable trigger tg_recruitment_requisitions_change").update();
        try {
            refused(() -> insertRaw("CLOSED", null, false));
            refused(() -> insertRaw("SUBMITTED", "REJECTED", false));
        } finally {
            jdbc.sql("alter table " + TABLE + " enable trigger tg_recruitment_requisitions_change").update();
        }
    }

    @Test
    @DisplayName("#1450 trigger: only the signed edges are written, a closed row never changes, only a draft is deleted, truncate is refused")
    void onlyTheSignedWritesPass() {
        UUID draft = insert("DRAFT", null, false);
        update(draft, "requested_count = 3, description_text = 'edited', version = version + 1");
        refused(() -> update(draft, "status = 'APPROVED'"));
        refused(() -> update(draft, "legal_entity_id = gen_random_uuid()"));
        refused(() -> update(draft, "requested_by_employee_id = gen_random_uuid()"));
        refused(() -> update(draft, "created_at = created_at + interval '1 day'"));

        UUID submitted = insert("SUBMITTED", null, false);
        refused(() -> update(submitted, "requested_count = 5"));
        refused(() -> update(submitted, "updated_at = now()"));
        refused(() -> update(submitted, "status = 'CLOSED', close_reason_kind = 'CANCELLED', approved_at = timestamptz '2026-11-03 00:00+00', closed_at = timestamptz '2026-11-04 00:00+00'"));
        refused(() -> update(submitted, "status = 'APPROVED', approved_at = timestamptz '2026-11-03 00:00+00', requested_count = 5"));
        update(submitted, "status = 'APPROVED', approved_at = timestamptz '2026-11-03 00:00+00'");

        refused(() -> update(submitted, "status = 'CLOSED', close_reason_kind = 'REJECTED', closed_at = timestamptz '2026-11-04 00:00+00', approved_at = null"));
        refused(() -> update(submitted, "status = 'CLOSED', close_reason_kind = 'FILLED', closed_at = timestamptz '2026-11-04 00:00+00', approved_at = timestamptz '2026-11-03 01:00+00'"));
        update(submitted, "status = 'CLOSED', close_reason_kind = 'FILLED', closed_at = timestamptz '2026-11-04 00:00+00'");
        refused(() -> update(submitted, "updated_at = now()"));
        refused(() -> jdbc.sql("delete from " + TABLE + " where id = :id").param("id", submitted).update());

        UUID rejected = insert("SUBMITTED", null, false);
        update(rejected, "status = 'CLOSED', close_reason_kind = 'REJECTED', closed_at = timestamptz '2026-11-04 00:00+00'");

        assertThat(jdbc.sql("delete from " + TABLE + " where id = :id").param("id", draft).update()).isEqualTo(1);
        refused(() -> jdbc.sql("truncate " + TABLE).update());
    }

    @Test
    @DisplayName("#1450 the requisition document type is registered in platform.numbering and in force")
    void documentTypeIsRegistered() {
        documentTypes.requireInForce(new DocumentTypeCode("RECRUITMENT_REQUISITION"), LocalDate.of(2026, 11, 5));
        assertThatThrownBy(() -> documentTypes.requireInForce(new DocumentTypeCode("RECRUITMENT_REQUISITIONS"), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(DomainException.class);
    }
}
