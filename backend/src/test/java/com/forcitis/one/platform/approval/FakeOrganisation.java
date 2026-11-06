package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.EmployeeId;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FakeOrganisation implements OrgDirectory, RoleDirectory {

    record Person(EmployeeId id, String name, long unit, Long leader, String wecom, boolean employed) {
    }

    record Unit(long id, Long parent, Long head, Long receivablesManager, boolean active) {
    }

    private final Map<EmployeeId, Person> people = new ConcurrentHashMap<>();
    private final Map<UUID, Unit> units = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> roles = new ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicBoolean failNextLookup = new java.util.concurrent.atomic.AtomicBoolean();

    public void failOnNextLookup() {
        failNextLookup.set(true);
    }

    public FakeOrganisation unit(long id, Long parent, Long head) {
        units.put(unitKey(id), new Unit(id, parent, head, null, true));
        return this;
    }

    public FakeOrganisation unit(long id, Long parent, Long head, Long receivablesManager, boolean active) {
        units.put(unitKey(id), new Unit(id, parent, head, receivablesManager, active));
        return this;
    }

    public FakeOrganisation person(long id, long unit, Long leader) {
        people.put(TestAccounts.employee(id), new Person(TestAccounts.employee(id), "person-" + id, unit, leader, "wx-" + id, true));
        return this;
    }

    public FakeOrganisation person(EmployeeId id, long unit, Long leader) {
        people.put(id, new Person(id, "person-" + id, unit, leader, "wx-" + id, true));
        return this;
    }

    public FakeOrganisation personWithoutWecom(long id, long unit, Long leader) {
        people.put(TestAccounts.employee(id), new Person(TestAccounts.employee(id), "person-" + id, unit, leader, null, true));
        return this;
    }

    public FakeOrganisation separated(long id, long unit, Long leader) {
        people.put(TestAccounts.employee(id), new Person(TestAccounts.employee(id), "person-" + id, unit, leader, "wx-" + id, false));
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
        return people.get(TestAccounts.employee(id)).wecom();
    }

    @Override
    public Optional<EmployeeProfile> findEmployee(EmployeeId employeeId) {
        if (failNextLookup.compareAndSet(true, false)) {
            throw new IllegalStateException("organisation lookup failed");
        }
        return Optional.ofNullable(people.get(employeeId)).map(p -> new EmployeeProfile(p.id(), p.name(),
                TestOrgIds.orgUnit(p.unit()), p.wecom() == null ? null : new WecomUserId(p.wecom()),
                p.employed() ? EmploymentStatus.EMPLOYED : EmploymentStatus.SEPARATED, false));
    }

    @Override
    public Optional<EmployeeProfile> findEmployeeByLogin(com.forcitis.one.organization.workforce.api.LoginLookup lookup) {
        if (!(lookup instanceof com.forcitis.one.organization.workforce.api.LoginLookup.ByWecomUser(WecomUserId wecomUserId))) {
            return Optional.empty();
        }
        return people.values().stream().filter(p -> wecomUserId.value().equals(p.wecom())).findFirst()
                .flatMap(p -> findEmployee(p.id()));
    }

    /** Who is on unpaid leave, for the tests that skip them. */
    public final java.util.Set<EmployeeId> onLeave = new java.util.HashSet<>();

    @Override
    public com.forcitis.one.organization.workforce.api.Availability availabilityOn(EmployeeId employeeId, java.time.LocalDate day) {
        return new com.forcitis.one.organization.workforce.api.Availability(onLeave.contains(employeeId), false, false);
    }

    @Override
    public java.util.Optional<EmploymentStatus> employmentOn(EmployeeId employeeId, java.time.LocalDate day) {
        return findEmployee(employeeId).map(e -> e.employmentStatus());
    }

    @Override
    public com.forcitis.one.organization.workforce.api.EmploymentVerdict employmentOf(EmployeeId employeeId) {
        return findEmployee(employeeId).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED
                ? com.forcitis.one.organization.workforce.api.EmploymentVerdict.EMPLOYED
                : com.forcitis.one.organization.workforce.api.EmploymentVerdict.SEPARATED)
                .orElse(com.forcitis.one.organization.workforce.api.EmploymentVerdict.NOT_LISTED);
    }

    @Override
    public Optional<EmployeeId> findSupervisor(EmployeeId employeeId) {
        return Optional.ofNullable(people.get(employeeId)).map(Person::leader)
                .map(TestAccounts::employee).filter(l -> people.containsKey(l) && people.get(l).employed());
    }

    @Override
    public Optional<EmployeeId> findOrgUnitHead(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(orgUnitId.value())).map(Unit::head).map(TestAccounts::employee);
    }

    @Override
    public Optional<EmployeeId> findRegionalReceivablesManager(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(orgUnitId.value())).map(Unit::receivablesManager).map(TestAccounts::employee);
    }

    @Override
    public Optional<OrgUnitProfile> findOrgUnit(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(orgUnitId.value())).map(u -> new OrgUnitProfile(TestOrgIds.orgUnit(u.id()), "unit-" + u.id(),
                u.parent() == null ? null : TestOrgIds.orgUnit(u.parent()), u.active()));
    }

    @Override
    public Set<UserId> findRoleHolders(RoleCode roleCode) {
        Set<UserId> holders = new LinkedHashSet<>();
        for (Long holder : roles.getOrDefault(roleCode.value(), Set.of())) {
            Person person = people.get(TestAccounts.employee(holder));
            if (person != null && person.employed()) {
                holders.add(TestAccounts.account(TestAccounts.employee(holder)));
            }
        }
        return holders;
    }

    private static UUID unitKey(long id) {
        return TestOrgIds.orgUnit(id).value();
    }
}
