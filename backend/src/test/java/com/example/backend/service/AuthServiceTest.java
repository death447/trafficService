package com.example.backend.service;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.entity.Role;
import com.example.backend.mapper.UserMapper;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private UserMapper userMapper;
    @InjectMocks
    private AuthService authService;

    @Test
    void loginReturnsTokenUserIdUsernamePermissionsAndRoles() {
        CustomUserDetails user = new CustomUserDetails(
                1L, "admin", "encoded", true,
                List.of(new SimpleGrantedAuthority("user:list")));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(user);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken(user)).thenReturn("jwt-token");
        Role role = new Role();
        role.setRoleCode("ADMIN");
        when(userMapper.findRolesByUserId(1L)).thenReturn(List.of(role));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        LoginResponse response = authService.login(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals(1L, response.getUserId());
        assertEquals("admin", response.getUsername());
        assertEquals(List.of("user:list"), response.getPermissions());
        assertEquals(List.of("ADMIN"), response.getRoles());
    }
}
