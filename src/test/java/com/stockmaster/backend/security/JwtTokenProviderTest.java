package com.stockmaster.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "qa-only-secret-for-unit-tests-not-for-production-1234567890");
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 60000L);
    }

    @Test
    void tokenValidoConservaIdentidad() {
        String token = provider.generateTokenFromUsername("qa@example.test");
        assertTrue(provider.validateToken(token));
        assertEquals("qa@example.test", provider.getUsernameFromJwt(token));
    }

    @Test
    void tokenExpiradoNoEsValido() {
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", -60000L);
        assertFalse(provider.validateToken(provider.generateTokenFromUsername("qa@example.test")));
    }

    @Test
    void tokenVacioNoEsValido() {
        assertFalse(provider.validateToken(""));
    }
}
