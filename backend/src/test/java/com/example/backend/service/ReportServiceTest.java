package com.example.backend.service;

import com.example.backend.mapper.ReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportMapper reportMapper;
    @InjectMocks ReportService service;

    @Test
    void summaryRejectsBlankDates() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> service.summary(null, "2026-09-01"));
        assertEquals("请选择开始和结束日期", e.getMessage());
        e = assertThrows(RuntimeException.class, () -> service.summary("2026-09-01", " "));
        assertEquals("请选择开始和结束日期", e.getMessage());
        e = assertThrows(RuntimeException.class, () -> service.summary("2026-13-01", "2026-09-01"));
        assertEquals("请选择开始和结束日期", e.getMessage());
    }

    @Test
    void summaryRejectsFromAfterTo() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.summary("2026-09-12", "2026-09-01"));
        assertEquals("开始日期不能晚于结束日期", e.getMessage());
    }

    @Test
    void summaryRejectsSpanOver366Days() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.summary("2025-09-01", "2026-09-02"));
        assertEquals("查询区间不能超过 366 天", e.getMessage());
    }
}
