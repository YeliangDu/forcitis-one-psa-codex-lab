package com.forcitis.one.platform.approval;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synthetic organisation for tests: in-memory OrgDirectory + RoleDirectory (the contracts other modules provide). All data is
 * made up by the tests; no real person appears anywhere.
 */
public class FakeOrganisation implements OrgDirectory, RoleDirectory {

    record Person(long id, long unit, Long leader, String wecom, boolean employed) {
    }

    record Unit(long id, Long parent, Long head, Long receivablesManager, boolean active) {
    }

    private final Map<Long, Person> people = new ConcurrentHashMap<>();
    private final Map<Long, Unit> units = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> roles = new ConcurrentHashMap<>();

    // ---------- building ----------

    public FakeOrganisation unit(long id, Long parent, Long head) {
        units.put(id, new Unit(id, parent, head, null, true));
        return this;
    }

    public FakeOrganisation unit(long id, Long parent, Long head, Long receivablesManager, boolean active) {
        units.put(id, new Unit(id, parent, head, receivablesManager, active));
        return this;
    }

    public FakeOrganisation person(long id, long unit, Long leader) {
        people.put(id, new Person(id, unit, leader, "wx-" + id, true));
        return this;
    }

    public FakeOrganisation personWithoutWecom(long id, long unit, Long leader) {
        people.put(id, new Person(id, unit, leader, null, true));
        return this;
    }

    public FakeOrganisation separated(long id, long unit, Long leader) {
        people.put(id, new Person(id, unit, leader, "wx-" + id, false));
        return this;
    }

    public FakeOrganisation role(String code, long... holders) {
        Set<Long> set = roles.computeIfAbsent(code, k -> ConcurrentHashMap.newKeySet());
        for (long holder : holders) {
            set.add(holder);
        }
        return this;
    }

    public String wecomOf(long id) {
        return people.get(id).wecom();
    }

    // ---------- OrgDirectory ----------

    @Override
    public Optional<EmployeeProfile> findEmployee(UserId userId) {
        return Optional.ofNullable(people.get(userId.value())).map(p -> new EmployeeProfile(new UserId(p.id()), "person-" + p.id(),
                new OrgUnitId(p.unit()), p.wecom() == null ? null : new WecomUserId(p.wecom()),
                p.employed() ? EmploymentStatus.EMPLOYED : EmploymentStatus.SEPARATED, false));
    }

    @Override
    public Optional<EmployeeProfile> findEmployeeByWecomUserId(WecomUserId wecomUserId) {
        return people.values().stream().filter(p -> wecomUserId.value().equals(p.wecom())).findFirst()
                .flatMap(p -> findEmployee(new UserId(p.id())));
    }

    @Override
    public Optional<UserId> findDirectLeader(UserId userId) {
        return Optional.ofNullable(people.get(userId.value())).map(Person::leader)
                .filter(l -> people.containsKey(l) && people.get(l).employed()).map(UserId::new);
    }

    @Override
    public Optional<UserId> findOrgUnitHead(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(orgUnitId.value())).map(Unit::head).map(UserId::new);
    }

    @Override
    public Optional<UserId> findRegionalReceivablesManager(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(orgUnitId.value())).map(Unit::receivablesManager).map(UserId::new);
    }

    @Override
    public Optional<OrgUnitProfile> findOrgUnit(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(orgUnitId.value())).map(u -> new OrgUnitProfile(new OrgUnitId(u.id()), "unit-" + u.id(),
                u.parent() == null ? null : new OrgUnitId(u.parent()), u.active()));
    }

    @Override
    public List<OrgUnitId> findAncestors(OrgUnitId orgUnitId) {
        List<OrgUnitId> ancestors = new ArrayList<>();
        Set<Long> seen = new HashSet<>(Set.of(orgUnitId.value()));
        Unit current = units.get(orgUnitId.value());
        while (current != null && current.parent() != null && seen.add(current.parent())) {
            ancestors.add(new OrgUnitId(current.parent()));
            current = units.get(current.parent());
        }
        return ancestors;
    }

    // ---------- RoleDirectory ----------

    @Override
    public Set<UserId> findRoleHolders(RoleCode roleCode) {
        Set<UserId> holders = new LinkedHashSet<>();
        for (Long holder : roles.getOrDefault(roleCode.value(), Set.of())) {
            Person person = people.get(holder);
            if (person != null && person.employed()) {
                holders.add(new UserId(holder));
            }
        }
        return holders;
    }
}
