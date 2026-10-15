package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourcePosition;
import com.forcitis.one.platform.integration.api.SourceRelationship;
import com.forcitis.one.platform.integration.application.SourceReferences;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Turns the ids of what the views returned into own ids (ADR-0033 section 2 items 2 to 4): one lookup per kind for every id of the read,
 * relationships included, and an allocation only for the ids that have no reference yet. An id that a relationship names but the read
 * does not return gets its own id all the same (RULING-P3B-D3), so the snapshot can tell "not there" from "none".
 */
final class SourceIdTranslation {

    private final SourceReferences references;

    SourceIdTranslation(SourceReferences references) {
        this.references = references;
    }

    SourceOrgSnapshot snapshot(List<PlacedUnit> units, List<RelationshipRow> relationships, List<PostRow> posts, List<MemberRow> members) {
        Ids ids = new Ids();
        units.forEach(u -> {
            ids.add(SourceObjectKind.ORG_UNIT, u.row().unitId());
            ids.add(SourceObjectKind.ORG_UNIT, u.parentId());
            ids.add(SourceObjectKind.ORG_UNIT, u.duplicateOfId());
        });
        relationships.forEach(r -> {
            ids.add(SourceObjectKind.ORG_UNIT, r.unitId());
            ids.add(SourceObjectKind.EMPLOYEE, r.memberId());
        });
        posts.forEach(p -> ids.add(SourceObjectKind.POSITION, p.postId()));
        members.forEach(m -> ids.addMember(m));
        Map<SourceObjectKind, Map<Long, UUID>> own = ids.translate(references);

        List<SourceOrgUnit> ownUnits = units.stream().map(u -> {
            OrgUnitRow row = u.row();
            return new SourceOrgUnit(id(own, SourceObjectKind.ORG_UNIT, row.unitId()), row.code(), row.name(),
                    id(own, SourceObjectKind.ORG_UNIT, u.parentId()), row.sortOrder(), row.state(), row.emptyFlags(), row.regionKind(),
                    row.rawRegion(), row.deliveryUnit(), id(own, SourceObjectKind.ORG_UNIT, u.duplicateOfId()));
        }).toList();
        List<SourceRelationship> ownRelationships = relationships.stream()
                .sorted(Comparator.comparingLong(RelationshipRow::memberId))
                .map(r -> new SourceRelationship(r.kind(), id(own, SourceObjectKind.ORG_UNIT, r.unitId()), id(own, SourceObjectKind.EMPLOYEE, r.memberId()),
                        r.memberDeleted()))
                .toList();
        List<SourcePosition> ownPositions = posts.stream()
                .map(p -> new SourcePosition(id(own, SourceObjectKind.POSITION, p.postId()), p.code(), p.name(), p.sortOrder(), p.state()))
                .toList();
        return new SourceOrgSnapshot(ownUnits, ownRelationships, ownPositions, people(members, own));
    }

    List<SourcePerson> people(List<MemberRow> members) {
        Ids ids = new Ids();
        members.forEach(ids::addMember);
        return people(members, ids.translate(references));
    }

    private static List<SourcePerson> people(List<MemberRow> members, Map<SourceObjectKind, Map<Long, UUID>> own) {
        return members.stream().map(m -> new SourcePerson(id(own, SourceObjectKind.EMPLOYEE, m.memberId()), m.code(), m.name(),
                id(own, SourceObjectKind.ORG_UNIT, m.unitId()), id(own, SourceObjectKind.POSITION, m.postId()),
                id(own, SourceObjectKind.EMPLOYEE, m.supervisorId()), m.loginName(), m.email(), m.gender(), m.wecomUserId(), m.feeEarner(),
                m.internalFlagMissing())).toList();
    }

    private static OwnObjectId id(Map<SourceObjectKind, Map<Long, UUID>> own, SourceObjectKind kind, Long sourceId) {
        if (sourceId == null) {
            return null;
        }
        UUID found = own.get(kind).get(sourceId);
        if (found == null) {
            throw new IllegalStateException("no own id for " + kind + " " + sourceId + " after the translation");
        }
        return new OwnObjectId(found);
    }

    private static final class Ids {
        private final Map<SourceObjectKind, TreeSet<Long>> byKind = new EnumMap<>(SourceObjectKind.class);

        void add(SourceObjectKind kind, Long sourceId) {
            if (sourceId != null) {
                byKind.computeIfAbsent(kind, k -> new TreeSet<>()).add(sourceId);
            }
        }

        void addMember(MemberRow m) {
            add(SourceObjectKind.EMPLOYEE, m.memberId());
            add(SourceObjectKind.EMPLOYEE, m.supervisorId());
            add(SourceObjectKind.ORG_UNIT, m.unitId());
            add(SourceObjectKind.POSITION, m.postId());
        }

        Map<SourceObjectKind, Map<Long, UUID>> translate(SourceReferences references) {
            Map<SourceObjectKind, Map<Long, UUID>> own = new EnumMap<>(SourceObjectKind.class);
            for (SourceObjectKind kind : List.of(SourceObjectKind.ORG_UNIT, SourceObjectKind.POSITION, SourceObjectKind.EMPLOYEE)) {
                TreeSet<Long> wanted = byKind.getOrDefault(kind, new TreeSet<>());
                Map<Long, UUID> found = new HashMap<>(wanted.isEmpty() ? Map.of() : references.findOwnIds(ExternalSource.ORG_SOURCE, kind, wanted));
                for (Long sourceId : wanted) {
                    if (!found.containsKey(sourceId)) {
                        found.put(sourceId, references.allocate(ExternalSource.ORG_SOURCE, kind, sourceId));
                    }
                }
                own.put(kind, found);
            }
            return own;
        }
    }
}
