package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.numbering.api.DocumentTypes;
import com.forcitis.one.platform.numbering.api.NumberingError;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** ADR-0066 decision 4: a document type is one row in the numbering registry; writers of other modules check the code through DocumentTypes. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
class DocumentTypeRegistryIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @MockitoBean
    com.forcitis.one.organization.workforce.api.LegalEntityDirectory legalEntityDirectory;

    static final TestDatabase postgres = TestPostgres.database(DocumentTypeRegistryIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    @Autowired
    DocumentTypes documentTypes;

    @Autowired
    com.forcitis.one.platform.numbering.api.RegisteredDocumentTypes registeredTypes;

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("the four seeded types are in force")
    void seededTypesAreInForce() {
        for (String code : new String[] {"QUOTE", "TIMESHEET", "JOURNAL_VOUCHER", "EMPLOYEE"}) {
            assertThatCode(() -> documentTypes.requireInForce(new DocumentTypeCode(code), DAY)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a well-formed code that is not registered is refused with PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED")
    void unregisteredCodeIsRefused() {
        assertThatThrownBy(() -> documentTypes.requireInForce(new DocumentTypeCode("PAYMENT"), DAY))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(NumberingError.PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED));
    }

    @Test
    @DisplayName("a new document type is one inserted row: no DDL and no change to kernel or platform code")
    void aNewTypeIsOneRow() {
        DocumentTypeCode code = new DocumentTypeCode("PURCHASE_ORDER");
        assertThatThrownBy(() -> documentTypes.requireInForce(code, DAY)).isInstanceOf(DomainException.class);

        jdbc.sql("insert into platform_numbering.document_types (document_type_id, code, owner_bc_code, is_finance_confirmation_required, effective_from_date)"
                + " values (uuidv7(), 'PURCHASE_ORDER', 'procurement', false, DATE '2026-01-01')").update();

        assertThatCode(() -> documentTypes.requireInForce(code, DAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a retired type is still registered: a continuing writer is not stranded, an unknown code is refused")
    void retiredTypeStaysRegistered() {
        jdbc.sql("insert into platform_numbering.document_types (document_type_id, code, owner_bc_code, is_finance_confirmation_required, effective_from_date, retired_date)"
                + " values (uuidv7(), 'OLD_FORM', 'procurement', false, DATE '2020-01-01', DATE '2021-01-01')").update();

        assertThatCode(() -> registeredTypes.requireRegistered(new DocumentTypeCode("OLD_FORM"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> registeredTypes.requireRegistered(new DocumentTypeCode("NEVER_REGISTERED"))).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("a type is refused before its effective date and from its retired date on")
    void typeIsOnlyInForceInsideItsPeriod() {
        jdbc.sql("insert into platform_numbering.document_types (document_type_id, code, owner_bc_code, is_finance_confirmation_required, effective_from_date, retired_date)"
                + " values (uuidv7(), 'SEASONAL_FORM', 'procurement', false, DATE '2026-03-01', DATE '2026-09-01')").update();
        DocumentTypeCode code = new DocumentTypeCode("SEASONAL_FORM");

        assertThatThrownBy(() -> documentTypes.requireInForce(code, LocalDate.of(2026, 2, 28))).isInstanceOf(DomainException.class);
        assertThatCode(() -> documentTypes.requireInForce(code, LocalDate.of(2026, 3, 1))).doesNotThrowAnyException();
        assertThatCode(() -> documentTypes.requireInForce(code, LocalDate.of(2026, 8, 31))).doesNotThrowAnyException();
        assertThatThrownBy(() -> documentTypes.requireInForce(code, LocalDate.of(2026, 9, 1))).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("a registered type is never deleted or rewritten; retired_date and number_code are set once")
    void registeredRowsAreAppendOnly() {
        jdbc.sql("insert into platform_numbering.document_types (document_type_id, code, owner_bc_code, is_finance_confirmation_required, effective_from_date)"
                + " values (uuidv7(), 'SITE_REPORT', 'delivery', false, DATE '2026-01-01')").update();

        assertThatThrownBy(() -> jdbc.sql("delete from platform_numbering.document_types where code = 'SITE_REPORT'").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate platform_numbering.document_types cascade").update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.document_types set code = 'SITE_NOTE' where code = 'SITE_REPORT'").update())
                .hasMessageContaining("cannot be rewritten");
        assertThat(jdbc.sql("update platform_numbering.document_types set retired_date = DATE '2026-12-31', number_code = 'SR' where code = 'SITE_REPORT'").update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.document_types set retired_date = DATE '2027-01-31' where code = 'SITE_REPORT'").update())
                .hasMessageContaining("set once");
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.document_types set number_code = 'SX' where code = 'SITE_REPORT'").update())
                .hasMessageContaining("set once");
    }
}
