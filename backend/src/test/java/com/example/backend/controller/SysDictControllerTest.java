package com.example.backend.controller;

import com.example.backend.common.PageParams;
import com.example.backend.entity.SysDictItem;
import com.example.backend.security.CustomUserDetailsService;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.service.SysDictService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SysDictController.class)
@AutoConfigureMockMvc(addFilters = false)
class SysDictControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysDictService dictService;
    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void enabledReturnsOptions() throws Exception {
        SysDictItem row = new SysDictItem();
        row.setId(1L);
        row.setDictType("DETAIN_DEPT");
        row.setName("福田交警大队");
        row.setStatus("ENABLED");
        when(dictService.listEnabled("DETAIN_DEPT")).thenReturn(List.of(row));

        mockMvc.perform(get("/api/dict/enabled").param("type", "DETAIN_DEPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("福田交警大队"));
    }

    @Test
    void enabledReturnsErrorForUnknownType() throws Exception {
        when(dictService.listEnabled("NOPE")).thenThrow(new RuntimeException("字典类型无效"));

        mockMvc.perform(get("/api/dict/enabled").param("type", "NOPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("字典类型无效"));
    }

    @Test
    void createPassesRequestToService() throws Exception {
        when(dictService.create(any())).thenReturn(true);

        mockMvc.perform(post("/api/dict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dictType":"RESCUE_METHOD","name":"拖车","sortOrder":1,\
                                "status":"ENABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(dictService).create(any());
    }

    @Test
    void listUsesNormalizedPageParams() throws Exception {
        when(dictService.list(eq("DETAIN_DEPT"), eq("ENABLED"), any(PageParams.class)))
                .thenReturn(Map.of("list", List.of(), "total", 0, "page", 1, "size", 10));

        mockMvc.perform(get("/api/dict/list")
                        .param("type", "DETAIN_DEPT")
                        .param("status", "ENABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
