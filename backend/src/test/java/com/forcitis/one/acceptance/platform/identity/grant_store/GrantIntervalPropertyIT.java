package com.forcitis.one.acceptance.platform.identity.grant_store;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #732 part A, 0034-G2-03 (grant part; the role version part is RoleVersionIntervalPropertyIT). ADR-0034 guard rule 2, decision 2 item 2 and
 * decision 4 item 3: a seeded random sequence of role-set replacements with the clock moving forward, standing still and going back is replayed
 * through the HTTP path. A model that knows only the rules (an opening starts at greatest(now, max end of the person's earlier rows of that
 * role); a close ends at greatest(now, start)) predicts every row; the rows must equal it, there is at most one open row per role, none is
 * inverted, every step succeeds, and the grants found at any moment by the as-of predicate equal the model's set at that moment.
 */
class GrantIntervalPropertyIT extends GrantStoreSupport {

    static final TestDatabase postgres = TestPostgres.database(GrantIntervalPropertyIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final int STEPS = 30;
    /** The administrator mode lasts an hour from NOW; the clock stays inside it. */
    private static final Instant EARLIEST = NOW.minus(Duration.ofMinutes(30));
    private static final Instant LATEST = NOW.plus(Duration.ofMinutes(50));

    private record Interval(Instant from, Instant to) {
        boolean contains(Instant moment) {
            return !from.isAfter(moment) && (to == null || to.isAfter(moment));
        }
    }

    private static Instant moveClock(Random random, Instant now) {
        Instant next = switch (random.nextInt(4)) {
            case 0 -> now;
            case 1 -> now.minus(Duration.ofSeconds(1 + random.nextInt(20 * 60)));
            default -> now.plus(Duration.ofSeconds(1 + random.nextInt(10 * 60)));
        };
        return next.isBefore(EARLIEST) ? EARLIEST : next.isAfter(LATEST) ? LATEST : next;
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {20261102L, 34L, 732L})
    @DisplayName("0034-G2-03 授予性质测试：随机替换角色集合，注入时钟前进、静止、回拨；每人每角色至多一条开放行、无倒挂、每步都成功（不撞排他约束）；授予行逐条等于按规则回放的结果；任一时刻按 tstzrange @> asOf 还原的角色集合等于回放在该时刻的集合，零长区间不出现")
    void grantsFollowTheReplay(long seed) throws Exception {
        Random random = new Random(seed);
        SignedIn administrator = administrator();
        SignedIn target = person();
        List<String> roles = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            roles.add(customRole(administrator, "SELF", PLAIN_CODES.get(i)));
        }
        List<List<Interval>> model = new ArrayList<>();
        roles.forEach(code -> model.add(new ArrayList<>()));
        Instant now = NOW;
        for (int step = 0; step < STEPS; step++) {
            now = moveClock(random, now);
            clock.set(now);
            List<String> wanted = new ArrayList<>(List.of("employee"));
            for (String role : roles) {
                if (random.nextBoolean()) {
                    wanted.add(role);
                }
            }
            replacedRoles(administrator, target.userId(), wanted);
            for (int r = 0; r < roles.size(); r++) {
                List<Interval> rows = model.get(r);
                boolean open = !rows.isEmpty() && rows.getLast().to() == null;
                boolean want = wanted.contains(roles.get(r));
                if (want && !open) {
                    Instant start = now;
                    for (Interval row : rows) {
                        if (row.to().isAfter(start)) {
                            start = row.to();
                        }
                    }
                    rows.add(new Interval(start, null));
                } else if (!want && open) {
                    Interval row = rows.removeLast();
                    rows.add(new Interval(row.from(), now.isAfter(row.from()) ? now : row.from()));
                }
            }
        }

        TreeSet<Instant> moments = new TreeSet<>(List.of(EARLIEST.minus(Duration.ofHours(1)), LATEST.plus(Duration.ofHours(1))));
        for (int r = 0; r < roles.size(); r++) {
            List<Grant> rows = grants(target.userId(), roles.get(r));
            assertThat(rows.stream().filter(Grant::open).count()).as("seed %d %s: at most one open row", seed, roles.get(r)).isLessThanOrEqualTo(1);
            assertThat(rows).as("seed %d %s: no inverted interval", seed, roles.get(r)).allSatisfy(row -> assertThat(row.open() || !row.to().isBefore(row.from())).isTrue());
            assertThat(rows.stream().map(row -> new Interval(row.from(), row.to())).toList()).as("seed %d %s: rows", seed, roles.get(r)).isEqualTo(model.get(r));
            for (Interval row : model.get(r)) {
                for (Instant edge : new Instant[] {row.from(), row.to()}) {
                    if (edge != null) {
                        moments.add(edge.minusMillis(1));
                        moments.add(edge);
                        moments.add(edge.plusMillis(1));
                    }
                }
            }
        }
        for (Instant moment : moments) {
            List<String> expected = new ArrayList<>();
            for (int r = 0; r < roles.size(); r++) {
                final int index = r;
                if (model.get(index).stream().anyMatch(row -> row.contains(moment))) {
                    expected.add(roles.get(index));
                }
            }
            List<String> restored = grantsAsOf(target.userId(), moment).stream().map(Grant::roleCode).filter(roles::contains).sorted().toList();
            assertThat(restored).as("seed %d as of %s", seed, moment).isEqualTo(expected.stream().sorted().toList());
        }
    }
}
