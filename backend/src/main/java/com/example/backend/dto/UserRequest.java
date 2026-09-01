package com.example.backend.dto;

import com.example.backend.entity.User;
import lombok.Data;

import java.util.List;

@Data
public class UserRequest {
    private String username;
    private String email;
    private String password;
    private String phone;
    private String realName;
    private Integer status;
    private List<Long> roleIds;

    public User toUser() {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        user.setPhone(phone);
        user.setRealName(realName);
        user.setStatus(status != null ? status : 1);
        return user;
    }
}
