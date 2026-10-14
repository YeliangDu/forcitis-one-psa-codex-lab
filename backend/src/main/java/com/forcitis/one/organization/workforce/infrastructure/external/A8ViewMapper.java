package com.forcitis.one.organization.workforce.infrastructure.external;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.Gender;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.domain.PositionId;
import com.forcitis.one.organization.workforce.domain.PositionStatus;
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.organization.workforce.domain.RelationshipKind;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceOrgUnit;
import com.forcitis.one.organization.workforce.domain.SourcePosition;
import com.forcitis.one.organization.workforce.domain.SourceRelationship;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Row to domain mapping for the A8 views. One mapper serves the bulk sync and the single-person lookup, so the same
 * interpretation (NULL flags, enum ids, gender, login name) applies everywhere. Reading is tolerant about column
 * types (A8 stores ids and flags as numbers or digit strings); rows that cannot be identified are skipped.
 */
final class A8ViewMapper {

    private final A8Mapping mapping;

    A8ViewMapper(A8Mapping mapping) {
        this.mapping = mapping;
    }

    /** RULE-355: disabled when IS_DELETED = 1, or IS_INTERNAL != 1, or IS_ENABLE != 1; a NULL flag counts as valid and is reported. */
    Optional<SourceOrgUnit> orgUnit(ResultSet rs) throws SQLException {
        Long id = longOrNull(rs, "ID");
        String name = text(rs, "NAME");
        String path = text(rs, "PATH");
        if (id == null || id == 0 || name == null || path == null) {
            return Optional.empty();
        }
        Integer deleted = flag(rs, "IS_DELETED");
        Integer internal = flag(rs, "IS_INTERNAL");
        Integer enabled = flag(rs, "IS_ENABLE");
        List<String> nullFlags = new ArrayList<>();
        if (deleted == null) {
            nullFlags.add("IS_DELETED");
        }
        if (internal == null) {
            nullFlags.add("IS_INTERNAL");
        }
        if (enabled == null) {
            nullFlags.add("IS_ENABLE");
        }
        boolean disabled = (deleted != null && deleted == 1) || (internal != null && internal != 1) || (enabled != null && enabled != 1);
        String rawCategory = text(rs, "CATEGORY");
        RegionCategory category = category(rawCategory);
        String deliveryEnum = text(rs, "DELIVERY_FLAG_ENUM_ID");
        boolean delivery = deliveryEnum != null && deliveryEnum.equals(mapping.deliveryUnitYesEnumId());
        return Optional.of(new SourceOrgUnit(new OrgUnitId(id), text(rs, "CODE"), name, path, intOrNull(rs, "SORT_ID"),
                disabled ? OrgUnitStatus.DISABLED : OrgUnitStatus.ACTIVE, nullFlags, category,
                category == null ? rawCategory : null, delivery));
    }

    Optional<SourceRelationship> relationship(ResultSet rs) throws SQLException {
        Long member = longOrNull(rs, "SOURCE_MEMBER_ID");
        Long unit = longOrNull(rs, "ORG_UNIT_ID");
        Long type = longOrNull(rs, "RELATIONSHIP_TYPE_ID");
        if (member == null || member == 0 || unit == null || unit == 0 || type == null) {
            return Optional.empty();
        }
        RelationshipKind kind;
        if (type == mapping.headRelationshipTypeId()) {
            kind = RelationshipKind.HEAD;
        } else if (type == mapping.regionalReceivablesManagerRelationshipTypeId()) {
            kind = RelationshipKind.REGIONAL_RECEIVABLES_MANAGER;
        } else {
            return Optional.empty();
        }
        Integer deleted = flag(rs, "MEMBER_IS_DELETED");
        return Optional.of(new SourceRelationship(kind, new OrgUnitId(unit), new UserId(member), deleted != null && deleted == 1));
    }

    /** RULE-367: deleted and disabled positions are read too, so they can be synced. */
    Optional<SourcePosition> position(ResultSet rs) throws SQLException {
        Long id = longOrNull(rs, "ID");
        String name = text(rs, "NAME");
        if (id == null || id == 0 || name == null) {
            return Optional.empty();
        }
        Integer deleted = flag(rs, "IS_DELETED");
        Integer status = flag(rs, "STATUS");
        PositionStatus positionStatus = deleted != null && deleted == 1 ? PositionStatus.DELETED
                : status != null && status != 0 ? PositionStatus.DISABLED : PositionStatus.ACTIVE;
        return Optional.of(new SourcePosition(new PositionId(id), text(rs, "CODE"), name, intOrNull(rs, "SORT_ID"), positionStatus));
    }

    Optional<SourceMember> member(ResultSet rs) throws SQLException {
        Long id = longOrNull(rs, "ID");
        Long orgUnit = longOrNull(rs, "ORG_UNIT_ID");
        String code = text(rs, "CODE");
        String name = text(rs, "NAME");
        if (id == null || id == 0 || orgUnit == null || orgUnit == 0 || code == null || name == null) {
            return Optional.empty();
        }
        Long post = longOrNull(rs, "POST_ID");
        Long leader = longOrNull(rs, "LEADER_MEMBER_ID");
        String wecom = text(rs, "WE_COM_ID");
        String feeEarnerEnum = text(rs, "FEE_EARNER_ENUM_ID");
        return Optional.of(new SourceMember(new UserId(id), code, name, new OrgUnitId(orgUnit),
                post == null || post == 0 ? null : new PositionId(post),
                leader == null || leader == 0 ? null : new UserId(leader),
                text(rs, "LOGIN_NAME"), text(rs, "ENTERPRISE_EMAIL"), gender(text(rs, "GENDER")),
                wecom == null ? null : new WecomUserId(wecom),
                feeEarnerEnum != null && feeEarnerEnum.equals(mapping.feeEarnerYesEnumId())));
    }

    static RegionCategory category(String displayValue) {
        if (displayValue == null) {
            return null;
        }
        return switch (displayValue.trim()) {
            case "总部" -> RegionCategory.HEADQUARTERS;
            case "区域" -> RegionCategory.REGIONAL;
            default -> null;
        };
    }

    /** Unknown or unmaintained stays empty (the old system wrote everything but male as female). */
    static Gender gender(String text) {
        if (text == null) {
            return null;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "男", "male", "m" -> Gender.MALE;
            case "女", "female", "f" -> Gender.FEMALE;
            default -> null;
        };
    }

    // ---- tolerant column readers

    static String text(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    static Long longOrNull(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        Long value = longOrNull(rs, column);
        return value == null ? null : Math.toIntExact(value);
    }

    /** A8 flags are numeric or bit columns: 1 / true is 1, 0 / false is 0, NULL stays null. */
    static Integer flag(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "t" -> 1;
            case "false", "f" -> 0;
            default -> {
                try {
                    yield Integer.parseInt(text);
                } catch (NumberFormatException notANumber) {
                    yield null;
                }
            }
        };
    }
}
