package com.example.backend.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private JwtTokenProvider tokenProvider;
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        tokenProvider = mock(JwtTokenProvider.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationWhenBearerTokenValid() throws Exception {
        CustomUserDetails user = new CustomUserDetails(1L, "admin", "pw", true, Collections.emptyList());
        when(tokenProvider.validateToken("good.jwt")).thenReturn(true);
        when(tokenProvider.getUsername("good.jwt")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain).doFilter(request, response);
    }

    @Test
    void skipsAuthenticationWhenNoBearerHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }
}
