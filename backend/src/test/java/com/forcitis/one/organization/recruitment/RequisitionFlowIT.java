package com.forcitis.one.organization.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import com.forcitis.one.organization.recruitment.api.RecruitmentError;
import com.forcitis.one.organization.recruitment.api.RequisitionDocument;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.api.RequisitionStatus;
import com.forcitis.one.organization.recruitment.application.CloseRequisition;
import com.forcitis.one.organization.recruitment.application.GetRequisition;
import com.forcitis.one.organization.recruitment.application.ListRequisitions;
import com.forcitis.one.organization.recruitment.application.RequisitionView;
import com.forcitis.one.organization.recruitment.application.SubmitRequisition;
import com.forcitis.one.organization.recruitment.application.SubmitRequisitionCommand;
import com.forcitis.one.organization.recruitment.application.WithdrawRequisition;
import com.forcitis.one.organization.recruitment.events.RequisitionApproved;
import com.forcitis.one.organization.recruitment.events.RequisitionClosed;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.PositionLookup;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.application.ListInbox;
import com.forcitis.one.platform.approval.application.ListPage;
import com.forcitis.one.platform.audit.api.AuditEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * #1451 R2: submit starts the approval of the unit head in the same transaction; the approval result opens or closes the requisition through the listener;
 * the requester withdraws, a manager closes; reads and submits are cut by the org-unit scope.
 */
@EnableScenarios
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.modulith.test.PublishedEventsExtension.class)
class RequisitionFlowIT extends ApprovalIntegrationSupport {

    private static final AtomicLong UNITS = new AtomicLong(700_000);

    @MockitoBean
    PositionLookup positions;

    @MockitoBean
    OrgUnitAccess access;

    @Autowired
    SubmitRequisition submit;

    @Autowired
    WithdrawRequisition withdraw;

    @Autowired
    CloseRequisition close;

    @Autowired
    GetRequisition get;

    @Autowired
    ListRequisitions list;

    @Autowired
    ClaimTask claimTask;

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ListInbox listInbox;

    @Autowired
    org.springframework.context.ApplicationEventPublisher publisher;

    long requester;
    long head;
    long unit;
    PositionId position = TestOrgIds.position(1);

    @BeforeEach
    void people() {
        requester = newId();
        head = newId();
        unit = UNITS.incrementAndGet();
        organisation.unit(unit, null, head).person(head, unit, null).person(requester, unit, head);
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

    OrgUnitId unitId() {
        return TestOrgIds.orgUnit(unit);
    }

    RequisitionView submitOne(int count) {
        signInAs(requester);
        return submit.execute(new SubmitRequisitionCommand(new RequisitionId(UUID.randomUUID()), position, unitId(), count, LocalDate.of(2027, 1, 4), "analyst"));
    }

    DocumentReference documentOf(RequisitionView view) {
        return new DocumentReference(RequisitionDocument.TYPE, new DocumentId(view.requisitionId().value()));
    }

    ApprovalTaskId headsTask(RequisitionView view) {
        return listInbox.execute(TestAccounts.account(head), ListPage.first()).items().stream()
                .filter(item -> item.documentId().filter(view.requisitionId().value()::equals).isPresent())
                .map(item -> new ApprovalTaskId(UUID.fromString(item.taskId()))).findFirst().orElseThrow();
    }

    void headDecides(ApprovalTaskId task, DecisionOutcome outcome) {
        claimTask.execute(task, TestAccounts.account(head), OptionalLong.empty());
        decideTasks.execute(TestAccounts.account(head), List.of(new TaskDecision(task, outcome, outcome == DecisionOutcome.APPROVE ? null : "no")), OptionalLong.empty());
    }

    @Test
    @DisplayName("#1451 submit: the requisition is SUBMITTED with a number, an approval of the unit head is open in the same transaction, and the answer is readable in scope")
    void submitStartsTheHeadsApproval() {
        RequisitionView view = submitOne(3);

        assertThat(view.status()).isEqualTo(RequisitionStatus.SUBMITTED);
        assertThat(view.number()).startsWith("TS-T-");
        assertThat(view.version()).isEqualTo(1L);
        assertThat(approvals.progress(com.forcitis.one.organization.recruitment.api.RequisitionApprovalProcess.KEY, documentOf(view))).isPresent();
        assertThat(headsTask(view)).as("the unit head holds the one task").isNotNull();
        signInAs(requester);
        assertThat(get.execute(view.requisitionId())).hasValueSatisfying(found -> assertThat(found.requestedCount()).isEqualTo(3));
    }

    @Test
    @DisplayName("#1451 submit again with the same id answers the stored requisition and opens no second approval")
    void submitIsIdempotentById() {
        RequisitionView first = submitOne(2);
        signInAs(requester);

        RequisitionView again = submit.execute(new SubmitRequisitionCommand(first.requisitionId(), position, unitId(), 2, LocalDate.of(2027, 1, 4), "analyst"));

        assertThat(again.number()).isEqualTo(first.number());
        assertThat(listInbox.execute(TestAccounts.account(head), ListPage.first()).items().stream()
                .filter(item -> item.documentId().filter(first.requisitionId().value()::equals).isPresent())).hasSize(1);
    }

    @Test
    @DisplayName("#1451 Q26 the count may exceed the establishment of the position: the requisition is accepted, nothing compares it with headcount")
    void headcountAboveTheEstablishmentIsAccepted() {
        assertThat(submitOne(999).status()).isEqualTo(RequisitionStatus.SUBMITTED);
    }

    @Test
    @DisplayName("#1451 submit refuses an inactive position, an unknown org unit and a count out of 1..999 with the module's own codes; nothing is written")
    void submitRefusals() {
        signInAs(requester);
        when(positions.isActive(any())).thenReturn(false);
        assertCode(() -> submit.execute(new SubmitRequisitionCommand(new RequisitionId(UUID.randomUUID()), position, unitId(), 1, null, "x")),
                RecruitmentError.ORGANIZATION_RECRUITMENT_POSITION_NOT_ACTIVE);
        when(positions.isActive(any())).thenReturn(true);
        assertCode(() -> submit.execute(new SubmitRequisitionCommand(new RequisitionId(UUID.randomUUID()), position, TestOrgIds.orgUnit(UNITS.incrementAndGet()), 1, null, "x")),
                RecruitmentError.ORGANIZATION_RECRUITMENT_ORG_UNIT_INVALID);
        assertCode(() -> submit.execute(new SubmitRequisitionCommand(new RequisitionId(UUID.randomUUID()), position, unitId(), 1000, null, "x")),
                RecruitmentError.ORGANIZATION_RECRUITMENT_COUNT_INVALID);
        assertThat(jdbc.queryForObject("select count(*) from organization_recruitment.recruitment_requisitions where org_unit_id = cast(? as uuid)", Long.class,
                unitId().value().toString())).isZero();
    }

    @Test
    @DisplayName("#1451 submitting for an org unit outside the submit scope answers ORG_UNIT_INVALID, writes a denied audit entry and no row")
    void submitOutsideScopeIsDeniedAndAudited() {
        when(access.visibleOrgUnits(any())).thenReturn(OrgUnitAccess.Visible.nothing());
        signInAs(requester);

        assertCode(() -> submit.execute(new SubmitRequisitionCommand(new RequisitionId(UUID.randomUUID()), position, unitId(), 1, null, "x")),
                RecruitmentError.ORGANIZATION_RECRUITMENT_ORG_UNIT_INVALID);

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit, org.mockito.Mockito.atLeastOnce()).recordIndependently(entry.capture());
        assertThat(entry.getAllValues()).anyMatch(e -> e.action().equals("organization.recruitment.requisition.denied"));
        assertThat(jdbc.queryForObject("select count(*) from organization_recruitment.recruitment_requisitions where org_unit_id = cast(? as uuid)", Long.class,
                unitId().value().toString())).isZero();
    }

    @Test
    @DisplayName("#1451 reads: out of scope answers empty (404) and the list shows nothing for an empty scope; a subtree grant on an ancestor covers the unit")
    void readsFollowTheScope() {
        RequisitionView view = submitOne(1);
        signInAs(requester);

        when(access.visibleOrgUnits(any())).thenReturn(OrgUnitAccess.Visible.nothing());
        assertThat(get.execute(view.requisitionId())).isEmpty();
        assertThat(list.execute(PageRequest.of(50, null, "recruitment-requisitions")).items()).isEmpty();

        when(access.visibleOrgUnits(any())).thenReturn(new OrgUnitAccess.Visible(false, Set.of(unitId().value()), Set.of()));
        assertThat(get.execute(view.requisitionId())).isPresent();
        assertThat(list.execute(PageRequest.of(50, null, "recruitment-requisitions")).items()).extracting(RequisitionView::requisitionId).contains(view.requisitionId());

        long child = UNITS.incrementAndGet();
        organisation.unit(child, unit, head);
        when(access.visibleOrgUnits(any())).thenReturn(new OrgUnitAccess.Visible(false, Set.of(), Set.of(TestOrgIds.orgUnit(child).value())));
        assertThat(get.execute(view.requisitionId())).as("a subtree below the requisition's unit does not cover it").isEmpty();
    }

    @Test
    @DisplayName("#1451 a subtree grant on a parent unit covers a requisition of its child unit (get and list), and a grant on the child alone does not cover the parent")
    void aSubtreeOnAnAncestorCoversTheUnit() {
        long child = UNITS.incrementAndGet();
        organisation.unit(child, unit, head);
        signInAs(requester);
        RequisitionView view = submit.execute(new SubmitRequisitionCommand(new RequisitionId(UUID.randomUUID()), position, TestOrgIds.orgUnit(child), 1, null, "x"));
        RequisitionView parentView = submitOne(1);

        when(access.visibleOrgUnits(any())).thenReturn(new OrgUnitAccess.Visible(false, Set.of(), Set.of(unitId().value())));
        assertThat(get.execute(view.requisitionId())).as("the parent's subtree covers the child").isPresent();
        assertThat(list.execute(PageRequest.of(50, null, "recruitment-requisitions")).items()).extracting(RequisitionView::requisitionId).contains(view.requisitionId());

        when(access.visibleOrgUnits(any())).thenReturn(new OrgUnitAccess.Visible(false, Set.of(), Set.of(TestOrgIds.orgUnit(child).value())));
        assertThat(get.execute(parentView.requisitionId())).as("a subtree below does not cover the parent").isEmpty();
    }

    @Test
    @DisplayName("#1451 closing: a caller who is neither the requester nor in scope gets not-found and a denied audit entry; the requisition is untouched")
    void closeByAnOutOfScopeStrangerIsNotFound() {
        RequisitionView view = submitOne(1);
        long stranger = newId();
        organisation.person(stranger, unit, null);
        when(access.visibleOrgUnits(any())).thenReturn(OrgUnitAccess.Visible.nothing());
        signInAs(stranger);

        assertCode(() -> close.execute(view.requisitionId(), CloseReasonKind.CANCELLED, Optional.empty()), RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND);

        verify(audit, org.mockito.Mockito.atLeastOnce()).recordIndependently(org.mockito.ArgumentMatchers.argThat(e -> e.action().equals("organization.recruitment.requisition.denied")));
        assertThat(jdbc.queryForObject("select status from organization_recruitment.recruitment_requisitions where id = cast(? as uuid)", String.class,
                view.requisitionId().value().toString())).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("#1451 the same requisition id submitted by a second person answers not-found, never the first person's requisition")
    void replayByAnotherPersonIsNotFound() {
        RequisitionView view = submitOne(1);
        long other = newId();
        organisation.person(other, unit, head);
        signInAs(other);

        assertCode(() -> submit.execute(new SubmitRequisitionCommand(view.requisitionId(), position, unitId(), 1, null, "x")),
                RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND);
    }

    @Test
    @DisplayName("#1451 s11 item 1 the requisition stores the requester's legal entity of that day")
    void storesTheRequestersLegalEntity() {
        RequisitionView view = submitOne(1);

        assertThat(jdbc.queryForObject("select legal_entity_id from organization_recruitment.recruitment_requisitions where id = cast(? as uuid)", String.class,
                view.requisitionId().value().toString())).isEqualTo(LEGAL_ENTITY.value().toString());
    }

    @Test
    @DisplayName("#1451 s11 item 3 AR-11 a requester who is the unit head sends the approval to the superior, not to themselves")
    void aHeadRequesterGoesToTheSuperior() {
        long superior = newId();
        long ownUnit = UNITS.incrementAndGet();
        long headRequester = newId();
        organisation.person(superior, unit, null).unit(ownUnit, unit, headRequester).person(headRequester, ownUnit, superior);
        signInAs(headRequester);

        RequisitionView view = submit.execute(new SubmitRequisitionCommand(new RequisitionId(UUID.randomUUID()), position, TestOrgIds.orgUnit(ownUnit), 1, null, "x"));

        assertThat(listInbox.execute(TestAccounts.account(superior), ListPage.first()).items().stream()
                .filter(item -> item.documentId().filter(view.requisitionId().value()::equals).isPresent())).hasSize(1);
        assertThat(listInbox.execute(TestAccounts.account(headRequester), ListPage.first()).items().stream()
                .filter(item -> item.documentId().filter(view.requisitionId().value()::equals).isPresent())).isEmpty();
    }

    @Test
    @DisplayName("#1451 the head approves: the listener opens the requisition (APPROVED) and publishes RequisitionApproved; delivering the result again changes nothing")
    void approvalOpensTheRequisition(Scenario scenario, org.springframework.modulith.test.PublishedEvents published) {
        RequisitionView view = submitOne(2);
        ApprovalTaskId task = headsTask(view);

        scenario.stimulate(() -> headDecides(task, DecisionOutcome.APPROVE))
                .andWaitForEventOfType(RequisitionApproved.class).matching(e -> e.requisitionId().equals(view.requisitionId()))
                .toArriveAndVerify(e -> assertThat(e.orgUnitId()).isEqualTo(unitId()));

        signInAs(requester);
        RequisitionView after = get.execute(view.requisitionId()).orElseThrow();
        assertThat(after.status()).isEqualTo(RequisitionStatus.APPROVED);
        assertThat(after.approvedAt()).isNotNull();
        long version = after.version();
        com.forcitis.one.platform.approval.events.ApprovalCompleted completed = published.ofType(com.forcitis.one.platform.approval.events.ApprovalCompleted.class)
                .matching(e -> e.document().equals(documentOf(view))).iterator().next();
        tx.executeWithoutResult(s -> publisher.publishEvent(completed));
        org.awaitility.Awaitility.await().pollDelay(java.time.Duration.ofMillis(300)).atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(get.execute(view.requisitionId()).orElseThrow().version()).isEqualTo(version);
                    assertThat(published.ofType(RequisitionApproved.class).matching(e -> e.requisitionId().equals(view.requisitionId()))).as("raised once").hasSize(1);
                });
    }

    @Test
    @DisplayName("#1451 the head rejects: the requisition is CLOSED with reason REJECTED and RequisitionClosed is published")
    void rejectionClosesTheRequisition(Scenario scenario) {
        RequisitionView view = submitOne(2);
        ApprovalTaskId task = headsTask(view);

        scenario.stimulate(() -> headDecides(task, DecisionOutcome.REJECT))
                .andWaitForEventOfType(RequisitionClosed.class).matching(e -> e.requisitionId().equals(view.requisitionId()))
                .toArriveAndVerify(e -> assertThat(e.closeReasonKind()).isEqualTo(CloseReasonKind.REJECTED));

        signInAs(requester);
        assertThat(get.execute(view.requisitionId()).orElseThrow().status()).isEqualTo(RequisitionStatus.CLOSED);
    }

    @Test
    @DisplayName("#1451 the requester withdraws while in approval: the approval ends cancelled, the requisition is CLOSED as WITHDRAWN; a stranger gets not-found; a stale version is refused")
    void withdrawClosesAndCancelsTheApproval() {
        RequisitionView view = submitOne(1);

        signInAs(head);
        assertCode(() -> withdraw.execute(view.requisitionId(), "mine now", ExpectedVersion.of(view.version())), RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND);
        signInAs(requester);
        assertThatThrownBy(() -> withdraw.execute(view.requisitionId(), "changed my mind", ExpectedVersion.of(view.version() + 5)))
                .isInstanceOf(com.forcitis.one.kernel.VersionMismatchException.class);

        RequisitionView closed = withdraw.execute(view.requisitionId(), "changed my mind", ExpectedVersion.of(view.version()));

        assertThat(closed.status()).isEqualTo(RequisitionStatus.CLOSED);
        assertThat(closed.closeReason()).isEqualTo(CloseReasonKind.WITHDRAWN);
        assertThat(listInbox.execute(TestAccounts.account(head), ListPage.first()).items().stream()
                .filter(item -> item.documentId().filter(view.requisitionId().value()::equals).isPresent())).as("the head's task is gone").isEmpty();
    }

    @Test
    @DisplayName("#1451 an approved requisition is closed as CANCELLED or FILLED, repeating the same close answers the closed requisition, and a submitted one cannot be closed by hand")
    void closeAnApprovedRequisition(Scenario scenario) {
        RequisitionView view = submitOne(1);
        signInAs(requester);
        assertCode(() -> close.execute(view.requisitionId(), CloseReasonKind.FILLED, Optional.empty()), com.forcitis.one.kernel.KernelError.KERNEL_TRANSITION_ILLEGAL);
        ApprovalTaskId task = headsTask(view);
        scenario.stimulate(() -> headDecides(task, DecisionOutcome.APPROVE)).andWaitForEventOfType(RequisitionApproved.class)
                .matching(e -> e.requisitionId().equals(view.requisitionId())).toArrive();

        signInAs(requester);
        assertCode(() -> close.execute(view.requisitionId(), CloseReasonKind.REJECTED, Optional.empty()), RecruitmentError.ORGANIZATION_RECRUITMENT_CLOSE_REASON_INVALID);
        RequisitionView closed = close.execute(view.requisitionId(), CloseReasonKind.FILLED, Optional.empty());
        RequisitionView again = close.execute(view.requisitionId(), CloseReasonKind.FILLED, Optional.empty());

        assertThat(closed.closeReason()).isEqualTo(CloseReasonKind.FILLED);
        assertThat(again.version()).as("an idempotent repeat writes nothing").isEqualTo(closed.version());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asRequester(String... permissions) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(TestAccounts.account(requester).value().toString())
                .authorities(java.util.Arrays.stream(permissions).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList());
    }

    @Test
    @DisplayName("#1451 over HTTP: POST answers 201 with Location and ETag, GET answers the row, a reader outside the scope gets 404, and a caller without the permission gets 403")
    void overHttp() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"requisitionId\":\"" + id + "\",\"positionId\":\"" + position.value() + "\",\"orgUnitId\":\"" + unitId().value()
                + "\",\"requestedCount\":2,\"description\":\"analyst\"}";
        String path = "/api/v1/organization/recruitment-requisitions";
        var csrf = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).with(asRequester()).with(csrf)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .with(asRequester(com.forcitis.one.platform.identity.api.Permission.Codes.ORGANIZATION_RECRUITMENT_REQUISITION_SUBMIT)).with(csrf)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("ETag", "\"1\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", path + "/" + id));

        String read = com.forcitis.one.platform.identity.api.Permission.Codes.ORGANIZATION_RECRUITMENT_REQUISITION_READ;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path + "/" + id).with(asRequester(read)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("SUBMITTED"));
        when(access.visibleOrgUnits(any())).thenReturn(OrgUnitAccess.Visible.nothing());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path + "/" + id).with(asRequester(read)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    static void assertCode(Runnable call, com.forcitis.one.kernel.ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(expected));
    }
}
