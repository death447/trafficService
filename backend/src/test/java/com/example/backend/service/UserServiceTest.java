package com.example.backend.service;

import com.example.backend.entity.User;
import com.example.backend.mapper.UserMapper;
import com.example.backend.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    @Test
    void createUserEncodesPasswordBeforeInsert() {
        User user = new User();
        user.setUsername("alice");
        user.setPassword("plain-secret");
        when(passwordEncoder.encode("plain-secret")).thenReturn("$2a$10$encoded");
        when(userMapper.insert(user)).thenAnswer(invocation -> {
            user.setId(10L);
            return 1;
        });

        userService.createUser(user, List.of(2L));

        verify(passwordEncoder).encode("plain-secret");
        assertEquals("$2a$10$encoded", user.getPassword());
        verify(userMapper).insert(user);
        verify(userRoleMapper).insert(10L, 2L);
    }

    @Test
    void updateUserEncodesPasswordWhenNonBlank() {
        User user = new User();
        user.setId(3L);
        user.setPassword("new-secret");
        when(passwordEncoder.encode("new-secret")).thenReturn("$2a$10$newEncoded");
        when(userMapper.update(user)).thenReturn(1);

        userService.updateUser(user, null);

        verify(passwordEncoder).encode("new-secret");
        assertEquals("$2a$10$newEncoded", user.getPassword());
        verify(userMapper).update(user);
        verify(userMapper, never()).findById(any());
    }

    @Test
    void updateUserKeepsExistingPasswordWhenBlank() {
        User user = new User();
        user.setId(3L);
        user.setPassword("   ");
        User existing = new User();
        existing.setId(3L);
        existing.setPassword("$2a$10$alreadyHashed");
        when(userMapper.findById(3L)).thenReturn(existing);
        when(userMapper.update(user)).thenReturn(1);

        userService.updateUser(user, List.of());

        verify(passwordEncoder, never()).encode(anyString());
        assertEquals("$2a$10$alreadyHashed", user.getPassword());
        verify(userMapper).update(user);
    }
}
