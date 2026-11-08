package com.forcitis.one.acceptance.operations.facts;

import static org.junit.jupiter.api.Assertions.*;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/** #30 additive signed schema boundaries; no edits to original four fixtures. */
class PostingFactsSignedBoundariesIT {
    static final TestDatabase postgres=TestPostgres.database(PostingFactsSignedBoundariesIT.class,TestPostgres.MIGRATED);
    static UUID id(){return Uuids.timeOrdered(Instant.now());}
    interface Sql{void run()throws Exception;}
    Connection ready() throws Exception {
        Connection db=DriverManager.getConnection(postgres.url(),postgres.username(),postgres.password());
        try(var s=db.createStatement();var rows=s.executeQuery("select to_regclass('operations_facts.posting_facts') is not null")) {
            assertTrue(rows.next()&&rows.getBoolean(1),"missing signed F1 schema");
        } catch(Exception|AssertionError failure){db.close();throw failure;}
        db.setAutoCommit(false);return db;
    }
    void execute(Connection db,String sql,Object...values)throws Exception{
        try(var s=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.execute();}
    }
    String component(Connection db)throws Exception{
        String key="T"+id().toString().replace("-","").toUpperCase(java.util.Locale.ROOT);
        execute(db,"insert into operations_facts.posting_components(id,fact_type,component_key,is_active,created_at,updated_at) values (?,'PAYROLL',?,true,now(),now())",id(),key);return key;
    }
    Map<String,Object> header(){
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("id",id());values.put("entity",id());values.put("year",2026);values.put("period",11);
        values.put("original_year",null);values.put("original_period",null);values.put("source","organization.payroll");
        values.put("source_id",id());values.put("key","synthetic-"+id());values.put("reverse",null);return values;
    }
    void insert(Connection db,Map<String,Object> v)throws Exception{
        execute(db,"""
            insert into operations_facts.posting_facts(id,legal_entity_id,fact_type,business_date,posted_fiscal_year_number,posted_period_number,
             original_fiscal_year_number,original_period_number,source_context_code,source_id,idempotency_key,reverses_fact_id,created_at,updated_at)
            values (?,?,'PAYROLL',date '2026-11-01',?,?,?,?,?,?,?,?,now(),now())
            """,v.get("id"),v.get("entity"),v.get("year"),v.get("period"),v.get("original_year"),v.get("original_period"),
                v.get("source"),v.get("source_id"),v.get("key"),v.get("reverse"));
    }
    void balanced(Connection db,Map<String,Object> v,String key)throws Exception{
        insert(db,v);
        for(int n=1;n<=2;n++)execute(db,"""
            insert into operations_facts.posting_fact_lines(id,fact_id,line_number,side_kind,component_key,line_amount,line_currency,created_at,updated_at)
            values (?,?,?,?,?,1.25,'CNY',now(),now())
            """,id(),v.get("id"),n,n==1?"DEBIT":"CREDIT",key);
        db.commit();
    }
    void refused(Connection db,String state,String constraint,Sql action)throws Exception{
        var save=db.setSavepoint();SQLException failure=assertThrows(SQLException.class,action::run);
        assertEquals(state,failure.getSQLState(),"exact intended integrity refusal, not unrelated SQL/transport");
        if(constraint!=null){PSQLException pg=assertInstanceOf(PSQLException.class,failure);assertNotNull(pg.getServerErrorMessage());assertEquals(constraint,pg.getServerErrorMessage().getConstraint());}
        db.rollback(save);db.releaseSavepoint(save);
    }

    @Test void reversalIsUniqueNotSelfAndUnknownTargetIsRefused()throws Exception{
        try(Connection db=ready()){
            String key=component(db);var original=header();balanced(db,original,key);
            var reversal=header();reversal.put("reverse",original.get("id"));balanced(db,reversal,key);
            var duplicate=header();duplicate.put("reverse",original.get("id"));
            refused(db,"23505","uk_posting_facts_reverses_fact_id",()->insert(db,duplicate));
            var self=header();self.put("reverse",self.get("id"));
            refused(db,"23514","ck_posting_facts_reverses_not_self",()->insert(db,self));
            var missing=header();missing.put("reverse",id());
            refused(db,"23503","fk_posting_facts_reverses_fact_id",()->insert(db,missing));
        }
    }

    @Test void signedYearPeriodAndOriginalPairBoundariesCommitOrRejectExactly()throws Exception{
        try(Connection db=ready()){
            String key=component(db);
            for(int[] pair:List.of(new int[]{2000,1},new int[]{2999,12})){
                var value=header();value.put("year",pair[0]);value.put("period",pair[1]);balanced(db,value,key);
                var routed=header();routed.put("original_year",pair[0]);routed.put("original_period",pair[1]);balanced(db,routed,key);
            }
            for(int year:List.of(1999,3000)){
                var posted=header();posted.put("year",year);refused(db,"23514","ck_posting_facts_posted_fiscal_year_number",()->insert(db,posted));
                var original=header();original.put("original_year",year);original.put("original_period",1);
                refused(db,"23514","ck_posting_facts_original_fiscal_year_number",()->insert(db,original));
            }
            for(int period:List.of(0,13)){
                var posted=header();posted.put("period",period);refused(db,"23514","ck_posting_facts_posted_period_number",()->insert(db,posted));
                var original=header();original.put("original_year",2026);original.put("original_period",period);
                refused(db,"23514","ck_posting_facts_original_period_number",()->insert(db,original));
            }
            for(String half:List.of("original_year","original_period")){
                var value=header();value.put(half,half.equals("original_year")?2026:11);
                refused(db,"23514","ck_posting_facts_original_pair",()->insert(db,value));
            }
        }
    }

    @Test void sourceKeyBoundariesAndReplayScopeMatchSignedDatabaseRules()throws Exception{
        try(Connection db=ready()){
            String key=component(db);
            for(String source:List.of("a.b","synthetic.fixture","organization.payroll"))for(int length:List.of(1,200)){
                var value=header();value.put("source",source);value.put("key","K".repeat(length));balanced(db,value,key);
            }
            for(String source:List.of("organization","Organization.payroll","organization..payroll","organization_payroll",".payroll")){
                var value=header();value.put("source",source);refused(db,"23514","ck_posting_facts_source_context_code",()->insert(db,value));
            }
            for(String replay:List.of("","K".repeat(201)," key","key ")){
                var value=header();value.put("key",replay);refused(db,"23514","ck_posting_facts_idempotency_key",()->insert(db,value));
            }
            var original=header();balanced(db,original,key);
            var duplicate=new LinkedHashMap<>(original);duplicate.put("id",id());duplicate.put("source_id",id());
            refused(db,"23505","uk_posting_facts_replay_key",()->insert(db,duplicate));
            var otherEntity=new LinkedHashMap<>(duplicate);otherEntity.put("entity",id());balanced(db,otherEntity,key);
            var otherProducer=new LinkedHashMap<>(duplicate);otherProducer.put("id",id());otherProducer.put("source","synthetic.fixture");balanced(db,otherProducer,key);
        }
    }

    @Test void sequenceOwnedByHeaderColumnAndNumberIsNotAnUpdatePath()throws Exception{
        try(Connection db=ready()){
            try(var s=db.createStatement();var rows=s.executeQuery("""
                select count(*) from pg_depend d join pg_attribute a on a.attrelid=d.refobjid and a.attnum=d.refobjsubid
                where d.classid='pg_class'::regclass and d.objid='operations_facts.posting_fact_post_seq'::regclass
                  and d.refobjid='operations_facts.posting_facts'::regclass and a.attname='post_number' and d.deptype='a'
                """)){assertTrue(rows.next());assertEquals(1,rows.getLong(1));}
            String key=component(db);var value=header();balanced(db,value,key);
            refused(db,"23001",null,()->execute(db,"update operations_facts.posting_facts set post_number=post_number+100 where id=?",value.get("id")));
            try(var s=db.createStatement();var rows=s.executeQuery("""
                select count(*) from pg_constraint where conrelid='operations_facts.posting_facts'::regclass
                 and conname='uk_posting_facts_post_number' and contype='u'
                 and pg_get_constraintdef(oid)='UNIQUE (post_number)'
                """)){assertTrue(rows.next());assertEquals(1,rows.getLong(1));}
            try(var s=db.createStatement();var rows=s.executeQuery("""
                select count(*) from information_schema.columns c where c.table_schema='operations_facts' and c.is_nullable='YES'
                and coalesce(pg_catalog.col_description((pg_catalog.quote_ident(c.table_schema)||'.'||pg_catalog.quote_ident(c.table_name))::regclass,c.ordinal_position),'') not like '%为空表示%'
                """)){assertTrue(rows.next());assertEquals(0,rows.getLong(1),"each nullable signed column explains null semantics");}
        }
    }
}
