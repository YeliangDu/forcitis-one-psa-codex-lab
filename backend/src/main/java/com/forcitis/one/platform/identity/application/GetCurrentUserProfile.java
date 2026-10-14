package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.NavigationNode;
import com.forcitis.one.platform.identity.domain.NavigationRepository;
import com.forcitis.one.platform.identity.domain.NavigationTree;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCurrentUserProfile {

    private final CurrentUser currentUser;
    private final OrgDirectory directory;
    private final AccountRepository accounts;
    private final NavigationRepository navigation;
    private final SessionContext session;

    GetCurrentUserProfile(CurrentUser currentUser, OrgDirectory directory, AccountRepository accounts,
            NavigationRepository navigation, SessionContext session) {
        this.session = session;
        this.currentUser = currentUser;
        this.directory = directory;
        this.accounts = accounts;
        this.navigation = navigation;
    }

    @Transactional(readOnly = true)
    public CurrentUserView execute() {
        UserId userId = currentUser.userId();
        Optional<EmployeeProfile> employee = directory.findEmployee(userId);
        Optional<OrgUnitProfile> unit = employee.flatMap(e -> directory.findOrgUnit(e.orgUnitId()));
        AccessProfile profile = AccessProfile.of(accounts.findEnabledRoles(userId));
        SessionMode mode = session.mode();
        Set<String> permissions = mode == SessionMode.ADMINISTRATOR ? profile.administratorModePermissionCodes() : profile.permissionCodes();
        List<NavigationEntry> menu = NavigationTree.visibleTo(permissions, navigation.findEnabled())
                .stream().map(GetCurrentUserProfile::entry).toList();
        return new CurrentUserView(userId.value(), employee.map(EmployeeProfile::name).orElse(""),
                employee.map(e -> e.orgUnitId().value()).orElse(null), unit.map(OrgUnitProfile::name).orElse(""),
                permissions.stream().sorted().toList(), menu, mode, session.administratorModeExpiresAt().orElse(null));
    }

    private static NavigationEntry entry(NavigationNode node) {
        return new NavigationEntry(node.name(), node.routePath(), node.children().stream().map(GetCurrentUserProfile::entry).toList());
    }
}
