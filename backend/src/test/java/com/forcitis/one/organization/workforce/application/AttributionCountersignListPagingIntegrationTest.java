package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignQueries;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignQueries.Row;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignQueries.Visible;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** #895 leftover (a): paging of the countersign list, the cursor is the id of the last row and only counts while the caller may see that row. */
class AttributionCountersignListPagingIntegrationTest extends WorkforceIntegrationSupport {
    private static final UUID HEAD = UUID.fromString("019a0000-0000-7000-8000-00000000b103");
    private static final UUID ENTITY_A = UUID.fromString("019a0000-0000-7000-8000-00000000b1f1");
    private static final UUID ENTITY_B = UUID.fromString("019a0000-0000-7000-8000-00000000b1f2");
    private static final UUID DRAFTER = UUID.fromString("019a0000-0000-7000-8000-00000000b1a1");
    private static final LocalDate TODAY = LocalDate.parse("2026-12-01");

    @Autowired
    AttributionCountersignQueries queries;

    private final List<UUID> ownRows = new ArrayList<>();
    private UUID otherEntityRow;

    @BeforeEach
    void seed() {
        jdbc.sql("delete from organization_workforce.attribution_countersigns").update();
        jdbc.sql("delete from organization_workforce.legal_entity_versions where legal_entity_id in (:a, :b)").param("a", ENTITY_A).param("b", ENTITY_B).update();
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (:id, 'FCB-B103', 'head', 'EMPLOYED') on conflict do nothing")
                .param("id", HEAD).update();
        for (UUID entity : List.of(ENTITY_A, ENTITY_B)) {
            jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (:id, :code, 'entity', 'ACTIVE') on conflict do nothing")
                    .param("id", entity).param("code", entity.equals(ENTITY_A) ? "QZFPA" : "QZFPB").update();
        }
        jdbc.sql("insert into organization_workforce.legal_entity_versions (legal_entity_version_id, legal_entity_id, effective_from_date, revision_number, effective_to_date, code, name, status,"
                + " finance_head_employee_id) values (gen_random_uuid(), :id, date '2026-01-01', 0, null, 'QZFPA', 'entity', 'ACTIVE', :head)").param("id", ENTITY_A).param("head", HEAD).update();
        ownRows.clear();
        for (int i = 0; i < 3; i++) {
            ownRows.add(countersign(ENTITY_A, i));
        }
        otherEntityRow = countersign(ENTITY_B, 3);
    }

    /** One request of its own command and employee, due {@code index} days after today. */
    private UUID countersign(UUID entity, int index) {
        UUID employee = UUID.randomUUID();
        UUID command = UUID.randomUUID();
        UUID row = UUID.randomUUID();
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (:id, :code, 'name', 'EMPLOYED')")
                .param("id", employee).param("code", "FCB-" + employee.toString().substring(24, 32)).update();
        jdbc.sql("insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at,"
                + " target_legal_entity_id, countersign_previous_legal_entity_id, countersign_new_legal_entity_id) values (:command, :employee, 'CHANGE_LEGAL_ENTITY', :day,"
                + " 'AWAITING_ATTRIBUTION_COUNTERSIGN', now(), :drafter, now(), :entity, :previous, :entity)")
                .param("command", command).param("employee", employee).param("day", TODAY.plusDays(30)).param("drafter", DRAFTER).param("entity", entity)
                .param("previous", entity.equals(ENTITY_A) ? ENTITY_B : ENTITY_A).update();
        jdbc.sql("insert into organization_workforce.attribution_countersigns (attribution_countersign_id, personnel_command_id, employee_id, drafted_by, request_kind,"
                + " request_sequence_number, legal_entity_id, requested_at, requested_by, due_on_date, created_at, updated_at) values (:id, :command, :employee, :drafter, 'CHANGE', 1,"
                + " :entity, now(), :drafter, :due, now(), now())")
                .param("id", row).param("command", command).param("employee", employee).param("drafter", DRAFTER).param("entity", entity).param("due", TODAY.plusDays(index)).update();
        return row;
    }

    private List<Row> page(Visible who, UUID after, int limit) {
        return queries.list(who, CountersignStatus.OPEN, after, limit);
    }

    private static Visible head() {
        return new Visible(new EmployeeId(HEAD), false, TODAY);
    }

    @Test
    @DisplayName("#895 分页 (1)：limit 2 取三行，用上一页最后一行的 id 取下一页，两页合起来等于一次取完，按到期日排序，没有重复和遗漏")
    void thePagesFollowOneAnotherWithoutGapOrRepeat() {
        List<Row> all = page(head(), null, 50);
        assertThat(all).extracting(Row::id).containsExactlyElementsOf(ownRows);
        assertThat(all).extracting(Row::dueDate).isSortedAccordingTo(Comparator.naturalOrder());

        List<Row> first = page(head(), null, 2);
        List<Row> second = page(head(), first.get(1).id(), 2);
        assertThat(first).hasSize(2);
        assertThat(second).extracting(Row::id).containsExactly(ownRows.get(2));
        assertThat(page(head(), second.get(0).id(), 2)).isEmpty();
    }

    @Test
    @DisplayName("#895 分页 (2)：游标指向调用者看不到的行（别的法人的行、不存在的 id）→ 空页，不报错，也探不出那一行的位置")
    void aCursorOnARowTheCallerCannotSeeGivesAnEmptyPage() {
        assertThat(page(head(), otherEntityRow, 50)).isEmpty();
        assertThat(page(head(), UUID.randomUUID(), 50)).isEmpty();
    }
}
