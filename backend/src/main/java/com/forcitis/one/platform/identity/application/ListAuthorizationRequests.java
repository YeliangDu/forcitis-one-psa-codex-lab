package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListAuthorizationRequests {

    private final AuthorizationRequestRepository requests;

    ListAuthorizationRequests(AuthorizationRequestRepository requests) {
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public List<AuthorizationRequestView> execute() {
        return requests.findPending().stream().map(AuthorizationRequestView::of).toList();
    }
}
