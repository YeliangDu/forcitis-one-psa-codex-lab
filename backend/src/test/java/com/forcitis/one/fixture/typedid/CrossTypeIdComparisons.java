package com.forcitis.one.fixture.typedid;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Comparisons of one kind of typed id with another: the ones the guard must catch (wrong…), the ones it cannot see and says so (boundary…),
 * and the ones that are fine (right…).
 */
public final class CrossTypeIdComparisons {

    public record Scope(Set<UserId> owners, Map<UserId, String> names) {
    }

    private final Set<UserId> accounts = new HashSet<>();
    private final Map<UserId, String> accountNames = new HashMap<>();

    public boolean wrongEqualsOnTheIds(UserId user, EmployeeId employee) {
        return user.equals(employee);
    }

    public boolean wrongObjectsEquals(UserId user, EmployeeId employee) {
        return Objects.equals(user, employee);
    }

    public boolean wrongContainsOnAParameter(Set<UserId> users, EmployeeId employee) {
        return users.contains(employee);
    }

    public boolean wrongContainsOnAField(EmployeeId employee) {
        return accounts.contains(employee);
    }

    public boolean wrongContainsOnALocal(EmployeeId employee) {
        Set<UserId> local = new HashSet<>();
        return local.contains(employee);
    }

    public boolean wrongListLookup(List<UserId> users, EmployeeId employee) {
        List<UserId> copy = new ArrayList<>(users);
        return copy.indexOf(employee) >= 0 || users.remove(employee);
    }

    public String wrongMapGet(Map<UserId, String> names, EmployeeId employee) {
        return names.get(employee);
    }

    public boolean wrongContainsKey(EmployeeId employee) {
        return accountNames.containsKey(employee);
    }

    public String wrongGetOrDefault(Map<UserId, String> names, EmployeeId employee) {
        return names.getOrDefault(employee, "nobody");
    }

    public boolean wrongRemoveFromAKeySet(Map<UserId, String> names, EmployeeId employee) {
        return names.keySet().remove(employee);
    }

    public boolean wrongThroughAnAccessor(Scope scope, EmployeeId employee) {
        return scope.owners().contains(employee) || scope.names().containsKey(employee);
    }

    public boolean wrongWithAWildcard(Set<? extends UserId> users, EmployeeId employee) {
        return users.contains(employee);
    }

    public Optional<Boolean> wrongReferenceEquals(Optional<UserId> found, EmployeeId employee) {
        return found.map(employee::equals);
    }

    public boolean wrongReferenceContains(Set<UserId> users, List<EmployeeId> employees) {
        return employees.stream().anyMatch(users::contains);
    }

    public boolean wrongReferenceContainsKey(Map<UserId, String> names, List<EmployeeId> employees) {
        return employees.stream().anyMatch(names::containsKey);
    }

    public Optional<Boolean> rightReferenceEquals(Optional<UserId> found, UserId user) {
        return found.map(user::equals);
    }

    public boolean rightReferenceContains(Set<UserId> users, List<UserId> others) {
        return others.stream().anyMatch(users::contains);
    }

    public boolean boundaryLongLocals(OrgUnitId user, EmployeeId employee) {
        java.util.UUID first = user.value();
        java.util.UUID second = employee.value();
        return first.equals(second);
    }

    public boolean boundaryLongCompare(OrgUnitId user, EmployeeId employee) {
        return user.value().compareTo(employee.value()) == 0;
    }

    public boolean boundaryValueAsText(UserId user, EmployeeId employee) {
        return String.valueOf(user.value()).equals(String.valueOf(employee.value()));
    }

    public boolean boundaryOuterContainers(Optional<UserId> user, Optional<EmployeeId> employee) {
        return user.equals(employee);
    }

    public List<OrgUnitId> boundaryBuiltThroughReferences(List<EmployeeId> employees) {
        return employees.stream().map(EmployeeId::value).map(OrgUnitId::new).toList();
    }

    public boolean boundaryAfterAnOptional(Optional<EmployeeId> found, Set<UserId> users) {
        return users.contains(found.orElseThrow());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean boundaryRawCollection(Set users, EmployeeId employee) {
        return users.contains(employee);
    }

    public boolean boundaryValueOfAMap(Map<String, Set<UserId>> byName, EmployeeId employee) {
        return byName.get("x").contains(employee);
    }

    public OrgUnitId wrongBuiltFromAnotherId(EmployeeId employee) {
        return new OrgUnitId(employee.value());
    }

    public EmployeeId wrongBuiltFromAnotherIdInAnExpression(OrgUnitId user) {
        return Optional.of(user).map(unit -> new EmployeeId(unit.value())).orElseThrow();
    }

    public OrgUnitId rightBuiltFromAnythingElse(java.util.UUID number, OrgUnitId user) {
        return new OrgUnitId(number != null ? number : user.value());
    }

    public boolean rightSameKindRebuilt(UserId user) {
        return new UserId(user.value()).equals(user);
    }

    public boolean rightSameKindEquals(UserId first, UserId second) {
        return first.equals(second) && Objects.equals(first, second);
    }

    public boolean rightSameKindLookups(Set<UserId> users, Map<UserId, String> names, UserId user) {
        return users.contains(user) || names.containsKey(user) || names.get(user) != null || accounts.contains(user);
    }

    public boolean rightThroughAnAccessor(Scope scope, UserId user) {
        return scope.owners().contains(user) && scope.names().containsKey(user);
    }

    public boolean rightAgainstAnObject(Set<UserId> users, Object anything) {
        return users.contains(anything);
    }

    public boolean rightPlainCollections(Set<String> names, List<java.util.UUID> numbers, EmployeeId employee) {
        return names.contains("x") || numbers.contains(employee.value());
    }

    public boolean rightIndexAndKeys(List<UserId> users, Map<EmployeeId, UserId> accountOfEmployee, EmployeeId employee) {
        return users.get(0) != null && accountOfEmployee.containsKey(employee) && users.contains(accountOfEmployee.get(employee));
    }
}
