package com.example.backend.controller;

import com.example.backend.entity.User;
import com.example.backend.security.CustomUserDetailsService;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void createUserAcceptsNumericRoleIdsAsLong() throws Exception {
        when(userService.createUser(any(User.class), any())).thenReturn(true);

        mockMvc.perform(post("/api/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol","email":"c@x.com","password":"secret",\
                                "phone":"","realName":"Carol","status":1,"roleIds":[5]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> roleIdsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).createUser(userCaptor.capture(), roleIdsCaptor.capture());

        List<Long> roleIds = roleIdsCaptor.getValue();
        assertEquals(List.of(5L), roleIds);
        assertEquals(Long.class, roleIds.get(0).getClass());
        assertEquals("carol", userCaptor.getValue().getUsername());
        assertEquals("secret", userCaptor.getValue().getPassword());
    }
}
