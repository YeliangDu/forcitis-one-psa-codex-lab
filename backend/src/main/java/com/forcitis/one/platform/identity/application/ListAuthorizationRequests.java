package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeLabel;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** user_accounts and roles are GLOBAL (ADR-0026), so naming the applicant, the subject and the roles adds nothing the permission to read the requests does not already give. */
@Service
public class ListAuthorizationRequests {

    private final AuthorizationRequestRepository requests;
    private final AccountRepository accounts;
    private final EmployeeLabels labels;
    private final RoleRepository roles;

    ListAuthorizationRequests(AuthorizationRequestRepository requests, AccountRepository accounts, EmployeeLabels labels, RoleRepository roles) {
        this.requests = requests;
        this.accounts = accounts;
        this.labels = labels;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public List<AuthorizationRequestView> execute() {
        List<AuthorizationRequest> pending = requests.findPending();
        Map<UserId, EmployeeId> employeeOf = new HashMap<>();
        for (UserId user : usersOf(pending)) {
            accounts.findEmployee(user).ifPresent(employee -> employeeOf.put(user, employee));
        }
        Map<EmployeeId, EmployeeLabel> named = labels.labelsOf(new HashSet<>(employeeOf.values()));
        Map<String, String> roleNames = new HashMap<>();
        for (Role role : roles.findAll()) {
            roleNames.put(role.code().value(), role.name());
        }
        return pending.stream().map(request -> AuthorizationRequestView.of(request, nameOf(request.requestedBy(), employeeOf, named),
                request.subjectUser() == null ? null : nameOf(request.subjectUser(), employeeOf, named),
                request.roleCodes().stream().sorted().map(code -> new AuthorizationRequestView.RoleLabel(code, roleNames.get(code))).toList())).toList();
    }

    private static Set<UserId> usersOf(List<AuthorizationRequest> pending) {
        Set<UserId> users = new HashSet<>();
        for (AuthorizationRequest request : pending) {
            users.add(request.requestedBy());
            if (request.subjectUser() != null) {
                users.add(request.subjectUser());
            }
        }
        return users;
    }

    private static @Nullable String nameOf(UserId user, Map<UserId, EmployeeId> employeeOf, Map<EmployeeId, EmployeeLabel> named) {
        EmployeeId employee = employeeOf.get(user);
        EmployeeLabel label = employee == null ? null : named.get(employee);
        return label == null ? null : label.displayName();
    }
}
