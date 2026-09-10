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

    public MeResponse getMe(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return toMe(user);
    }

    public MeResponse updateMe(Long userId, RescuerProfileUpdateRequest req) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (req != null) {
            if (req.getRealName() != null) {
                user.setRealName(req.getRealName());
            }
            if (req.getPhone() != null) {
                user.setPhone(req.getPhone());
            }
            if (req.getEmail() != null) {
                user.setEmail(req.getEmail());
            }
        }
        userMapper.update(user);
        return toMe(user);
    }

    private static MeResponse toMe(User user) {
        return MeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .build();
    }
}
