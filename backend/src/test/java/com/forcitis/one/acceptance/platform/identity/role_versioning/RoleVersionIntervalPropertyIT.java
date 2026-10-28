package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ADR-0034 guard rule 2 (性质测试 GrantIntervalPropertyTest：随机序列含开启、关闭、同刻开关、时钟回拨、「快节点关闭后慢节点再开」；每条至多一条开放行、
 * 无倒挂、不撞排他约束；任一时刻的还原等于按序列回放得到的集合；角色版本按同一序列验证), decision 1 items 4–6 and decision 4 item 3 (按时刻还原的谓词
 * {@code tstzrange(effective_from_at, effective_to_at, '[)') @> :asOf}，零长区间不出现); ADR-0034-IMPL card row 0034-G2-03, the role version
 * part that PR-2 owns (the grant part follows in PR-3).
 * <p>
 * A seeded random sequence of role changes (create, rename, enable / disable, scope, permission set, an unchanged definition, delete) with
 * the injected clock moving forward, standing still and going back is replayed through the role management HTTP paths. A model that only
 * knows ADR-0034's rules (a new version starts at greatest(:now, current start, max closed end); no change adds no version; delete closes
 * the last version) predicts every role's versions; the rows must equal it, and the version found at any moment by the as-of predicate
 * must be the one the model has in force then. Fixed seeds keep runs reproducible (CS-5.6).
 */
@Testcontainers
class RoleVersionIntervalPropertyIT extends RoleVersioningSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    static final int OPERATIONS = 32;
    static final List<String> SCOPES = List.of("SELF", "ALL", "OWN_ORG_UNIT", "SELF_AND_DIRECT_REPORTS");
    /** The administrator mode lasts an hour from NOW; the clock stays inside it. */
    static final Instant EARLIEST = NOW.minus(Duration.ofMinutes(30));
    static final Instant LATEST = NOW.plus(Duration.ofMinutes(50));

    /** One role as the model sees it: the versions it should have (start and definition), and when it was deleted. */
    static final class ModelRole {
        final UUID roleId;
        final List<Instant> starts = new ArrayList<>();
        final List<Definition> definitions = new ArrayList<>();
        Instant deletedAt;

        ModelRole(UUID roleId) {
            this.roleId = roleId;
        }

        Definition current() {
            return definitions.getLast();
        }

        /** ADR-0034 decision 1 item 6: greatest(:now, current version's start, max end of the role's closed versions). */
        Instant nextStart(Instant now) {
            Instant start = now;
            for (Instant s : starts) {
                if (s.isAfter(start)) {
                    start = s;
                }
            }
            return start;
        }

        void change(Instant now, Definition definition) {
            if (definition.equals(current())) {
                return;
            }
            Instant start = nextStart(now);
            starts.add(start);
            definitions.add(definition);
        }

        /** The definition in force at the moment, by the half-open intervals the versions form; empty when none is. */
        Definition inForceAt(Instant moment) {
            Definition found = null;
            for (int i = 0; i < starts.size(); i++) {
                Instant end = i + 1 < starts.size() ? starts.get(i + 1) : deletedAt;
                if (!starts.get(i).isAfter(moment) && (end == null || end.isAfter(moment))) {
                    found = definitions.get(i);
                }
            }
            return found;
        }
    }

    private Definition inForceInDatabase(UUID role, Instant moment) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement("""
                select role_name, role_status, access_scope_kind, permission_codes from role_versions
                 where role_id = ? and tstzrange(effective_from_at, effective_to_at, '[)') @> ?""")) {
            select.setObject(1, role);
            select.setObject(2, OffsetDateTime.ofInstant(moment, ZoneOffset.UTC));
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Definition definition = new Definition(rs.getString(1), "ENABLED".equals(rs.getString(2)), rs.getString(3), codes(rs.getArray(4)));
                assertThat(rs.next()).as("at most one version of role %s is in force at %s", role, moment).isFalse();
                return definition;
            }
        }
    }

    private static Instant moveClock(Random random, Instant now) {
        Instant next = switch (random.nextInt(4)) {
            case 0 -> now;
            case 1 -> now.minus(Duration.ofSeconds(1 + random.nextInt(20 * 60)));
            default -> now.plus(Duration.ofSeconds(1 + random.nextInt(10 * 60)));
        };
        if (next.isBefore(EARLIEST)) {
            return EARLIEST;
        }
        return next.isAfter(LATEST) ? LATEST : next;
    }

    private static List<String> someCodes(Random random) {
        List<String> codes = new ArrayList<>();
        for (String code : PLAIN_CODES) {
            if (random.nextBoolean()) {
                codes.add(code);
            }
        }
        return codes;
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {20261102L, 34L, 728L})
    @DisplayName("0034-G2-03 角色版本性质测试：随机修订、停用启用、内容不变、删除，注入时钟前进、静止、回拨；每个角色至多一个当前版本、版本序号连续、无倒挂、相邻版本首尾相接、每步都成功（不撞排他约束）；版本行逐条等于按规则回放的结果；任一时刻按 tstzrange @> asOf 还原的定义等于回放在该时刻生效的定义，零长区间不出现")
    void roleVersionsFollowTheReplay(long seed) throws Exception {
        Random random = new Random(seed);
        SignedIn administrator = roleAdministrator();
        String prefix = "rv_p" + Long.toHexString(seed) + "_" + UUID.randomUUID().toString().substring(0, 6);
        int names = 0;
        Instant now = NOW;
        List<ModelRole> roles = new ArrayList<>();
        for (int r = 0; r < 2; r++) {
            now = moveClock(random, now);
            clock.set(now);
            Definition first = new Definition(prefix + " role " + r + " name " + names++, true, SCOPES.get(random.nextInt(SCOPES.size())), someCodes(random));
            ModelRole role = new ModelRole(createdRole(administrator, prefix + "_" + r, first));
            role.starts.add(now);
            role.definitions.add(first);
            roles.add(role);
        }

        for (int step = 0; step < OPERATIONS; step++) {
            now = moveClock(random, now);
            clock.set(now);
            List<ModelRole> live = roles.stream().filter(role -> role.deletedAt == null).toList();
            if (live.isEmpty()) {
                break;
            }
            ModelRole role = live.get(random.nextInt(live.size()));
            Definition current = role.current();
            int kind = random.nextInt(step > OPERATIONS / 2 ? 7 : 6);
            if (kind == 6) {
                MockHttpServletResponse deleted = deleteRole(administrator, role.roleId);
                assertThat(deleted.getStatus()).as("seed %d step %d delete: %s", seed, step, deleted.getContentAsString()).isEqualTo(204);
                role.deletedAt = role.nextStart(now);
                continue;
            }
            Definition next = switch (kind) {
                case 0 -> new Definition(prefix + " role name " + names++, current.enabled(), current.accessScope(), current.permissionCodes());
                case 1 -> new Definition(current.name(), !current.enabled(), current.accessScope(), current.permissionCodes());
                case 2 -> new Definition(current.name(), current.enabled(), SCOPES.get(random.nextInt(SCOPES.size())), current.permissionCodes());
                case 3 -> new Definition(current.name(), current.enabled(), current.accessScope(), someCodes(random));
                default -> current;
            };
            MockHttpServletResponse updated = updateRole(administrator, role.roleId, next);
            assertThat(updated.getStatus()).as("seed %d step %d update to %s: %s", seed, step, next, updated.getContentAsString()).isEqualTo(200);
            role.change(now, next);
        }

        for (ModelRole role : roles) {
            List<VersionRow> versions = versionsOf(role.roleId);
            assertWellFormedHistory(versions);
            assertThat(versions.stream().filter(VersionRow::open).count()).as("seed %d role %s: current versions", seed, role.roleId)
                    .isEqualTo(role.deletedAt == null ? 1 : 0);
            assertThat(versions.stream().map(VersionRow::from).toList()).as("seed %d role %s: version starts", seed, role.roleId).isEqualTo(role.starts);
            assertThat(versions.stream().map(v -> new Definition(v.roleName(), "ENABLED".equals(v.roleStatus()), v.accessScopeKind(), v.permissionCodes())).toList())
                    .as("seed %d role %s: version definitions", seed, role.roleId).isEqualTo(role.definitions);
            if (role.deletedAt != null) {
                assertThat(versions.getLast().to()).as("seed %d role %s: closed at the delete", seed, role.roleId).isEqualTo(role.deletedAt);
            }

            TreeSet<Instant> moments = new TreeSet<>();
            moments.add(EARLIEST.minus(Duration.ofHours(1)));
            List<Instant> boundaries = new ArrayList<>(role.starts);
            if (role.deletedAt != null) {
                boundaries.add(role.deletedAt);
            }
            for (Instant boundary : boundaries) {
                moments.add(boundary.minusMillis(1));
                moments.add(boundary);
                moments.add(boundary.plusMillis(1));
            }
            moments.add(LATEST.plus(Duration.ofHours(1)));
            for (Instant moment : moments) {
                assertThat(inForceInDatabase(role.roleId, moment)).as("seed %d role %s as of %s", seed, role.roleId, moment).isEqualTo(role.inForceAt(moment));
            }
        }
    }
}
