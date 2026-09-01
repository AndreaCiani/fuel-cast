package com.fuelcast.manager.security;

import com.fuelcast.manager.model.Manager;
import com.fuelcast.manager.repository.ManagerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the authenticated manager from the security context. */
@Service
public class CurrentManagerService {

    private final ManagerRepository managers;

    public CurrentManagerService(ManagerRepository managers) {
        this.managers = managers;
    }

    /** The authenticated manager, or 401 if there is none. */
    public Manager require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return managers.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
