package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.Permission;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Assigning roles and switching an account on or off use the role update permission (no separate account permission yet). */
@RestController
@RequestMapping("/api/v1/platform")
class RoleController {

    private static final String READ = "hasAuthority('" + Permission.Codes.PLATFORM_IDENTITY_ROLE_READ + "')";
    private static final String UPDATE = "hasAuthority('" + Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE + "')";

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
    ResponseEntity<RoleResponse> get(@PathVariable long roleId) {
        RoleView role = getRole.execute(roleId)
                .orElseThrow(() -> IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND.refusal("no role " + roleId));
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(role.version())).body(RoleResponse.of(role));
    }

    @PostMapping(path = "/roles")
    @PreAuthorize(UPDATE)
    ResponseEntity<RoleResponse> create(@RequestBody RoleRequest request) {
        RoleView role = createRole.execute(command(request));
        return ResponseEntity.created(URI.create("/api/v1/platform/roles/" + role.roleId()))
                .eTag(ExpectedVersion.etag(role.version())).body(RoleResponse.of(role));
    }

    @PutMapping(path = "/roles/{roleId}")
    @PreAuthorize(UPDATE)
    ResponseEntity<RoleResponse> update(@PathVariable long roleId, @RequestBody RoleRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        RoleView role = updateRole.execute(roleId, ExpectedVersion.fromHeader(ifMatch), command(request));
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(role.version())).body(RoleResponse.of(role));
    }

    @DeleteMapping(path = "/roles/{roleId}")
    @PreAuthorize(UPDATE)
    ResponseEntity<Void> delete(@PathVariable long roleId, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        deleteRole.execute(roleId, ExpectedVersion.fromHeader(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/user-accounts/{userId}:replace-roles")
    @PreAuthorize(UPDATE)
    ResponseEntity<Void> replaceRoles(@PathVariable long userId, @RequestBody ReplaceRolesRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Set<String> codes = request.roleCodes() == null ? Set.of() : request.roleCodes();
        long version = replaceAccountRoles.execute(userId, codes, ExpectedVersion.fromHeader(ifMatch));
        return ResponseEntity.noContent().eTag(ExpectedVersion.etag(version)).build();
    }

    @GetMapping(path = "/user-accounts/{userId}")
    @PreAuthorize(READ)
    ResponseEntity<UserAccountResponse> account(@PathVariable long userId) {
        UserAccountView view = getUserAccount.execute(userId);
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(UserAccountResponse.of(view));
    }

    @PostMapping(path = "/user-accounts/{userId}:enable")
    @PreAuthorize(UPDATE)
    ResponseEntity<Void> enable(@PathVariable long userId, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        long version = changeAccountStatus.execute(userId, true, ExpectedVersion.fromHeader(ifMatch));
        return ResponseEntity.noContent().eTag(ExpectedVersion.etag(version)).build();
    }

    @PostMapping(path = "/user-accounts/{userId}:disable")
    @PreAuthorize(UPDATE)
    ResponseEntity<Void> disable(@PathVariable long userId, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        long version = changeAccountStatus.execute(userId, false, ExpectedVersion.fromHeader(ifMatch));
        return ResponseEntity.noContent().eTag(ExpectedVersion.etag(version)).build();
    }

    private static SaveRoleCommand command(RoleRequest request) {
        return new SaveRoleCommand(request.code(), request.name(), request.enabled() == null || request.enabled(), request.accessScope(), request.permissionCodes());
    }
}
