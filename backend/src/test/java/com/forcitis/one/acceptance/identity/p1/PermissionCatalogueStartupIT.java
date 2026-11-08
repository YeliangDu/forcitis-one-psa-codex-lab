package com.forcitis.one.acceptance.identity.p1;

import static com.forcitis.one.acceptance.identity.p1.PermissionP1Fixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Actual hosted Spring collection, not a manually supplied workforce contribution. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class PermissionCatalogueStartupIT {
    static final TestDatabase postgres = TestPostgres.database(PermissionCatalogueStartupIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { postgres.register(registry); }

    @MockitoBean WecomGateway wecomGateway;
    @Autowired ApplicationContext context;
    @Autowired JdbcClient jdbc;

    @Test
    void realStartupReconcilesExactlyTheFrozenSystemRoleRevisionsAndScopes() throws Exception {
        type(CATALOGUE);
        var stored = jdbc.sql("""
                select r.code, r.role_id, r.is_administrative, v.role_name, v.access_scope_kind,
                       v.declaration_revision_number, v.permission_codes
                from platform_identity.roles r join platform_identity.role_versions v on v.role_id = r.role_id
                where r.kind = 'SYSTEM' and v.effective_to_at is null
                """).query((rs, number) -> String.join("\t", rs.getString("code"), rs.getString("role_id"),
                        rs.getString("role_name"), rs.getString("access_scope_kind"),
                        Boolean.toString(rs.getBoolean("is_administrative")), rs.getString("declaration_revision_number"),
                        String.join(",", new java.util.TreeSet<>(java.util.Arrays.asList((String[]) rs.getArray("permission_codes").getArray())))).stripTrailing()).list();
        assertThat(stored).containsExactlyInAnyOrderElementsOf(lines("roles.tsv"));
    }

    @Test
    void actualSpringContributionAndPlainGuardScanProduceTheSameCompleteMixedCatalogue() throws Exception {
        Class<?> catalogueType = type(CATALOGUE);
        var beans = context.getBeansOfType(catalogueType);
        assertThat(beans).as("real validated startup catalogue").hasSize(1);
        Object runtime = beans.values().iterator().next();
        Object guard = invoke(catalogueType, "fromClasspath", new Class<?>[0]);
        assertThat(snapshot(runtime)).isEqualTo(frozen());
        assertThat(snapshot(guard)).isEqualTo(snapshot(runtime));
        assertThat(twoPerson(snapshot(runtime))).isEqualTo(SENSITIVE);
        assertThat(PermissionCatalogueP1Test.roleSnapshot()).containsExactlyInAnyOrderElementsOf(lines("roles.tsv"));
    }

    @Test
    void workforceContributionReallyExistsInHostedContextIncludingAllRetiredEntries() throws Exception {
        Class<?> contributionType = type(KERNEL + "PermissionContribution");
        var contributions = context.getBeansOfType(contributionType);
        assertThat(contributions).as("actual contribution discovery").isNotEmpty();
        int workforce = 0;
        for (Object contribution : contributions.values()) {
            if (!"organization.workforce".equals(invoke(contribution, "owner", new Class<?>[0]))) continue;
            workforce++;
            Object entries = invoke(contribution, "permissions", new Class<?>[0]);
            assertThat(entries).isInstanceOf(List.class);
            var actual = snapshot(assemble(List.of(), List.of(contribution),
                    SENSITIVE.stream().filter(code -> code.startsWith("organization.workforce:")).collect(java.util.stream.Collectors.toSet()),
                    java.util.Set.of(), java.util.Set.of(), java.util.Set.of()));
            var expected = new java.util.TreeMap<String, String>();
            frozen().forEach((code, metadata) -> { if (code.startsWith("organization.workforce:")) expected.put(code, metadata); });
            assertThat(actual).isEqualTo(expected);
        }
        assertThat(workforce).as("one owning workforce contribution").isEqualTo(1);
    }
}
