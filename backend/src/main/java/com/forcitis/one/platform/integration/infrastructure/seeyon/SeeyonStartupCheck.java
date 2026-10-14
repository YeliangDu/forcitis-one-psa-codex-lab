package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Startup check of the Seeyon side (REIMAGINED_ARCHITECTURE §3.3, RULE-356): the columns the account can see in the view
 * schema must be EQUAL to the whitelist - not merely contain the needed ones - so an extra column (say, a birthday
 * or a bank card) or an extra view makes the application refuse to start. The configured relationship-type and enum
 * ids must exist in the data, and the account must not hold write or database-wide read permissions.
 * Switchable by configuration; tests inject an in-memory inspector.
 */
final class SeeyonStartupCheck implements SmartInitializingSingleton {

    private final SeeyonSchemaInspector inspector;
    private final SeeyonMapping mapping;

    SeeyonStartupCheck(SeeyonSchemaInspector inspector, SeeyonMapping mapping) {
        this.inspector = inspector;
        this.mapping = mapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Seeyon view check failed, refusing to start:\n  " + String.join("\n  ", problems));
        }
    }

    /** Empty when everything holds. */
    List<String> problems() {
        List<String> problems = new ArrayList<>(mapping.configurationProblems());
        try {
            problems.addAll(columnProblems(inspector.viewColumns()));
            if (mapping.headRelationshipTypeId() != 0 && inspector.countRelationshipsOfType(mapping.headRelationshipTypeId()) == 0) {
                problems.add("no relationship of the configured head type " + mapping.headRelationshipTypeId()
                        + " exists in the Seeyon view: every org unit head would silently become empty");
            }
            if (mapping.regionalReceivablesManagerRelationshipTypeId() != 0
                    && inspector.countRelationshipsOfType(mapping.regionalReceivablesManagerRelationshipTypeId()) == 0) {
                problems.add("no relationship of the configured regional receivables manager type "
                        + mapping.regionalReceivablesManagerRelationshipTypeId() + " exists in the Seeyon view");
            }
            if (mapping.deliveryUnitYesEnumId() != null && !mapping.deliveryUnitYesEnumId().isBlank()
                    && inspector.countDeliveryUnitsMarked(mapping.deliveryUnitYesEnumId()) == 0) {
                problems.add("no org unit carries the configured delivery-unit 'yes' enum id: every delivery marker would silently become false");
            }
            if (mapping.feeEarnerYesEnumId() != null && !mapping.feeEarnerYesEnumId().isBlank()
                    && inspector.countFeeEarnersMarked(mapping.feeEarnerYesEnumId()) == 0) {
                problems.add("no person carries the configured fee-earner 'yes' enum id: every fee earner marker would silently become false");
            }
            List<String> excessive = inspector.excessivePermissions();
            if (!excessive.isEmpty()) {
                problems.add("the Seeyon account holds database-level permissions a read-only view account must not have: " + excessive);
            }
        } catch (RuntimeException unreachable) {
            problems.add("Seeyon cannot be checked (" + unreachable.getClass().getSimpleName() + ")");
        }
        return problems;
    }

    /** Exact equality of views and columns with the whitelist, in both directions. */
    static List<String> columnProblems(Map<String, Set<String>> actual) {
        List<String> problems = new ArrayList<>();
        Set<String> expectedViews = new TreeSet<>();
        SeeyonViewSql.VIEW_COLUMNS.keySet().forEach(v -> expectedViews.add(v.toUpperCase(java.util.Locale.ROOT)));
        for (String view : expectedViews) {
            Set<String> have = actual.get(view);
            if (have == null) {
                problems.add("view " + SeeyonViewSql.SCHEMA + "." + view + " is missing");
                continue;
            }
            Set<String> want = new TreeSet<>();
            SeeyonViewSql.VIEW_COLUMNS.get(view.toLowerCase(java.util.Locale.ROOT)).forEach(c -> want.add(c.toUpperCase(java.util.Locale.ROOT)));
            Set<String> extra = new TreeSet<>(have);
            extra.removeAll(want);
            Set<String> missing = new TreeSet<>(want);
            missing.removeAll(have);
            if (!extra.isEmpty()) {
                problems.add("view " + view + " has columns outside the whitelist: " + extra);
            }
            if (!missing.isEmpty()) {
                problems.add("view " + view + " lacks whitelisted columns: " + missing);
            }
        }
        actual.keySet().stream().filter(v -> !expectedViews.contains(v))
                .forEach(v -> problems.add("object " + SeeyonViewSql.SCHEMA + "." + v + " is visible to the account but is not a whitelisted view"));
        return problems;
    }
}
