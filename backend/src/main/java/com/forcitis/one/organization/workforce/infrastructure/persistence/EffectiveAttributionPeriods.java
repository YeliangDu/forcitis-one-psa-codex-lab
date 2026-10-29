package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.organization.workforce.api.AttributionKind;

/**
 * The periods as they read today: the stored end of a closed period is replaced by the new end of its latest correction. The rows of
 * the period tables themselves are never rewritten.
 */
final class EffectiveAttributionPeriods {
    private EffectiveAttributionPeriods() {
    }

    /** The column that names the subject of the kind, in the attribution, correction and period tables alike (DA-5). */
    static String subjectColumn(AttributionKind kind) {
        return kind == AttributionKind.ORG_UNIT ? "org_unit_id" : "employee_id";
    }

    static String select(AttributionKind kind) {
        String table = kind == AttributionKind.ORG_UNIT ? "org_unit_legal_entities" : "employee_legal_entities";
        String column = subjectColumn(kind);
        return """
                select p.legal_entity_id, p.effective_from_date, coalesce(c.new_effective_to_date, p.effective_to_date) as effective_to_date
                  from organization_workforce.%1$s p
                  left join lateral (select new_effective_to_date from organization_workforce.legal_entity_attribution_corrections
                                      where %2$s = p.%2$s
                                        and period_effective_from_date = p.effective_from_date
                                      order by correction_number desc limit 1) c on true
                 where p.%2$s = :id
                """.formatted(table, column);
    }
}
