package com.forcitis.one.acceptance.operations.facts;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** #30 schema-stage metadata only; no SQL or product writer/grant claim. */
class PostingFactsSignedMetadataTest {
    @Test void signedRegistryHasExactStageRolesKeysRetentionAndNoImplementedWriter() throws Exception {
        Path path=Path.of("architecture/data-access-registry.yaml");String source=Files.readString(path);
        Object parsed=new Yaml().load(source);assertInstanceOf(Map.class,parsed);
        Object raw=((Map<?,?>)parsed).get("tables");assertInstanceOf(Map.class,raw);
        Map<?,?> tables=(Map<?,?>)raw;
        for(String name:List.of("posting_facts","posting_fact_lines","posting_components")) {
            Object value=tables.get("operations_facts."+name);assertNotNull(value,"missing signed F1 registry entry");
            Map<?,?> entry=assertInstanceOf(Map.class,value);
            assertEquals(name.equals("posting_components")?"CONFIG":name.equals("posting_facts")?"AGGREGATE_ROOT":"AGGREGATE_CHILD",entry.get("role"));
            assertEquals(name.equals("posting_components")?"MIGRATION_ONLY":"APPEND_ONLY",entry.get("mutability"));
            if(!name.equals("posting_components"))assertEquals("PostingFact",entry.get("aggregate"));
            Map<?,?> retention=assertInstanceOf(Map.class,entry.get("retention"));assertEquals("PERMANENT",retention.get("policy"));
            Map<?,?> lineage=assertInstanceOf(Map.class,entry.get("lineage"));assertEquals("NEW",lineage.get("source"));
            assertEquals(List.of(),entry.get("writers"),"schema-only stage has no implemented Java writer");
            List<String> key=name.equals("posting_facts")?List.of("legal_entity_id","source_context_code","idempotency_key"):
                name.equals("posting_fact_lines")?List.of("fact_id","line_number"):List.of("fact_type","component_key");
            assertEquals(key,entry.get("business_key"));
        }
        assertTrue(source.contains("posting_fact_post_seq"),"owned sequence must be named in registry comment");
        assertTrue(source.contains("posting_facts.post_number"),"sequence responsibility tied to owning header column");
    }

    @Test void onlySignedAmountAndComponentColumnsAreSensitiveNoneOtherColumnsInternalNone() throws Exception {
        Map<String,String> sensitive=new TreeMap<>();
        for(String line:Files.readAllLines(Path.of("architecture/data-classification.txt"))) {
            if(!line.startsWith("operations_facts."))continue;
            String[] fields=line.split("\\|",4);assertEquals(4,fields.length,"classification line shape");
            String column=fields[0].strip(),level=fields[1].strip(),pi=fields[2].strip();
            assertEquals("NONE",pi,"no new PI ruling in F1 metadata");
            if(column.equals("operations_facts.posting_fact_lines.line_amount")||column.equals("operations_facts.posting_fact_lines.component_key")) {
                assertNull(sensitive.put(column,level+":"+pi),"no duplicate column classification");
            } else assertEquals("INTERNAL",level,"other signed columns retain internal default");
        }
        assertEquals(Map.of("operations_facts.posting_fact_lines.line_amount","SENSITIVE:NONE",
                            "operations_facts.posting_fact_lines.component_key","SENSITIVE:NONE"),sensitive);
    }
}
