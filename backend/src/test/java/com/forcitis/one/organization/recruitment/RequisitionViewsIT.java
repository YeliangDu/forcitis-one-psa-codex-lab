package com.forcitis.one.organization.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.application.ListRequestedRequisitions;
import com.forcitis.one.organization.recruitment.application.ListApprovableRequisitions;
import com.forcitis.one.organization.recruitment.application.RequisitionView;
import com.forcitis.one.organization.recruitment.application.SubmitRequisition;
import com.forcitis.one.organization.recruitment.application.SubmitRequisitionCommand;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.PositionLookup;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.ListInbox;
import com.forcitis.one.platform.approval.application.ListPage;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.identity.api.Permission;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** #1452 R3: the requested and approvable views of requisitions, with cursor paging, over the use cases and over HTTP. */
class RequisitionViewsIT extends ApprovalIntegrationSupport {

    private static final AtomicLong UNITS = new AtomicLong(710_000);
    private static final String PATH = "/api/v1/organization/recruitment-requisitions";

    @MockitoBean
    PositionLookup positions;

    @MockitoBean
    OrgUnitAccess access;

    @Autowired
    SubmitRequisition submit;

    @Autowired
    ListRequestedRequisitions requested;

    @Autowired
    ListApprovableRequisitions approvable;

    @Autowired
    ClaimTask claimTask;

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ListInbox listInbox;

    long requester;
    long other;
    long head;
    long unit;
    PositionId position = TestOrgIds.position(1);

    @BeforeEach
    void people() {
        requester = newId();
        other = newId();
        head = newId();
        unit = UNITS.incrementAndGet();
        organisation.unit(unit, null, head).person(head, unit, null).person(requester, unit, head).person(other, unit, head);
        when(positions.isActive(any())).thenReturn(true);
        when(access.visibleOrgUnits(any())).thenReturn(OrgUnitAccess.Visible.all());
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    void signInAs(long person) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(TestAccounts.account(person).value().toString(), "x", "ROLE_TEST"));
    }

    RequisitionView submitAs(long person) {
        signInAs(person);
        OrgUnitId orgUnit = TestOrgIds.orgUnit(unit);
        return submit.execute(new SubmitRequisitionCommand(new RequisitionId(UUID.randomUUID()), position, orgUnit, 1, LocalDate.of(2027, 1, 4), "analyst"));
    }

    List<RequisitionId> ids(CursorPage<RequisitionView> page) {
        return page.items().stream().map(RequisitionView::requisitionId).toList();
    }

    /** An ordinary session (ROLE_ORDINARY_SESSION) holding the given permission codes. */
    RequestPostProcessor as(long person, String... permissions) {
        return SecurityMockMvcRequestPostProcessors.user(TestAccounts.account(person).value().toString())
                .authorities(Stream.concat(Stream.of("ROLE_ORDINARY_SESSION"), Arrays.stream(permissions)).map(SimpleGrantedAuthority::new).toList());
    }

    /** An administrator-mode session: platform codes only, no ordinary-session role (I-11). */
    RequestPostProcessor asAdministrator(long person) {
        return SecurityMockMvcRequestPostProcessors.user(TestAccounts.account(person).value().toString())
                .authorities(new SimpleGrantedAuthority(Permission.Codes.PLATFORM_IDENTITY_ROLE_READ));
    }

    @Test
    @DisplayName("#1452 requested: lists only what the caller submitted, newest first, and an empty org-unit scope does not hide it")
    void requestedIsTheCallersOwn() {
        RequisitionView first = submitAs(requester);
        RequisitionView second = submitAs(requester);
        RequisitionView foreign = submitAs(other);

        when(access.visibleOrgUnits(any())).thenReturn(OrgUnitAccess.Visible.nothing());
        signInAs(requester);
        assertThat(ids(requested.execute(PageRequest.of(50, null, "recruitment-requisitions-requested")))).containsExactly(second.requisitionId(), first.requisitionId());
        signInAs(other);
        assertThat(ids(requested.execute(PageRequest.of(50, null, "recruitment-requisitions-requested")))).containsExactly(foreign.requisitionId());
        signInAs(head);
        assertThat(requested.execute(PageRequest.of(50, null, "recruitment-requisitions-requested")).items()).isEmpty();
    }

    @Test
    @DisplayName("#1452 requested: the cursor continues after the last row without repeating or skipping one")
    void requestedKeepsPagesApart() {
        RequisitionView a = submitAs(requester);
        RequisitionView b = submitAs(requester);
        RequisitionView c = submitAs(requester);
        signInAs(requester);

        CursorPage<RequisitionView> one = requested.execute(PageRequest.of(2, null, "recruitment-requisitions-requested"));
        assertThat(ids(one)).containsExactly(c.requisitionId(), b.requisitionId());
        assertThat(one.nextCursor()).isNotNull();
        CursorPage<RequisitionView> two = requested.execute(PageRequest.of(2, one.nextCursor(), "recruitment-requisitions-requested"));
        assertThat(ids(two)).containsExactly(a.requisitionId());
        assertThat(two.nextCursor()).isNull();
    }

    @Test
    @DisplayName("#1452 approvable: the head holding the open task sees it; the requester, a stranger and the head after deciding do not")
    void approvableIsTheOpenTask() {
        RequisitionView view = submitAs(requester);
        RequisitionView otherView = submitAs(other);

        signInAs(head);
        assertThat(ids(approvable.execute(PageRequest.of(50, null, "recruitment-requisitions-approvable")))).containsExactly(otherView.requisitionId(), view.requisitionId());
        signInAs(requester);
        assertThat(approvable.execute(PageRequest.of(50, null, "recruitment-requisitions-approvable")).items()).isEmpty();

        ApprovalTaskId task = listInbox.execute(TestAccounts.account(head), ListPage.first()).items().stream()
                .filter(item -> item.documentId().filter(view.requisitionId().value()::equals).isPresent())
                .map(item -> new ApprovalTaskId(UUID.fromString(item.taskId()))).findFirst().orElseThrow();
        claimTask.execute(task, TestAccounts.account(head), OptionalLong.empty());
        decideTasks.execute(TestAccounts.account(head), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, null)), OptionalLong.empty());

        signInAs(head);
        assertThat(ids(approvable.execute(PageRequest.of(50, null, "recruitment-requisitions-approvable")))).containsExactly(otherView.requisitionId());
    }

    @Test
    @DisplayName("#1452 approvable: pages continue after the cursor")
    void approvableKeepsPagesApart() {
        RequisitionView a = submitAs(requester);
        RequisitionView b = submitAs(requester);
        signInAs(head);

        CursorPage<RequisitionView> one = approvable.execute(PageRequest.of(1, null, "recruitment-requisitions-approvable"));
        assertThat(ids(one)).containsExactly(b.requisitionId());
        CursorPage<RequisitionView> two = approvable.execute(PageRequest.of(1, one.nextCursor(), "recruitment-requisitions-approvable"));
        assertThat(ids(two)).containsExactly(a.requisitionId());
        assertThat(two.nextCursor()).isNull();
    }

    @Test
    @DisplayName("#1452 over HTTP: view=requested needs the submit permission and shows the caller's rows; view=approvable answers the head's task; a plain reader sees neither of the other's rows")
    void overHttp() throws Exception {
        RequisitionView view = submitAs(requester);
        String submitCode = Permission.Codes.ORGANIZATION_RECRUITMENT_REQUISITION_SUBMIT;
        String readCode = Permission.Codes.ORGANIZATION_RECRUITMENT_REQUISITION_READ;

        mvc.perform(get(PATH + "?view=requested").with(as(requester))).andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(as(requester, submitCode))).andExpect(status().isForbidden());
        mvc.perform(get(PATH + "?view=unknown").with(as(requester, readCode, submitCode))).andExpect(status().isForbidden());
        mvc.perform(get(PATH + "?view=requested").with(as(requester, submitCode))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].requisitionId").value(view.requisitionId().value().toString()));
        mvc.perform(get(PATH + "?view=requested").with(as(other, submitCode))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(get(PATH + "?view=approvable").with(as(head))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].requisitionId").value(view.requisitionId().value().toString()));
        mvc.perform(get(PATH + "?view=approvable").with(as(requester))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(PATH + "?view=approvable")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "?view=approvable").with(asAdministrator(head))).andExpect(status().isForbidden());

        when(access.visibleOrgUnits(any())).thenReturn(OrgUnitAccess.Visible.nothing());
        mvc.perform(get(PATH).with(as(other, readCode))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(PATH + "/" + view.requisitionId().value()).with(as(other, readCode))).andExpect(status().isNotFound());
    }
}
