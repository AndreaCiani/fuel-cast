package com.fuelcast.manager.security;

import com.fuelcast.manager.model.Manager;
import com.fuelcast.manager.repository.ManagerRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads managers for Spring Security by email. */
@Service
public class ManagerUserDetailsService implements UserDetailsService {

    private final ManagerRepository managers;

    public ManagerUserDetailsService(ManagerRepository managers) {
        this.managers = managers;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Manager m = managers.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No manager with email " + email));
        return User.builder()
                .username(m.getEmail())
                .password(m.getPasswordHash())
                .authorities("ROLE_MANAGER")
                .build();
    }
}
