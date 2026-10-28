package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.application.AdminOutcome;
import com.forcitis.one.platform.identity.application.ChangeAccountStatus;
import com.forcitis.one.platform.identity.application.CreateRole;
import com.forcitis.one.platform.identity.application.DeleteRole;
import com.forcitis.one.platform.identity.application.GetRole;
import com.forcitis.one.platform.identity.application.GetUserAccount;
import com.forcitis.one.platform.identity.application.UserAccountView;
import com.forcitis.one.platform.identity.application.ListRoles;
import com.forcitis.one.platform.identity.application.ReplaceAccountRoles;
import com.forcitis.one.platform.identity.application.RoleView;
import com.forcitis.one.platform.identity.application.SaveRoleCommand;
import com.forcitis.one.platform.identity.application.UpdateRole;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Switching an account on or off still uses the role update permission; assigning roles has its own (ADR-0026 section 6). */
@RestController
@RequestMapping("/api/v1/platform")
class RoleController {

    private static final String READ = "hasAuthority('" + Permission.Codes.PLATFORM_IDENTITY_ROLE_READ + "')";
    private static final String UPDATE = "hasAuthority('" + Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE + "')";
    private static final String UPDATE_ACCOUNT_ROLES = "hasAuthority('" + Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE + "')";

    private final ListRoles listRoles;
    private final GetRole getRole;
    private final CreateRole createRole;
    private final UpdateRole updateRole;
    private final DeleteRole deleteRole;
    private final ReplaceAccountRoles replaceAccountRoles;
    private final ChangeAccountStatus changeAccountStatus;
    private final GetUserAccount getUserAccount;

    RoleController(ListRoles listRoles, GetRole getRole, CreateRole createRole, UpdateRole updateRole, DeleteRole deleteRole,
            ReplaceAccountRoles replaceAccountRoles, ChangeAccountStatus changeAccountStatus, GetUserAccount getUserAccount) {
        this.getUserAccount = getUserAccount;
        this.listRoles = listRoles;
        this.getRole = getRole;
        this.createRole = createRole;
        this.updateRole = updateRole;
        this.deleteRole = deleteRole;
        this.replaceAccountRoles = replaceAccountRoles;
        this.changeAccountStatus = changeAccountStatus;
    }

    @GetMapping(path = "/roles")
    @PreAuthorize(READ)
    CursorPage<RoleResponse> list(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<RoleView> page = listRoles.execute(PageRequest.of(limit, cursor, "roles"));
        return CursorPage.of(page.items().stream().map(RoleResponse::of).toList(), page.nextCursor());
    }

    @GetMapping(path = "/roles/{roleId}")
    @PreAuthorize(READ)
    ResponseEntity<RoleResponse> get(@PathVariable UUID roleId) {
        RoleView role = getRole.execute(new RoleId(roleId))
                .orElseThrow(() -> IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND.refusal("no role " + roleId));
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(role.version())).body(RoleResponse.of(role));
    }

    @PostMapping(path = "/roles")
    @PreAuthorize(UPDATE)
    ResponseEntity<?> create(@RequestBody RoleRequest request) {
        return switch (createRole.execute(command(request))) {
            case AdminOutcome.Done<RoleView> done -> ResponseEntity.created(URI.create("/api/v1/platform/roles/" + done.value().roleId().value()))
                    .eTag(ExpectedVersion.etag(done.value().version())).body(RoleResponse.of(done.value()));
            case AdminOutcome.Refused<RoleView> refused -> throw refusal(refused.error());
            case AdminOutcome.Awaiting<RoleView> awaiting -> accepted(awaiting.requestId());
        };
    }

    @PutMapping(path = "/roles/{roleId}")
    @PreAuthorize(UPDATE)
    ResponseEntity<?> update(@PathVariable UUID roleId, @RequestBody RoleRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return switch (updateRole.execute(new RoleId(roleId), ExpectedVersion.fromHeader(ifMatch), command(request))) {
            case AdminOutcome.Done<RoleView> done -> ResponseEntity.ok().eTag(ExpectedVersion.etag(done.value().version()))
                    .body(RoleResponse.of(done.value()));
            case AdminOutcome.Refused<RoleView> refused -> throw refusal(refused.error());
            case AdminOutcome.Awaiting<RoleView> awaiting -> accepted(awaiting.requestId());
        };
    }

    @DeleteMapping(path = "/roles/{roleId}")
    @PreAuthorize(UPDATE)
    ResponseEntity<Void> delete(@PathVariable UUID roleId, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        if (deleteRole.execute(new RoleId(roleId), ExpectedVersion.fromHeader(ifMatch)) instanceof AdminOutcome.Refused<Void> refused) {
            throw refusal(refused.error());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/user-accounts/{userId}:replace-roles")
    @PreAuthorize(UPDATE_ACCOUNT_ROLES)
    ResponseEntity<?> replaceRoles(@PathVariable UUID userId, @RequestBody ReplaceRolesRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Set<String> codes = request.roleCodes() == null ? Set.of() : request.roleCodes();
        return switch (replaceAccountRoles.execute(AccountPath.user(userId), codes, ExpectedVersion.fromHeader(ifMatch))) {
            case AdminOutcome.Done<Long> done -> ResponseEntity.noContent().eTag(ExpectedVersion.etag(done.value())).build();
            case AdminOutcome.Refused<Long> refused -> throw refusal(refused.error());
            case AdminOutcome.Awaiting<Long> awaiting -> accepted(awaiting.requestId());
        };
    }

    @GetMapping(path = "/user-accounts/{userId}")
    @PreAuthorize(READ)
    ResponseEntity<UserAccountResponse> account(@PathVariable UUID userId) {
        UserAccountView view = getUserAccount.execute(AccountPath.user(userId));
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(UserAccountResponse.of(view));
    }

    @PostMapping(path = "/user-accounts/{userId}:enable")
    @PreAuthorize(UPDATE)
    ResponseEntity<Void> enable(@PathVariable UUID userId, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        long version = changeAccountStatus.execute(AccountPath.user(userId), true, ExpectedVersion.fromHeader(ifMatch));
        return ResponseEntity.noContent().eTag(ExpectedVersion.etag(version)).build();
    }

    @PostMapping(path = "/user-accounts/{userId}:disable")
    @PreAuthorize(UPDATE)
    ResponseEntity<Void> disable(@PathVariable UUID userId, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        long version = changeAccountStatus.execute(AccountPath.user(userId), false, ExpectedVersion.fromHeader(ifMatch));
        return ResponseEntity.noContent().eTag(ExpectedVersion.etag(version)).build();
    }

    private static DomainException refusal(IdentityError error) {
        return error.refusal(error.name());
    }

    private static ResponseEntity<AwaitingConfirmationResponse> accepted(String requestId) {
        return ResponseEntity.accepted().body(new AwaitingConfirmationResponse(requestId));
    }

    private static SaveRoleCommand command(RoleRequest request) {
        return new SaveRoleCommand(request.code(), request.name(), request.enabled() == null || request.enabled(), request.accessScope(), request.permissionCodes(),
                request.customOrgUnits() == null ? null : request.customOrgUnits().stream().map(CustomOrgUnitRequest::toUnit).toList());
    }
}
