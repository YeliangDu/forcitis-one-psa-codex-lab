package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListRoles {

    private final RoleRepository roles;

    ListRoles(RoleRepository roles) {
        this.roles = roles;
    }

    /** Ordered by code; the cursor is the code the previous page ended with. */
    @Transactional(readOnly = true)
    public CursorPage<RoleView> execute(PageRequest page) {
        List<RoleView> after = roles.findAll().stream().map(RoleView::of).sorted(Comparator.comparing(RoleView::code))
                .filter(role -> page.cursorValue() == null || role.code().compareTo(page.cursorValue()) > 0).toList();
        List<RoleView> items = after.stream().limit(page.limit()).toList();
        String next = after.size() > items.size() ? PageRequest.encode("roles", items.getLast().code()) : null;
        return CursorPage.of(items, next);
    }
}
