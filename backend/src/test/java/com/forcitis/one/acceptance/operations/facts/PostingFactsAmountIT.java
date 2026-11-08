package com.forcitis.one.acceptance.operations.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/** Lab #30 additive NaN regression; actual PostgreSQL, no changed frozen fixtures. */
class PostingFactsAmountIT {
    static final TestDatabase postgres=TestPostgres.database(PostingFactsAmountIT.class,TestPostgres.MIGRATED);
    static UUID id() { return Uuids.timeOrdered(Instant.now()); }

    @Test
    void nanIsRejectedImmediatelyByAmountConstraintWithValidParentAndActiveComponent() throws Exception {
        try(Connection db=DriverManager.getConnection(postgres.url(),postgres.username(),postgres.password())) {
            // Distinguish absent structure from actual amount behavior before fixture DML.
            try(var statement=db.prepareStatement("select to_regclass('operations_facts.posting_fact_lines') is not null");var rows=statement.executeQuery()) {
                assertTrue(rows.next() && rows.getBoolean(1),"required signed table must exist before NaN behavior probe");
            }
            db.setAutoCommit(false);
            UUID fact=id();
            String component="TEST_FINITE_AMOUNT";
            try(var statement=db.prepareStatement("""
                insert into operations_facts.posting_components
                (id,fact_type,component_key,is_active,created_at,updated_at)
                values (?,'PAYROLL',?,true,now(),now())
                """)) {
                statement.setObject(1,id());statement.setString(2,component);assertEquals(1,statement.executeUpdate());
            }
            try(var statement=db.prepareStatement("""
                insert into operations_facts.posting_facts
                (id,legal_entity_id,fact_type,business_date,posted_fiscal_year_number,posted_period_number,
                 source_context_code,source_id,idempotency_key,created_at,updated_at)
                values (?,?,'PAYROLL',date '2026-11-01',2026,11,'organization.payroll',?,?,now(),now())
                """)) {
                statement.setObject(1,fact);statement.setObject(2,id());statement.setObject(3,id());
                statement.setString(4,"synthetic-finite-"+fact);assertEquals(1,statement.executeUpdate());
            }
            // Same transaction, real active component/header, known-valid amounts and currency.
            for(int number=1;number<=2;number++)try(var statement=db.prepareStatement("""
                insert into operations_facts.posting_fact_lines
                (id,fact_id,line_number,side_kind,component_key,line_amount,line_currency,created_at,updated_at)
                values (?,?,?,?,?,12.34,'CNY',now(),now())
                """)) {
                statement.setObject(1,id());statement.setObject(2,fact);statement.setInt(3,number);
                statement.setString(4,number==1?"DEBIT":"CREDIT");statement.setString(5,component);
                assertEquals(1,statement.executeUpdate(),"finite positive baseline must insert before NaN probe");
            }
            var savepoint=db.setSavepoint();
            try(var statement=db.prepareStatement("""
                insert into operations_facts.posting_fact_lines
                (id,fact_id,line_number,side_kind,component_key,line_amount,line_currency,created_at,updated_at)
                values (?,?,3,'DEBIT',?,CAST('NaN' AS numeric),'CNY',now(),now())
                """)) {
                statement.setObject(1,id());statement.setObject(2,fact);statement.setString(3,component);
                SQLException failure=assertThrows(SQLException.class,statement::executeUpdate,
                        "non-finite NaN must be refused immediately, not accepted until balance commit");
                assertEquals("23514",failure.getSQLState(),"must be CHECK refusal, not trigger/FK/syntax/transport failure");
                PSQLException postgresFailure=assertInstanceOf(PSQLException.class,failure);
                assertNotNull(postgresFailure.getServerErrorMessage());
                assertEquals("ck_posting_fact_lines_line_amount_positive",postgresFailure.getServerErrorMessage().getConstraint(),
                        "only the signed amount CHECK proves this regression");
            }
            db.rollback(savepoint);db.releaseSavepoint(savepoint);
            db.commit(); // The untouched finite baseline must still satisfy real deferred balance.
            try(var statement=db.prepareStatement("select count(*) from operations_facts.posting_fact_lines where fact_id=?")) {
                statement.setObject(1,fact);
                try(var rows=statement.executeQuery()){assertTrue(rows.next());assertEquals(2,rows.getLong(1));}
            }
        }
    }
}
