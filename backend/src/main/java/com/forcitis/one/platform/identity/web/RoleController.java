package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.application.AdminOutcome;
import com.forcitis.one.platform.identity.application.ChangeAccountStatus;
import com.forcitis.one.platform.identity.application.CreateRole;
import com.forcitis.one.platform.identity.application.DeleteRole;
import com.forcitis.one.platform.identity.application.GetRole;
import com.forcitis.one.platform.identity.application.IdentityError;
import com.forcitis.one.platform.identity.application.ListRoles;
import com.forcitis.one.platform.identity.application.ReplaceAccountRoles;
import com.forcitis.one.platform.identity.application.RoleView;
import com.forcitis.one.platform.identity.application.SaveRoleCommand;
import com.forcitis.one.platform.identity.application.UpdateRole;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Assigning roles and switching an account on or off use the role update permission (no separate account permission yet). */
@RestController
@RequestMapping("/api/{version}/platform")
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

    RoleController(ListRoles listRoles, GetRole getRole, CreateRole createRole, UpdateRole updateRole, DeleteRole deleteRole,
            ReplaceAccountRoles replaceAccountRoles, ChangeAccountStatus changeAccountStatus) {
        this.listRoles = listRoles;
        this.getRole = getRole;
        this.createRole = createRole;
        this.updateRole = updateRole;
        this.deleteRole = deleteRole;
        this.replaceAccountRoles = replaceAccountRoles;
        this.changeAccountStatus = changeAccountStatus;
    }

    @GetMapping(path = "/roles", version = "1")
    @PreAuthorize(READ)
    List<RoleResponse> list() {
        return listRoles.execute().stream().map(RoleResponse::of).toList();
    }

    @GetMapping(path = "/roles/{roleId}", version = "1")
    @PreAuthorize(READ)
    ResponseEntity<?> get(@PathVariable long roleId) {
        return getRole.execute(roleId).<ResponseEntity<?>>map(role -> ResponseEntity.ok(RoleResponse.of(role)))
                .orElseGet(() -> IdentityResponses.error(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND, null));
    }

    @PostMapping(path = "/roles", version = "1")
    @PreAuthorize(UPDATE)
    ResponseEntity<?> create(@RequestBody RoleRequest request) {
        AdminOutcome<RoleView> outcome = createRole.execute(command(request));
        return switch (outcome) {
            case AdminOutcome.Done<RoleView> done -> ResponseEntity
                    .created(URI.create("/api/v1/platform/roles/" + done.value().roleId())).body(RoleResponse.of(done.value()));
            case AdminOutcome.Refused<RoleView> refused -> IdentityResponses.error(refused.error(), null);
        };
    }

    @PutMapping(path = "/roles/{roleId}", version = "1")
    @PreAuthorize(UPDATE)
    ResponseEntity<?> update(@PathVariable long roleId, @RequestBody RoleRequest request) {
        AdminOutcome<RoleView> outcome = updateRole.execute(roleId, command(request));
        return switch (outcome) {
            case AdminOutcome.Done<RoleView> done -> ResponseEntity.ok(RoleResponse.of(done.value()));
            case AdminOutcome.Refused<RoleView> refused -> IdentityResponses.error(refused.error(), null);
        };
    }

    @DeleteMapping(path = "/roles/{roleId}", version = "1")
    @PreAuthorize(UPDATE)
    ResponseEntity<?> delete(@PathVariable long roleId) {
        return noContent(deleteRole.execute(roleId));
    }

    @PostMapping(path = "/user-accounts/{userId}:replace-roles", version = "1")
    @PreAuthorize(UPDATE)
    ResponseEntity<?> replaceRoles(@PathVariable long userId, @RequestBody ReplaceRolesRequest request) {
        Set<String> codes = request.roleCodes() == null ? Set.of() : request.roleCodes();
        return noContent(replaceAccountRoles.execute(userId, codes));
    }

    @PostMapping(path = "/user-accounts/{userId}:enable", version = "1")
    @PreAuthorize(UPDATE)
    ResponseEntity<?> enable(@PathVariable long userId) {
        return noContent(changeAccountStatus.execute(userId, true));
    }

    @PostMapping(path = "/user-accounts/{userId}:disable", version = "1")
    @PreAuthorize(UPDATE)
    ResponseEntity<?> disable(@PathVariable long userId) {
        return noContent(changeAccountStatus.execute(userId, false));
    }

    private static SaveRoleCommand command(RoleRequest request) {
        return new SaveRoleCommand(request.code(), request.name(), request.enabled() == null || request.enabled(), request.accessScope(), request.permissionCodes());
    }

    private static ResponseEntity<?> noContent(AdminOutcome<Void> outcome) {
        return switch (outcome) {
            case AdminOutcome.Done<Void> done -> ResponseEntity.noContent().build();
            case AdminOutcome.Refused<Void> refused -> IdentityResponses.error(refused.error(), null);
        };
    }
}
