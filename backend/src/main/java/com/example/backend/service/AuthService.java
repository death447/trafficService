package com.example.backend.service;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.entity.Role;
import com.example.backend.mapper.UserMapper;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private UserMapper userMapper;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        String token = tokenProvider.generateToken(user);
        List<String> permissions = user.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toList());
        List<String> roles = userMapper.findRolesByUserId(user.getId()).stream()
                .map(Role::getRoleCode).collect(Collectors.toList());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .permissions(permissions)
                .roles(roles)
                .build();
    }
}
