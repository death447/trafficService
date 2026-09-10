package com.example.backend.service;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.MeResponse;
import com.example.backend.dto.RescuerProfileUpdateRequest;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void getMeReturnsPublicFieldsWithoutPassword() {
        User user = new User();
        user.setId(4L);
        user.setUsername("parkingadmin");
        user.setRealName("停车场演示");
        user.setPhone("13800000004");
        user.setEmail("parking@example.com");
        user.setPassword("secret-hash");
        when(userMapper.findById(4L)).thenReturn(user);

        MeResponse me = authService.getMe(4L);

        assertEquals(4L, me.getId());
        assertEquals("parkingadmin", me.getUsername());
        assertEquals("停车场演示", me.getRealName());
        assertEquals("13800000004", me.getPhone());
        assertEquals("parking@example.com", me.getEmail());
    }

    @Test
    void getMeThrowsWhenMissing() {
        when(userMapper.findById(9L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.getMe(9L));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void updateMePatchesProfileFields() {
        User user = new User();
        user.setId(4L);
        user.setUsername("parkingadmin");
        user.setPassword("secret-hash");
        when(userMapper.findById(4L)).thenReturn(user);
        when(userMapper.update(any(User.class))).thenReturn(1);

        RescuerProfileUpdateRequest req = new RescuerProfileUpdateRequest();
        req.setRealName("新名");
        req.setPhone("13900000000");
        req.setEmail("n@example.com");

        MeResponse me = authService.updateMe(4L, req);

        assertEquals("新名", user.getRealName());
        assertEquals("13900000000", user.getPhone());
        assertEquals("n@example.com", user.getEmail());
        verify(userMapper).update(user);
        assertEquals("新名", me.getRealName());
    }
}
