package com.example.app.service;

import com.example.app.dto.auth.AuthResponse;
import com.example.app.dto.auth.LoginRequest;
import com.example.app.dto.auth.RegisterRequest;
import com.example.app.exception.BadRequestException;
import com.example.app.model.AppUser;
import com.example.app.repository.AppUserRepository;
import com.example.app.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("Email is already registered.");
        }

        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPreferredStyle(StringUtils.hasText(request.preferredStyle()) ? request.preferredStyle().trim() : "casual");

        AppUser savedUser = appUserRepository.save(user);
        String token = jwtService.generateToken(savedUser);
        return new AuthResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getPreferredStyle(), token);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadRequestException("Invalid credentials."));
        String token = jwtService.generateToken(user);
        return new AuthResponse(user.getId(), user.getEmail(), user.getPreferredStyle(), token);
    }
}
