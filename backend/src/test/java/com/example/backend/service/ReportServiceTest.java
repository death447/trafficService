package com.example.backend.service;

import com.example.backend.dto.*;
import com.example.backend.mapper.ReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

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

    @Test
    void summaryFillsCountsTrendStatusAndRoundsScores() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 3, 0, 0);
        when(reportMapper.countOrders(start, end)).thenReturn(4L);
        when(reportMapper.countOrdersByStatus(start, end)).thenReturn(List.of(
                status("COMPLETED", 1),
                status("PENDING", 1),
                status("ABORTED", 1),
                status("ACCEPTED", 1)
        ));
        ReportDateCount day = new ReportDateCount();
        day.setDate("2026-09-01");
        day.setCount(3);
        when(reportMapper.countOrdersByDay(start, end)).thenReturn(List.of(day));
        when(reportMapper.listOrders(start, end)).thenReturn(List.of());
        ReportQualityAvg avg = new ReportQualityAvg();
        avg.setRatedCount(1);
        avg.setAvgPunctual(4.14);
        avg.setAvgStandard(5.0);
        avg.setAvgSafety(4.85);
        avg.setAvgAttitude(3.0);
        when(reportMapper.selectQualityAvg(start, end)).thenReturn(avg);
        ReportRescuerQuality row = new ReportRescuerQuality();
        row.setRescuerId(3L);
        row.setRescuerName("张三");
        row.setRatedCount(1);
        row.setAvgPunctual(4.14);
        row.setAvgStandard(5.0);
        row.setAvgSafety(4.85);
        row.setAvgAttitude(3.0);
        when(reportMapper.listQualityByRescuer(start, end)).thenReturn(List.of(row));
        when(reportMapper.countDetainInbound(start, end)).thenReturn(2L);
        when(reportMapper.countDetainInYard(start, end)).thenReturn(1L);
        when(reportMapper.countDetainOutbound(start, end)).thenReturn(3L);
        when(reportMapper.listDetainByLot(start, end)).thenReturn(List.of());

        ReportSummary s = service.summary("2026-09-01", "2026-09-02");
        assertEquals(4, s.getDispatch().getTotal());
        assertEquals(1, s.getDispatch().getCompleted());
        assertEquals(2, s.getDispatch().getInProgress());
        assertEquals(1, s.getDispatch().getAborted());
        assertEquals(2, s.getDispatch().getTrend().size());
        assertEquals("2026-09-01", s.getDispatch().getTrend().get(0).getDate());
        assertEquals(3, s.getDispatch().getTrend().get(0).getCount());
        assertEquals("2026-09-02", s.getDispatch().getTrend().get(1).getDate());
        assertEquals(0, s.getDispatch().getTrend().get(1).getCount());
        assertEquals(5, s.getDispatch().getStatusDist().size());
        assertEquals("PENDING", s.getDispatch().getStatusDist().get(0).getStatus());
        assertEquals(1, s.getQuality().getRatedCount());
        assertEquals(4.1, s.getQuality().getAvgPunctual());
        assertEquals(5.0, s.getQuality().getAvgStandard());
        assertEquals(4.9, s.getQuality().getAvgSafety());
        assertEquals(3.0, s.getQuality().getAvgAttitude());
        assertEquals(4.3, s.getQuality().getByRescuer().get(0).getAvgOverall());
        assertEquals(2, s.getDetain().getInbound());
        assertEquals(1, s.getDetain().getInYard());
        assertEquals(3, s.getDetain().getOutbound());
    }

    @Test
    void summaryNullAveragesWhenNoRatings() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 2, 0, 0);
        when(reportMapper.countOrders(start, end)).thenReturn(0L);
        when(reportMapper.countOrdersByStatus(start, end)).thenReturn(List.of());
        when(reportMapper.countOrdersByDay(start, end)).thenReturn(List.of());
        when(reportMapper.listOrders(start, end)).thenReturn(List.of());
        ReportQualityAvg avg = new ReportQualityAvg();
        avg.setRatedCount(0);
        when(reportMapper.selectQualityAvg(start, end)).thenReturn(avg);
        when(reportMapper.listQualityByRescuer(start, end)).thenReturn(List.of());
        when(reportMapper.countDetainInbound(start, end)).thenReturn(0L);
        when(reportMapper.countDetainInYard(start, end)).thenReturn(0L);
        when(reportMapper.countDetainOutbound(start, end)).thenReturn(0L);
        when(reportMapper.listDetainByLot(start, end)).thenReturn(List.of());

        ReportSummary s = service.summary("2026-09-01", "2026-09-01");
        assertNull(s.getQuality().getAvgPunctual());
        assertEquals(1, s.getDispatch().getTrend().size());
        assertEquals(0, s.getDispatch().getTrend().get(0).getCount());
    }

    private static ReportStatusCount status(String st, long n) {
        ReportStatusCount c = new ReportStatusCount();
        c.setStatus(st);
        c.setCount(n);
        return c;
    }
}
