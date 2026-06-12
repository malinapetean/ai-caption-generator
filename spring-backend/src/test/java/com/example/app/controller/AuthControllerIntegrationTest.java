package com.example.app.controller;

import java.util.UUID;

import com.example.app.model.AppUser;
import com.example.app.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        appUserRepository.deleteAll();
    }

    @Test
    void register_createsUserWithNormalizedEmailAndDefaultStyle() throws Exception {
        String email = "USER-" + UUID.randomUUID() + "@Example.com";
        String password = "supersecret";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "preferredStyle": "   "
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.preferredStyle").value("casual"))
                .andExpect(jsonPath("$.token").isString());

        AppUser savedUser = appUserRepository.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(savedUser.getPreferredStyle()).isEqualTo("casual");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, savedUser.getPasswordHash())).isTrue();
    }

    @Test
    void login_authenticatesExistingUserAndReturnsToken() throws Exception {
        String email = ("login-" + UUID.randomUUID() + "@example.com").toLowerCase();
        String password = "password123";

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPreferredStyle("travel");
        AppUser savedUser = appUserRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email.toUpperCase(), password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(savedUser.getId()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.preferredStyle").value("travel"))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void register_withDuplicateEmail_returnsBadRequest() throws Exception {
        String email = ("duplicate-" + UUID.randomUUID() + "@example.com").toLowerCase();

        AppUser existingUser = new AppUser();
        existingUser.setEmail(email);
        existingUser.setPasswordHash(passwordEncoder.encode("password123"));
        existingUser.setPreferredStyle("casual");
        appUserRepository.save(existingUser);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "preferredStyle": "poetic"
                                }
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered."));
    }
}
