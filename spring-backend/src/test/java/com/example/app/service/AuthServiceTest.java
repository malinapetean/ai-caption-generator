package com.example.app.service;

import java.util.Optional;

import com.example.app.dto.auth.AuthResponse;
import com.example.app.dto.auth.LoginRequest;
import com.example.app.dto.auth.RegisterRequest;
import com.example.app.exception.BadRequestException;
import com.example.app.model.AppUser;
import com.example.app.repository.AppUserRepository;
import com.example.app.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_normalizesEmailAndUsesDefaultStyleWhenBlank() {
        RegisterRequest request = new RegisterRequest(" User@Example.com ", "password123", "   ");

        when(appUserRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(7L);
            return user;
        });
        when(jwtService.generateToken(any(AppUser.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());

        AppUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getPreferredStyle()).isEqualTo("casual");

        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.preferredStyle()).isEqualTo("casual");
        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void register_withDuplicateEmail_throwsBadRequest() {
        RegisterRequest request = new RegisterRequest("user@example.com", "password123", "travel");

        when(appUserRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email is already registered.");

        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void login_authenticatesWithNormalizedEmailAndReturnsToken() {
        LoginRequest request = new LoginRequest("USER@EXAMPLE.COM ", "password123");
        AppUser user = new AppUser();
        user.setId(3L);
        user.setEmail("user@example.com");
        user.setPreferredStyle("travel");

        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> authCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(authCaptor.capture());

        UsernamePasswordAuthenticationToken token = authCaptor.getValue();
        assertThat(token.getPrincipal()).isEqualTo("user@example.com");
        assertThat(token.getCredentials()).isEqualTo("password123");

        assertThat(response.userId()).isEqualTo(3L);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.preferredStyle()).isEqualTo("travel");
        assertThat(response.token()).isEqualTo("jwt-token");
    }
}
