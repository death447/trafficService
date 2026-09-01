package com.example.backend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserRequestTest {

    @Test
    void deserializesIntegerRoleIdsAsLong() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UserRequest request = mapper.readValue(
                "{\"username\":\"carol\",\"password\":\"secret\",\"roleIds\":[5]}",
                UserRequest.class);

        assertEquals(List.of(5L), request.getRoleIds());
        assertEquals(Long.class, request.getRoleIds().get(0).getClass());
    }
}
