package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #730 S3, ruling 6602368110 item 3, empty scope of a code holder: on a database whose only item is about the caller, a holder of the
 * read code sees 200 with an empty page, never 403 and never its own item. It has its own class because the other read tests share one
 * database in which other people's items are always present.
 */
class PermissionAdjustmentEmptyScopeIT extends PermissionAdjustmentApiSupport {

    static final TestDatabase postgres = TestPostgres.database(PermissionAdjustmentEmptyScopeIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void organisation() throws Exception {
        apiWorld();
    }

    @Test
    @DisplayName("#730 S3 裁定第 3 条空范围：库里唯一的条目是关于本人的，权限码持有者的列表为 200、items 为空、没有下一页游标（不是 403），读本人条目 404；另一位管理员能列出它")
    void aCodeHolderWhoseOnlyItemIsItsOwnSeesAnEmptyList() throws Exception {
        UUID own = sensitiveItemAbout("odaSubject");

        MockHttpServletResponse listed = listPage(session("odaSubject"), "limit", "200");

        assertThat(listed.getStatus()).as(listed.getContentAsString()).isEqualTo(200);
        assertThat(items(listed)).isEmpty();
        assertThat((Object) JsonPath.read(listed.getContentAsString(), "$.nextCursor")).isNull();
        assertThat(read(session("odaSubject"), own).getStatus()).isEqualTo(404);
        assertThat(listedIds(session("oda"))).contains(own.toString());
    }
}
