package com.example.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "vue-springboot-rbac-jwt-secret-change-me-32bytes");
        ReflectionTestUtils.setField(provider, "expirationMs", 7200000L);
    }

    @Test
    void generateAndParseToken() {
        CustomUserDetails user = new CustomUserDetails(
                1L, "admin", "encoded", true, Collections.emptyList());
        String token = provider.generateToken(user);
        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertEquals("admin", provider.getUsername(token));
    }
}
