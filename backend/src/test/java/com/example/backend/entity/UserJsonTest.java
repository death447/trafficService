package com.example.backend.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UserJsonTest {

    @Test
    void serializedUserOmitsPasswordHash() throws Exception {
        User user = new User();
        user.setUsername("alice");
        user.setPassword("$2a$10$hashedSecret");

        String json = new ObjectMapper().writeValueAsString(user);

        assertFalse(json.contains("password"));
        assertFalse(json.contains("$2a$10$hashedSecret"));
    }
}
