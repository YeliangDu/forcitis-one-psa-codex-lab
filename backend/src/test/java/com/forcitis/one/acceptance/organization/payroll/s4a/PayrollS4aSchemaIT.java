package com.forcitis.one.acceptance.organization.payroll.s4a;

import static org.junit.jupiter.api.Assertions.*;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Lab #36 first signed seven-empty-table shape witness; no payroll rows/roles/formula. */
class PayrollS4aSchemaIT {
    static final TestDatabase postgres=TestPostgres.database(PayrollS4aSchemaIT.class,TestPostgres.MIGRATED);
    static final List<String> NEW=List.of("compensation_profile_versions","compensation_allowance_versions","insurance_enrolments",
        "payroll_confirmed_inputs","payroll_settlements","payroll_settlement_lines","payroll_disbursements");
    static final List<String> ORIGINAL=List.of("payroll_rule_versions","payroll_rule_rows","bonus_inputs");

    Connection ready()throws Exception{
        Connection db=DriverManager.getConnection(postgres.url(),postgres.username(),postgres.password());
        try{
            Set<String> tables=new TreeSet<>(strings(db,"select table_name from information_schema.tables where table_schema='organization_payroll' and table_type='BASE TABLE'"));
            assertTrue(tables.containsAll(NEW),"missing signed S4-A seven-table shape; absence must be an assertion, not undefined-relation error");
            Set<String> expected=new TreeSet<>(ORIGINAL);expected.addAll(NEW);assertEquals(expected,tables,"exact original three plus signed seven; no posting/filing/history additions");
            return db;
        }catch(Exception|AssertionError failure){db.close();throw failure;}
    }
    static List<String> strings(Connection db,String sql)throws Exception{
        List<String> result=new ArrayList<>();try(var s=db.createStatement();var rows=s.executeQuery(sql)){while(rows.next())result.add(rows.getString(1));}return result;
    }
    static List<String> specs(String text){return Arrays.stream(text.split(" ")).filter(s->!s.isBlank()).toList();}
    static Map<String,List<String>> signed(){return Map.of(
        "compensation_profile_versions",specs("id:uuid:NO created_at:timestamptz:NO updated_at:timestamptz:NO employment_relationship_id:uuid:NO effective_from_date:date:NO base_monthly_amount:numeric:NO base_monthly_currency:text:NO paying_legal_entity_id:uuid:NO paying_entity_reason_key:text:YES decision_reference_text:text:NO created_by_user_id:uuid:NO"),
        "compensation_allowance_versions",specs("id:uuid:NO created_at:timestamptz:NO updated_at:timestamptz:NO employment_relationship_id:uuid:NO paying_legal_entity_id:uuid:NO allowance_key:text:NO allowance_amount:numeric:NO allowance_currency:text:NO effective_from_date:date:NO effective_to_date:date:YES decision_reference_text:text:NO created_by_user_id:uuid:NO"),
        "insurance_enrolments",specs("id:uuid:NO created_at:timestamptz:NO updated_at:timestamptz:NO employment_relationship_id:uuid:NO employer_legal_entity_id:uuid:NO insurance_kind:text:NO enrolment_status:text:NO effective_from_date:date:NO decision_reference_text:text:NO created_by_user_id:uuid:NO"),
        "payroll_confirmed_inputs",specs("id:uuid:NO created_at:timestamptz:NO updated_at:timestamptz:NO employment_relationship_id:uuid:NO paying_legal_entity_id:uuid:NO payroll_month_start_date:date:NO gross_amount:numeric:NO deduction_amount:numeric:NO amount_currency:text:NO source_kind:text:NO decision_reference_text:text:NO entered_by_employee_id:uuid:NO status:text:NO voided_by_user_id:uuid:YES voided_at:timestamptz:YES"),
        "payroll_settlements",specs("id:uuid:NO created_at:timestamptz:NO updated_at:timestamptz:NO paying_legal_entity_id:uuid:NO payroll_month_start_date:date:NO status:text:NO approval_instance_id:uuid:YES calculated_by_user_id:uuid:NO total_gross_amount:numeric:NO total_deduction_amount:numeric:NO total_net_amount:numeric:NO total_currency:text:NO payment_status:text:NO version:int8:NO"),
        "payroll_settlement_lines",specs("id:uuid:NO created_at:timestamptz:NO updated_at:timestamptz:NO payroll_settlement_id:uuid:NO employment_relationship_id:uuid:NO employee_id:uuid:NO compensation_profile_version_id:uuid:NO payroll_confirmed_input_id:uuid:NO gross_amount:numeric:UNSPECIFIED deduction_amount:numeric:UNSPECIFIED net_amount:numeric:UNSPECIFIED amount_currency:text:UNSPECIFIED adjustment_of_month_start_date:date:YES"),
        "payroll_disbursements",specs("id:uuid:NO created_at:timestamptz:NO updated_at:timestamptz:NO payroll_settlement_line_id:uuid:NO disbursement_status:text:NO payment_reference_text:text:YES paid_on_date:date:YES failure_reason_key:text:YES redisbursement_of_id:uuid:YES result_idempotency_key:text:YES version:int8:NO"));}

    @Test void actualMigrationCreatesExactSignedColumnsEmptyTablesAndNoUnspecifiedValueColumns()throws Exception{
        try(Connection db=ready()){
            for(var table:signed().entrySet()){
                List<String> actual=strings(db,"select column_name||':'||udt_name||':'||is_nullable from information_schema.columns where table_schema='organization_payroll' and table_name='"+table.getKey()+"'");
                Set<String> names=new TreeSet<>();
                for(String expected:table.getValue()){
                    String[] fields=expected.split(":");names.add(fields[0]);
                    if(fields[2].equals("UNSPECIFIED"))assertTrue(actual.stream().anyMatch(row->row.startsWith(fields[0]+":"+fields[1]+":")),"signed type, unresolved explicit nullability not guessed");
                    else assertTrue(actual.contains(expected),"signed column/type/nullability: "+table.getKey()+"."+fields[0]);
                }
                assertEquals(names,new TreeSet<>(actual.stream().map(row->row.split(":")[0]).toList()),"no bases/rates/posting/deduction split columns");
                assertEquals(List.of("0"),strings(db,"select count(*)::text from organization_payroll."+table.getKey()),"fresh migration seeds no rows");
                assertEquals(List.of(),strings(db,"select c.column_name from information_schema.columns c where c.table_schema='organization_payroll' and c.table_name='"+table.getKey()+"' and pg_catalog.col_description((pg_catalog.quote_ident(c.table_schema)||'.'||pg_catalog.quote_ident(c.table_name))::regclass,c.ordinal_position) is null"),"every signed column commented");
            }
            String names="'"+String.join("','",NEW)+"'";
            assertEquals(List.of(),strings(db,"select column_name from information_schema.columns where table_schema='organization_payroll' and table_name in ("+names+") and udt_name='numeric' and (numeric_precision<>19 or numeric_scale<>2)"));
        }
    }

    @Test void originalThreeColumnShapesRemainExactAndNoCrossContextForeignKeysAppear()throws Exception{
        try(Connection db=ready()){
            var input=getClass().getResourceAsStream("/acceptance/organization/payroll/s4a/original-payroll-columns.tsv");assertNotNull(input,"frozen PY0/PY3 source snapshot");
            List<String> expected;try(var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){expected=reader.lines().sorted().toList();}
            String originals="'"+String.join("','",ORIGINAL)+"'";
            assertEquals(expected,strings(db,"select table_name||E'\\t'||column_name||E'\\t'||udt_name||E'\\t'||is_nullable from information_schema.columns where table_schema='organization_payroll' and table_name in ("+originals+") order by table_name,column_name"));
            String news="'"+String.join("','",NEW)+"'";
            assertEquals(List.of(),strings(db,"select k.conname from pg_catalog.pg_constraint k join pg_catalog.pg_class a on a.oid=k.conrelid join pg_catalog.pg_namespace na on na.oid=a.relnamespace join pg_catalog.pg_class b on b.oid=k.confrelid join pg_catalog.pg_namespace nb on nb.oid=b.relnamespace where k.contype='f' and na.nspname='organization_payroll' and a.relname in ("+news+") and (nb.nspname<>'organization_payroll' or k.confdeltype<>'r')"),"only own-schema RESTRICT FKs");
            List<String> links=strings(db,"select a.relname||':'||ca.attname||'->'||b.relname||':'||cb.attname||':'||k.confdeltype from pg_catalog.pg_constraint k join pg_catalog.pg_class a on a.oid=k.conrelid join pg_catalog.pg_namespace n on n.oid=a.relnamespace join pg_catalog.pg_class b on b.oid=k.confrelid join pg_catalog.pg_attribute ca on ca.attrelid=a.oid and ca.attnum=k.conkey[1] join pg_catalog.pg_attribute cb on cb.attrelid=b.oid and cb.attnum=k.confkey[1] where k.contype='f' and cardinality(k.conkey)=1 and n.nspname='organization_payroll' and a.relname in ("+news+")");
            assertEquals(Set.of("payroll_settlement_lines:payroll_settlement_id->payroll_settlements:id:r",
                "payroll_settlement_lines:compensation_profile_version_id->compensation_profile_versions:id:r",
                "payroll_settlement_lines:payroll_confirmed_input_id->payroll_confirmed_inputs:id:r",
                "payroll_disbursements:payroll_settlement_line_id->payroll_settlement_lines:id:r",
                "payroll_disbursements:redisbursement_of_id->payroll_disbursements:id:r"),new TreeSet<>(links),"exact signed five in-schema links");
            assertEquals(5,links.size(),"no duplicate link masquerading as complete set");
        }
    }

    @Test void signedKeysAndRequiredGuardPresenceAreObservableOnActualDatabase()throws Exception{
        try(Connection db=ready()){
            List<String> keys=strings(db,"select c.conname from pg_catalog.pg_constraint c join pg_catalog.pg_namespace n on n.oid=c.connamespace where n.nspname='organization_payroll'");
            for(String name:List.of("uk_compensation_profile_versions_relationship_from","uk_insurance_enrolments_relationship_kind_from",
                "ex_compensation_allowance_versions_period_overlap","uk_payroll_settlements_approval_instance_id",
                "uk_payroll_settlement_lines_settlement_relationship_adjustment","uk_payroll_disbursements_result_idempotency_key"))assertTrue(keys.contains(name),"signed key "+name);
            List<String> indexes=strings(db,"select indexname from pg_indexes where schemaname='organization_payroll'");
            for(String name:List.of("uk_payroll_confirmed_inputs_relationship_month","uk_payroll_settlements_entity_month","uk_payroll_disbursements_line_open"))assertTrue(indexes.contains(name),"signed partial key "+name);
            for(String table:NEW){
                assertTrue(keys.contains("pk_"+table),"signed UUID primary key");
                assertFalse(strings(db,"select tgname from pg_catalog.pg_trigger where tgrelid='organization_payroll."+table+"'::regclass and not tgisinternal").isEmpty(),"signed table-specific guard required; body/transition tested separately");
            }
            assertEquals(List.of("true"),strings(db,"select i.indnullsnotdistinct::text from pg_catalog.pg_constraint c join pg_catalog.pg_index i on i.indexrelid=c.conindid where c.conname='uk_payroll_settlement_lines_settlement_relationship_adjustment' and c.connamespace='organization_payroll'::regnamespace"));
        }
    }
}
