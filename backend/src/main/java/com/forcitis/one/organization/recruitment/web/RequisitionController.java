package com.forcitis.one.organization.recruitment.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.recruitment.api.RecruitmentError;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.application.CloseRequisition;
import com.forcitis.one.organization.recruitment.application.GetRequisition;
import com.forcitis.one.organization.recruitment.application.ListRequisitions;
import com.forcitis.one.organization.recruitment.application.RequisitionView;
import com.forcitis.one.organization.recruitment.application.SubmitRequisition;
import com.forcitis.one.organization.recruitment.application.WithdrawRequisition;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/recruitment-requisitions")
class RequisitionController {
    private final SubmitRequisition submit;
    private final WithdrawRequisition withdraw;
    private final CloseRequisition close;
    private final GetRequisition get;
    private final ListRequisitions list;

    RequisitionController(SubmitRequisition submit, WithdrawRequisition withdraw, CloseRequisition close, GetRequisition get, ListRequisitions list) {
        this.submit = submit;
        this.withdraw = withdraw;
        this.close = close;
        this.get = get;
        this.list = list;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + RecruitmentPermissions.SUBMIT + "')")
    ResponseEntity<RequisitionResponse> submit(@RequestBody SubmitRequisitionRequest request) {
        RequisitionView view = submit.execute(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/organization/recruitment-requisitions/" + view.requisitionId().value()))
                .eTag(ExpectedVersion.etag(view.version())).body(RequisitionResponse.of(view));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + RecruitmentPermissions.READ + "')")
    CursorPage<RequisitionResponse> list(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<RequisitionView> page = list.execute(PageRequest.of(limit, cursor, "recruitment-requisitions"));
        return CursorPage.of(page.items().stream().map(RequisitionResponse::of).toList(), page.nextCursor());
    }

    @GetMapping(path = "/{requisitionId}")
    @PreAuthorize("hasAuthority('" + RecruitmentPermissions.READ + "')")
    ResponseEntity<RequisitionResponse> get(@PathVariable UUID requisitionId) {
        RequisitionView view = get.execute(new RequisitionId(requisitionId))
                .orElseThrow(() -> RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND.refusal("No such requisition"));
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(RequisitionResponse.of(view));
    }

    @PostMapping(path = "/{requisitionId}:withdraw")
    @PreAuthorize("hasAuthority('" + RecruitmentPermissions.SUBMIT + "')")
    ResponseEntity<RequisitionResponse> withdraw(@PathVariable UUID requisitionId, @RequestBody WithdrawRequisitionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        RequisitionView view = withdraw.execute(new RequisitionId(requisitionId), request.requiredReason(), ExpectedVersion.fromHeader(ifMatch));
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(RequisitionResponse.of(view));
    }

    @PostMapping(path = "/{requisitionId}:close")
    @PreAuthorize("hasAuthority('" + RecruitmentPermissions.UPDATE + "')")
    ResponseEntity<RequisitionResponse> close(@PathVariable UUID requisitionId, @RequestBody CloseRequisitionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Optional<ExpectedVersion> expected = ifMatch == null ? Optional.empty() : Optional.of(ExpectedVersion.fromHeader(ifMatch));
        RequisitionView view = close.execute(new RequisitionId(requisitionId), request.required(), expected);
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(RequisitionResponse.of(view));
    }
}
