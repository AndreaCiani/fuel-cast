package com.fuelcast.manager.controller;

import com.fuelcast.manager.dto.ManagerDtos.LoginRequest;
import com.fuelcast.manager.dto.ManagerDtos.ManagerResponse;
import com.fuelcast.manager.dto.ManagerDtos.RegisterRequest;
import com.fuelcast.manager.model.Manager;
import com.fuelcast.manager.repository.ManagerRepository;
import com.fuelcast.manager.security.CurrentManagerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Registration, login, logout and "who am I" for station managers. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ManagerRepository managers;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final CurrentManagerService currentManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(ManagerRepository managers, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, CurrentManagerService currentManager) {
        this.managers = managers;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.currentManager = currentManager;
    }

    @PostMapping("/register")
    public ResponseEntity<ManagerResponse> register(@Valid @RequestBody RegisterRequest req,
                                                    HttpServletRequest request, HttpServletResponse response) {
        String email = req.email().trim().toLowerCase();
        if (managers.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered");
        }
        Manager m = new Manager();
        m.setEmail(email);
        m.setDisplayName(req.displayName().trim());
        m.setPasswordHash(passwordEncoder.encode(req.password()));
        managers.save(m);

        authenticate(email, req.password(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(m));
    }

    @PostMapping("/login")
    public ManagerResponse login(@Valid @RequestBody LoginRequest req,
                                 HttpServletRequest request, HttpServletResponse response) {
        String email = req.email().trim().toLowerCase();
        try {
            authenticate(email, req.password(), request, response);
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        Manager m = managers.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return toResponse(m);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ManagerResponse me() {
        return toResponse(currentManager.require());
    }

    private void authenticate(String email, String password,
                              HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static ManagerResponse toResponse(Manager m) {
        return new ManagerResponse(m.getId(), m.getEmail(), m.getDisplayName());
    }
}
