package com.forcitis.one.acceptance.operations.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lab #30 signed same-transaction/savepoint/append-only/sequence PostgreSQL behavior. */
class PostingFactsIntegrityIT {
    static final TestDatabase postgres=TestPostgres.database(PostingFactsIntegrityIT.class,TestPostgres.MIGRATED);
    record Fact(UUID id,UUID entity,String key,long number) {}
    interface Sql { void run() throws Exception; }

    Connection ready() throws Exception {
        Connection db=DriverManager.getConnection(postgres.url(),postgres.username(),postgres.password());
        try {
            assertThat(PostingFactsSchemaIT.strings(db,"select schema_name from information_schema.schemata where schema_name='operations_facts'"))
                    .as("missing signed operations_facts schema").containsExactly("operations_facts");
            db.setAutoCommit(false);return db;
        } catch (Exception|AssertionError failure) { db.close();throw failure; }
    }
    UUID id() {return Uuids.timeOrdered(Instant.now());}
    void run(Connection db,String sql,Object...values) throws Exception {
        try(var statement=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);statement.execute();}
    }
    String component(Connection db,boolean active) throws Exception {
        String key="T"+id().toString().replace("-","").toUpperCase(java.util.Locale.ROOT);
        run(db,"insert into operations_facts.posting_components (id,fact_type,component_key,is_active,created_at,updated_at) values (?,'PAYROLL',?,?,now(),now())",id(),key,active);
        return key;
    }
    Fact header(Connection db) throws Exception {return header(db,id(),"synthetic-"+id());}
    Fact header(Connection db,UUID entity,String key) throws Exception {
        UUID fact=id();
        try(var statement=db.prepareStatement("""
            insert into operations_facts.posting_facts
            (id,legal_entity_id,fact_type,business_date,posted_fiscal_year_number,posted_period_number,source_context_code,source_id,idempotency_key,created_at,updated_at)
            values (?,?,'PAYROLL',date '2026-11-01',2026,11,'organization.payroll',?,?,now(),now()) returning post_number
            """)) {
            statement.setObject(1,fact);statement.setObject(2,entity);statement.setObject(3,id());statement.setString(4,key);
            try(var row=statement.executeQuery()){assertThat(row.next()).isTrue();return new Fact(fact,entity,key,row.getLong(1));}
        }
    }
    void line(Connection db,Fact fact,String component,int number,String side,String amount,String currency) throws Exception {
        run(db,"""
            insert into operations_facts.posting_fact_lines
            (id,fact_id,line_number,side_kind,component_key,line_amount,line_currency,created_at,updated_at)
            values (?,?,?,?,?,?,?,now(),now())
            """,id(),fact.id(),number,side,component,new BigDecimal(amount),currency);
    }
    void balanced(Connection db,Fact fact,String key) throws Exception {line(db,fact,key,1,"DEBIT","12.34","CNY");line(db,fact,key,2,"CREDIT","12.34","CNY");}
    void rejected(Sql operation) {
        SQLException failure=catchThrowableOfType(operation::run,SQLException.class);
        assertThat(failure).as("database must reject signed invariant, not accept or fail with SQL syntax/transport error").isNotNull();
        assertThat(failure.getSQLState()).matches("23[A-Z0-9]{3}|P0001");
    }
    long count(Connection db,String table,UUID fact) throws Exception {
        try(var s=db.prepareStatement("select count(*) from operations_facts."+table+" where "+(table.equals("posting_facts")?"id":"fact_id")+"=?")) {
            s.setObject(1,fact);try(var rows=s.executeQuery()){rows.next();return rows.getLong(1);}
        }
    }

    @Test void validBalancedSyntheticPostingCommits() throws Exception {
        try(Connection db=ready()) {
            String key=component(db,true);Fact fact=header(db);balanced(db,fact,key);db.commit();
            assertThat(count(db,"posting_facts",fact.id())).isEqualTo(1);
            assertThat(count(db,"posting_fact_lines",fact.id())).isEqualTo(2);
        }
    }
    @Test void deferredBalanceRejectsHeaderOnlyMissingSideAndUnequalTotalsAtCommit() throws Exception {
        for(int shape:List.of(0,1,2))try(Connection db=ready()) {
            String key=component(db,true);Fact fact=header(db);
            if(shape>0)line(db,fact,key,1,"DEBIT","12.34","CNY");
            if(shape==2)line(db,fact,key,2,"CREDIT","12.33","CNY");
            rejected(db::commit);db.rollback();assertThat(count(db,"posting_facts",fact.id())).isZero();
        }
    }
    @Test void componentsAmountsCurrencyAndSideChecksAreRealDatabaseRefusals() throws Exception {
        for(String invalid:List.of("unknown","inactive","zero","negative","currency","side","line-number"))try(Connection db=ready()) {
            String key=component(db,!invalid.equals("inactive"));Fact fact=header(db);
            String used=invalid.equals("unknown")?"UNREGISTERED_TEST_KEY":key;
            rejected(()->line(db,fact,used,invalid.equals("line-number")?0:1,invalid.equals("side")?"OTHER":"DEBIT",
                invalid.equals("zero")?"0":invalid.equals("negative")?"-1":"12.34",invalid.equals("currency")?"USD":"CNY"));
            db.rollback();
        }
    }
    @Test void laterTransactionCannotAppendEvenAnOtherwiseBalancedPair() throws Exception {
        try(Connection db=ready()) {
            String key=component(db,true);Fact fact=header(db);balanced(db,fact,key);db.commit();
            rejected(()->{line(db,fact,key,3,"DEBIT","1.00","CNY");line(db,fact,key,4,"CREDIT","1.00","CNY");db.commit();});
            db.rollback();assertThat(count(db,"posting_fact_lines",fact.id())).isEqualTo(2);
        }
    }
    @Test void releasedSavepointsWorkInBothDirectionsAndRolledBackRowsDisappear() throws Exception {
        for(String arrangement:List.of("header-inside","lines-inside","rolled-back"))try(Connection db=ready()) {
            String key=component(db,true);
            if(arrangement.equals("rolled-back")) {
                var doomed=db.setSavepoint();Fact discarded=header(db);balanced(db,discarded,key);
                db.rollback(doomed);db.releaseSavepoint(doomed);
                assertThat(count(db,"posting_facts",discarded.id())).isZero();assertThat(count(db,"posting_fact_lines",discarded.id())).isZero();
            }
            Fact fact;
            if(arrangement.equals("header-inside")) {
                var save=db.setSavepoint();fact=header(db);db.releaseSavepoint(save);balanced(db,fact,key);
            } else {
                fact=header(db);var save=db.setSavepoint();balanced(db,fact,key);db.releaseSavepoint(save);
            }
            db.commit();assertThat(count(db,"posting_fact_lines",fact.id())).isEqualTo(2);
        }
    }
    @Test void factsAndLinesRejectUpdateDeleteAndTruncateWithoutWeakeningAnyRow() throws Exception {
        try(Connection db=ready()) {
            String key=component(db,true);Fact fact=header(db);balanced(db,fact,key);db.commit();
            for(String table:List.of("posting_facts","posting_fact_lines"))for(String verb:List.of("update","delete")) {
                var save=db.setSavepoint();String predicate=table.equals("posting_facts")?"id":"fact_id";
                rejected(()->run(db,(verb.equals("update")?"update operations_facts."+table+" set updated_at=updated_at":"delete from operations_facts."+table)+" where "+predicate+"=?",fact.id()));
                db.rollback(save);db.releaseSavepoint(save);
            }
            var save=db.setSavepoint();rejected(()->run(db,"truncate operations_facts.posting_fact_lines, operations_facts.posting_facts"));db.rollback(save);db.releaseSavepoint(save);
            assertThat(count(db,"posting_facts",fact.id())).isEqualTo(1);assertThat(count(db,"posting_fact_lines",fact.id())).isEqualTo(2);
        }
    }
    @Test void sequenceAllocationsAdvanceAcrossRollbackAndReplayKeyRemainsUnique() throws Exception {
        try(Connection db=ready()) {
            String key=component(db,true);Fact first=header(db);balanced(db,first,key);db.commit();
            Fact burned=header(db);long gap=burned.number();db.rollback();
            Fact next=header(db);balanced(db,next,key);db.commit();
            assertThat(gap).isGreaterThan(first.number());assertThat(next.number()).isGreaterThan(gap);
            var save=db.setSavepoint();rejected(()->header(db,first.entity(),first.key()));db.rollback(save);db.releaseSavepoint(save);
            assertThat(count(db,"posting_facts",burned.id())).isZero();
            assertThat(first.number()).isNotEqualTo(next.number());
        }
    }
}
