package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.SourceGender;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourcePersonId;
import com.forcitis.one.platform.integration.api.SourcePosition;
import com.forcitis.one.platform.integration.api.SourcePositionId;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRegionKind;
import com.forcitis.one.platform.integration.api.SourceRelationshipKind;
import com.forcitis.one.platform.integration.api.SourceRelationship;
import com.forcitis.one.platform.integration.api.SourceUnitId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Row to api mapping for the Seeyon views. One mapper serves the bulk read and the single-person lookups, so the same
 * interpretation (NULL flags, enum ids, gender, login name) applies everywhere. Reading is tolerant about column
 * types (the source stores ids and flags as numbers or digit strings); rows that cannot be identified are skipped.
 */
final class SeeyonViewReader {

    private final SeeyonMapping mapping;

    SeeyonViewReader(SeeyonMapping mapping) {
        this.mapping = mapping;
    }

    /**
     * RULE-355: disabled when IS_DELETED = 1, or IS_INTERNAL != 1, or IS_ENABLE != 1; a NULL flag counts as valid and is
     * reported by its role name. The hierarchy path stays inside this package: {@link SeeyonOrgTree} turns it into a parent.
     */
    Optional<OrgUnitRow> orgUnit(ResultSet rs) throws SQLException {
        Long id = longOrNull(rs, "ID");
        String name = text(rs, "NAME");
        String path = text(rs, "PATH");
        if (id == null || id == 0 || name == null || path == null) {
            return Optional.empty();
        }
        Integer deleted = flag(rs, "IS_DELETED");
        Integer internal = flag(rs, "IS_INTERNAL");
        Integer enabled = flag(rs, "IS_ENABLE");
        List<String> unmaintained = new ArrayList<>();
        if (deleted == null) {
            unmaintained.add("deleted");
        }
        if (internal == null) {
            unmaintained.add("internal");
        }
        if (enabled == null) {
            unmaintained.add("enabled");
        }
        boolean disabled = (deleted != null && deleted == 1) || (internal != null && internal != 1) || (enabled != null && enabled != 1);
        String rawCategory = text(rs, "CATEGORY");
        SourceRegionKind region = region(rawCategory);
        String deliveryEnum = text(rs, "DELIVERY_FLAG_ENUM_ID");
        boolean delivery = deliveryEnum != null && deliveryEnum.equals(mapping.deliveryUnitYesEnumId());
        return Optional.of(new OrgUnitRow(new SourceUnitId(id), text(rs, "CODE"), name, path, intOrNull(rs, "SORT_ID"),
                disabled ? SourceRecordState.DISABLED : SourceRecordState.ACTIVE, unmaintained, region,
                region == null ? rawCategory : null, delivery));
    }

    Optional<SourceRelationship> relationship(ResultSet rs) throws SQLException {
        Long member = longOrNull(rs, "SOURCE_MEMBER_ID");
        Long unit = longOrNull(rs, "ORG_UNIT_ID");
        Long type = longOrNull(rs, "RELATIONSHIP_TYPE_ID");
        if (member == null || member == 0 || unit == null || unit == 0 || type == null) {
            return Optional.empty();
        }
        SourceRelationshipKind kind;
        if (type == mapping.headRelationshipTypeId()) {
            kind = SourceRelationshipKind.UNIT_HEAD;
        } else if (type == mapping.regionalReceivablesManagerRelationshipTypeId()) {
            kind = SourceRelationshipKind.REGIONAL_RECEIVABLES_MANAGER;
        } else {
            return Optional.empty();
        }
        Integer deleted = flag(rs, "MEMBER_IS_DELETED");
        return Optional.of(new SourceRelationship(kind, new SourceUnitId(unit), new SourcePersonId(member), deleted != null && deleted == 1));
    }

    /** RULE-367: deleted and disabled positions are read too, so they can be followed. */
    Optional<SourcePosition> position(ResultSet rs) throws SQLException {
        Long id = longOrNull(rs, "ID");
        String name = text(rs, "NAME");
        if (id == null || id == 0 || name == null) {
            return Optional.empty();
        }
        Integer deleted = flag(rs, "IS_DELETED");
        Integer status = flag(rs, "STATUS");
        SourceRecordState state = deleted != null && deleted == 1 ? SourceRecordState.DELETED
                : status != null && status != 0 ? SourceRecordState.DISABLED : SourceRecordState.ACTIVE;
        return Optional.of(new SourcePosition(new SourcePositionId(id), text(rs, "CODE"), name, intOrNull(rs, "SORT_ID"), state));
    }

    Optional<SourcePerson> person(ResultSet rs) throws SQLException {
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
        return Optional.of(new SourcePerson(new SourcePersonId(id), code, name, new SourceUnitId(orgUnit),
                post == null || post == 0 ? null : new SourcePositionId(post),
                leader == null || leader == 0 ? null : new SourcePersonId(leader),
                text(rs, "LOGIN_NAME"), text(rs, "ENTERPRISE_EMAIL"), gender(text(rs, "GENDER")),
                wecom == null ? null : new WecomUserId(wecom),
                feeEarnerEnum != null && feeEarnerEnum.equals(mapping.feeEarnerYesEnumId())));
    }

    static SourceRegionKind region(String displayValue) {
        if (displayValue == null) {
            return null;
        }
        return switch (displayValue.trim()) {
            case "总部" -> SourceRegionKind.HEADQUARTERS;
            case "区域" -> SourceRegionKind.REGIONAL;
            default -> null;
        };
    }

    /** Unknown or unmaintained stays empty (the old system wrote everything but male as female). */
    static SourceGender gender(String text) {
        if (text == null) {
            return null;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "男", "male", "m" -> SourceGender.MALE;
            case "女", "female", "f" -> SourceGender.FEMALE;
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

    /** Flags are numeric or bit columns: 1 / true is 1, 0 / false is 0, NULL stays null. */
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
