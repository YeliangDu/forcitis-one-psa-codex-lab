package com.forcitis.one.operations.facts;

import static org.junit.jupiter.api.Assertions.*;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Lab #30: actual migrated triggers with synthetic private-container owner/runtime separation. */
@Testcontainers
class PostingFactsDmlRoleIT {
    static final String OWNER="fixture_f1_owner",DML="fixture_f1_dml",COMPONENT="F1_DML_BOTH";
    static final String PASSWORD=UUID.randomUUID().toString();
    @Container static PostgreSQLContainer postgres=TestPostgres.container().withInitScript("init-schemas.sql");

    @BeforeAll static void migrateAndInstallPrivateFixtureRoles() throws Exception {
        Properties properties=new Properties();
        try(var stream=PostingFactsDmlRoleIT.class.getResourceAsStream("/application.properties")){assertNotNull(stream);properties.load(stream);}
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
            .locations(properties.getProperty("spring.flyway.locations").split(","))
            .defaultSchema(properties.getProperty("spring.flyway.default-schema"))
            .schemas(properties.getProperty("spring.flyway.schemas").split(",")).createSchemas(true).load().migrate();
        try(Connection db=admin()) {
            execute(db,"CREATE ROLE "+OWNER+" NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
            execute(db,"CREATE ROLE "+DML+" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD '"+PASSWORD+"'");
            String database=db.getCatalog();assertTrue(database.matches("[A-Za-z0-9_]+"));
            execute(db,"GRANT CONNECT,TEMP ON DATABASE "+database+" TO "+DML);
            execute(db,"GRANT TEMP ON DATABASE "+database+" TO "+OWNER);
            // Test-only ownership provisioning; actual migration function bodies remain unchanged.
            execute(db,"ALTER SCHEMA operations_facts OWNER TO "+OWNER);
            for(String table:List.of("posting_facts","posting_fact_lines","posting_components"))
                execute(db,"ALTER TABLE operations_facts."+table+" OWNER TO "+OWNER);
            execute(db,"ALTER SEQUENCE operations_facts.posting_fact_post_seq OWNER TO "+OWNER);
            List<String> functions=new ArrayList<>();
            try(var s=db.createStatement();var rows=s.executeQuery("select proname from pg_proc where pronamespace='operations_facts'::regnamespace and pronargs=0")) {
                while(rows.next()){String name=rows.getString(1);assertTrue(name.matches("[a-z_]+"));functions.add(name);}
            }
            assertTrue(functions.contains("guard_posting_fact_transaction"));
            for(String function:functions)execute(db,"ALTER FUNCTION operations_facts."+function+"() OWNER TO "+OWNER);
            execute(db,"GRANT USAGE ON SCHEMA operations_facts TO "+DML);
            execute(db,"GRANT SELECT,INSERT ON operations_facts.posting_facts,operations_facts.posting_fact_lines TO "+DML);
            execute(db,"GRANT SELECT ON operations_facts.posting_components TO "+DML);
            execute(db,"GRANT USAGE ON SEQUENCE operations_facts.posting_fact_post_seq TO "+DML);
            execute(db,"insert into operations_facts.posting_components(id,fact_type,component_key,is_active,created_at,updated_at) values (?,'PAYROLL',?,true,now(),now())",id(),COMPONENT);
        }
    }

    static Connection admin() throws SQLException{return DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());}
    static Connection runtime() throws Exception {
        Connection db=DriverManager.getConnection(postgres.getJdbcUrl(),DML,PASSWORD);
        try(var s=db.createStatement();var r=s.executeQuery("select current_user,session_user,rolsuper,rolcreaterole,pg_has_role(current_user,'"+OWNER+"','MEMBER') from pg_roles where rolname=current_user")) {
            assertTrue(r.next());assertEquals(DML,r.getString(1));assertEquals(DML,r.getString(2));
            assertFalse(r.getBoolean(3));assertFalse(r.getBoolean(4));assertFalse(r.getBoolean(5));
        }
        db.setAutoCommit(false);return db;
    }
    static UUID id(){return Uuids.timeOrdered(Instant.now());}
    static void execute(Connection db,String sql,Object...args) throws Exception {
        try(var s=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.execute();}
    }
    static UUID header(Connection db) throws Exception {
        UUID fact=id();execute(db,"""
            insert into operations_facts.posting_facts(id,legal_entity_id,fact_type,business_date,
             posted_fiscal_year_number,posted_period_number,source_context_code,source_id,idempotency_key,created_at,updated_at)
            values (?,?,'PAYROLL',date '2026-11-01',2026,11,'organization.payroll',?,?,now(),now())
            """,fact,id(),id(),"synthetic-dml-"+fact);return fact;
    }
    static void line(Connection db,UUID fact,int number,String side) throws Exception {
        execute(db,"""
            insert into operations_facts.posting_fact_lines(id,fact_id,line_number,side_kind,component_key,
             line_amount,line_currency,created_at,updated_at) values (?,?,?,?,?,12.34,'CNY',now(),now())
            """,id(),fact,number,side,COMPONENT);
    }
    static void balanced(Connection db,UUID fact) throws Exception{line(db,fact,1,"DEBIT");line(db,fact,2,"CREDIT");}
    interface Sql{void run() throws Exception;}
    static void refused(String state,Sql operation) {
        SQLException failure=assertThrows(SQLException.class,operation::run);assertEquals(state,failure.getSQLState());
    }

    @Test void actualDmlRoleUsesOwnerTempAndBalancedSavepointsWithoutDirectTempMutation() throws Exception {
        for(boolean headerInside:List.of(true,false))try(Connection db=runtime()) {
            UUID fact;
            if(headerInside){var save=db.setSavepoint();fact=header(db);db.releaseSavepoint(save);}
            else fact=header(db);
            try(var s=db.createStatement();var rows=s.executeQuery("select pg_get_userbyid(relowner) from pg_class where oid='pg_temp.f1_posting_transaction_members'::regclass")) {
                assertTrue(rows.next());assertEquals(OWNER,rows.getString(1));
            }
            for(String sql:List.of("insert into pg_temp.f1_posting_transaction_members(fact_id) values ('"+id()+"')",
                "update pg_temp.f1_posting_transaction_members set fact_id=fact_id",
                "delete from pg_temp.f1_posting_transaction_members","truncate pg_temp.f1_posting_transaction_members")) {
                var save=db.setSavepoint();refused("42501",()->execute(db,sql));db.rollback(save);db.releaseSavepoint(save);
            }
            var save=db.setSavepoint();balanced(db,fact);db.releaseSavepoint(save);db.commit();
            try(var s=db.prepareStatement("select count(*) from operations_facts.posting_fact_lines where fact_id=?")) {
                s.setObject(1,fact);try(var rows=s.executeQuery()){assertTrue(rows.next());assertEquals(2,rows.getLong(1));}
            }
        }
    }

    @Test void laterBalancedAppendCannotBeEnabledByCallerGuc() throws Exception {
        try(Connection db=runtime()) {
            UUID fact=header(db);balanced(db,fact);db.commit();
            execute(db,"select set_config('operations_facts.posting_fact_transaction',?,true)",fact.toString());
            refused("23001",()->{line(db,fact,3,"DEBIT");line(db,fact,4,"CREDIT");db.commit();});db.rollback();
        }
    }

    @Test void callerOwnedMatchingTempCannotBeAdoptedOrRepairedByDefiner() throws Exception {
        try(Connection db=runtime()) {
            execute(db,"CREATE TEMP TABLE f1_posting_transaction_members(fact_id uuid NOT NULL PRIMARY KEY) ON COMMIT DROP");
            var save=db.setSavepoint();refused("23001",()->header(db));db.rollback(save);db.releaseSavepoint(save);
            try(var s=db.createStatement();var rows=s.executeQuery("select pg_get_userbyid(relowner) from pg_class where oid='pg_temp.f1_posting_transaction_members'::regclass")) {
                assertTrue(rows.next());assertEquals(DML,rows.getString(1));
            }
            db.rollback();
        }
    }

    @Test void privilegedLookalikeTriggerAttachmentStillCannotCreateTrustedMembership() throws Exception {
        // Deliberately grant only this EXECUTE inside the private scratch container
        // to test trigger context defense, not to propose any runtime deployment grant.
        try(Connection db=admin()){execute(db,"GRANT EXECUTE ON FUNCTION operations_facts.guard_posting_fact_transaction() TO "+DML);}
        try(Connection db=runtime()) {
            execute(db,"CREATE TEMP TABLE f1_runtime_lookalike(id uuid PRIMARY KEY)");
            execute(db,"CREATE TRIGGER forged_attachment AFTER INSERT ON f1_runtime_lookalike FOR EACH ROW EXECUTE FUNCTION operations_facts.guard_posting_fact_transaction()");
            var save=db.setSavepoint();refused("23001",()->execute(db,"insert into f1_runtime_lookalike values (?)",id()));db.rollback(save);db.releaseSavepoint(save);
            try(var s=db.createStatement();var rows=s.executeQuery("select to_regclass('pg_temp.f1_posting_transaction_members') is null")) {
                assertTrue(rows.next());assertTrue(rows.getBoolean(1));
            }
            db.rollback();
        } finally {try(Connection db=admin()){execute(db,"REVOKE EXECUTE ON FUNCTION operations_facts.guard_posting_fact_transaction() FROM "+DML);}}
    }
}
