package com.example.backend.service;

import com.example.backend.dto.*;
import com.example.backend.mapper.ReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
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

    @Test
    void exportWritesFourSheetsAndSummaryHeader() throws Exception {
        stubEmptySummaryWindow();
        byte[] bytes = service.export("2026-09-01", "2026-09-01");
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(4, wb.getNumberOfSheets());
            assertEquals("汇总", wb.getSheetName(0));
            assertEquals("工单明细", wb.getSheetName(1));
            assertEquals("评价按施救员", wb.getSheetName(2));
            assertEquals("扣留按停车场", wb.getSheetName(3));
            assertEquals("指标", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("数值", wb.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
            assertEquals("工单总数", wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("已完成", wb.getSheetAt(0).getRow(2).getCell(0).getStringCellValue());
            assertEquals("处置中", wb.getSheetAt(0).getRow(3).getCell(0).getStringCellValue());
            assertEquals("中止", wb.getSheetAt(0).getRow(4).getCell(0).getStringCellValue());
            assertEquals("已评价数", wb.getSheetAt(0).getRow(5).getCell(0).getStringCellValue());
            assertEquals("到达及时均分", wb.getSheetAt(0).getRow(6).getCell(0).getStringCellValue());
            assertEquals("处置规范均分", wb.getSheetAt(0).getRow(7).getCell(0).getStringCellValue());
            assertEquals("操作安全均分", wb.getSheetAt(0).getRow(8).getCell(0).getStringCellValue());
            assertEquals("服务态度均分", wb.getSheetAt(0).getRow(9).getCell(0).getStringCellValue());
            assertEquals("入库", wb.getSheetAt(0).getRow(10).getCell(0).getStringCellValue());
            assertEquals("在场", wb.getSheetAt(0).getRow(11).getCell(0).getStringCellValue());
            assertEquals("出库", wb.getSheetAt(0).getRow(12).getCell(0).getStringCellValue());

            assertEquals("单号", wb.getSheetAt(1).getRow(0).getCell(0).getStringCellValue());
            assertEquals("事故地址", wb.getSheetAt(1).getRow(0).getCell(1).getStringCellValue());
            assertEquals("状态", wb.getSheetAt(1).getRow(0).getCell(2).getStringCellValue());
            assertEquals("车牌", wb.getSheetAt(1).getRow(0).getCell(3).getStringCellValue());
            assertEquals("施救员", wb.getSheetAt(1).getRow(0).getCell(4).getStringCellValue());
            assertEquals("创建时间", wb.getSheetAt(1).getRow(0).getCell(5).getStringCellValue());
            assertEquals("派单时间", wb.getSheetAt(1).getRow(0).getCell(6).getStringCellValue());
            assertEquals("完成时间", wb.getSheetAt(1).getRow(0).getCell(7).getStringCellValue());

            assertEquals("施救员", wb.getSheetAt(2).getRow(0).getCell(0).getStringCellValue());
            assertEquals("评价单数", wb.getSheetAt(2).getRow(0).getCell(1).getStringCellValue());
            assertEquals("到达及时", wb.getSheetAt(2).getRow(0).getCell(2).getStringCellValue());
            assertEquals("处置规范", wb.getSheetAt(2).getRow(0).getCell(3).getStringCellValue());
            assertEquals("操作安全", wb.getSheetAt(2).getRow(0).getCell(4).getStringCellValue());
            assertEquals("服务态度", wb.getSheetAt(2).getRow(0).getCell(5).getStringCellValue());
            assertEquals("综合均分", wb.getSheetAt(2).getRow(0).getCell(6).getStringCellValue());

            assertEquals("停车场", wb.getSheetAt(3).getRow(0).getCell(0).getStringCellValue());
            assertEquals("入库", wb.getSheetAt(3).getRow(0).getCell(1).getStringCellValue());
            assertEquals("在场", wb.getSheetAt(3).getRow(0).getCell(2).getStringCellValue());
            assertEquals("出库", wb.getSheetAt(3).getRow(0).getCell(3).getStringCellValue());
        }
    }

    private void stubEmptySummaryWindow() {
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
    }

    private static ReportStatusCount status(String st, long n) {
        ReportStatusCount c = new ReportStatusCount();
        c.setStatus(st);
        c.setCount(n);
        return c;
    }
}
