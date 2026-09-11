package com.example.backend.service;

import com.example.backend.dto.ReportDateCount;
import com.example.backend.dto.ReportLotDetain;
import com.example.backend.dto.ReportOrderRow;
import com.example.backend.dto.ReportQualityAvg;
import com.example.backend.dto.ReportRescuerQuality;
import com.example.backend.dto.ReportStatusCount;
import com.example.backend.dto.ReportSummary;
import com.example.backend.dto.ReportSummary.DetainBlock;
import com.example.backend.dto.ReportSummary.DispatchBlock;
import com.example.backend.dto.ReportSummary.QualityBlock;
import com.example.backend.mapper.ReportMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private static final String UNSPECIFIED_RESCUER = "未指定";
    private static final String[] STATUS_ORDER = {
            "PENDING", "DISPATCHED", "ACCEPTED", "COMPLETED", "ABORTED"
    };
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<String, String> STATUS_ZH = Map.of(
            "PENDING", "待派单",
            "DISPATCHED", "已派单",
            "ACCEPTED", "已接单",
            "COMPLETED", "已完成",
            "ABORTED", "已中止"
    );

    @Autowired
    private ReportMapper reportMapper;

    public ReportSummary summary(String from, String to) {
        LocalDate[] range = parseRange(from, to);
        LocalDate fromDate = range[0];
        LocalDate toDate = range[1];
        LocalDateTime startDt = fromDate.atStartOfDay();
        LocalDateTime endDt = toDate.plusDays(1).atStartOfDay();

        ReportSummary out = new ReportSummary();
        out.setFrom(fromDate.toString());
        out.setTo(toDate.toString());
        fillDispatch(out.getDispatch(), fromDate, toDate, startDt, endDt);
        fillQuality(out.getQuality(), startDt, endDt);
        fillDetain(out.getDetain(), startDt, endDt);
        return out;
    }

    public byte[] export(String from, String to) {
        ReportSummary s = summary(from, to);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeSummarySheet(wb.createSheet("汇总"), s);
            writeOrdersSheet(wb.createSheet("工单明细"), s);
            writeQualitySheet(wb.createSheet("评价按施救员"), s);
            writeDetainSheet(wb.createSheet("扣留按停车场"), s);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("导出失败", e);
        }
    }

    LocalDate[] parseRange(String from, String to) {
        LocalDate start;
        LocalDate end;
        try {
            if (from == null || to == null || from.isBlank() || to.isBlank()) {
                throw new DateTimeParseException("blank", "", 0);
            }
            start = LocalDate.parse(from.trim());
            end = LocalDate.parse(to.trim());
        } catch (DateTimeParseException e) {
            throw new RuntimeException("请选择开始和结束日期");
        }
        if (start.isAfter(end)) {
            throw new RuntimeException("开始日期不能晚于结束日期");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (inclusiveDays > 366) {
            throw new RuntimeException("查询区间不能超过 366 天");
        }
        return new LocalDate[] { start, end };
    }

    private void fillDispatch(DispatchBlock dispatch, LocalDate fromDate, LocalDate toDate,
                              LocalDateTime startDt, LocalDateTime endDt) {
        dispatch.setTotal(reportMapper.countOrders(startDt, endDt));

        Map<String, Long> statusMap = new HashMap<>();
        List<ReportStatusCount> byStatus = reportMapper.countOrdersByStatus(startDt, endDt);
        if (byStatus != null) {
            for (ReportStatusCount row : byStatus) {
                if (row.getStatus() != null) {
                    statusMap.put(row.getStatus(), row.getCount());
                }
            }
        }
        dispatch.setCompleted(statusMap.getOrDefault("COMPLETED", 0L));
        dispatch.setInProgress(statusMap.getOrDefault("PENDING", 0L)
                + statusMap.getOrDefault("DISPATCHED", 0L)
                + statusMap.getOrDefault("ACCEPTED", 0L));
        dispatch.setAborted(statusMap.getOrDefault("ABORTED", 0L));

        List<ReportStatusCount> statusDist = new ArrayList<>();
        for (String status : STATUS_ORDER) {
            ReportStatusCount item = new ReportStatusCount();
            item.setStatus(status);
            item.setCount(statusMap.getOrDefault(status, 0L));
            statusDist.add(item);
        }
        dispatch.setStatusDist(statusDist);

        Map<String, Long> dayMap = new HashMap<>();
        List<ReportDateCount> byDay = reportMapper.countOrdersByDay(startDt, endDt);
        if (byDay != null) {
            for (ReportDateCount row : byDay) {
                if (row.getDate() != null) {
                    dayMap.put(row.getDate(), row.getCount());
                }
            }
        }
        List<ReportDateCount> trend = new ArrayList<>();
        for (LocalDate day = fromDate; !day.isAfter(toDate); day = day.plusDays(1)) {
            ReportDateCount item = new ReportDateCount();
            item.setDate(day.toString());
            item.setCount(dayMap.getOrDefault(day.toString(), 0L));
            trend.add(item);
        }
        dispatch.setTrend(trend);

        List<ReportOrderRow> orders = reportMapper.listOrders(startDt, endDt);
        if (orders == null) {
            orders = new ArrayList<>();
        }
        for (ReportOrderRow order : orders) {
            order.setRescuerName(blankToUnspecified(order.getRescuerName()));
        }
        dispatch.setOrders(orders);
    }

    private void fillQuality(QualityBlock quality, LocalDateTime startDt, LocalDateTime endDt) {
        ReportQualityAvg avg = reportMapper.selectQualityAvg(startDt, endDt);
        if (avg == null || avg.getRatedCount() == 0) {
            quality.setRatedCount(avg == null ? 0 : avg.getRatedCount());
            quality.setAvgPunctual(null);
            quality.setAvgStandard(null);
            quality.setAvgSafety(null);
            quality.setAvgAttitude(null);
        } else {
            quality.setRatedCount(avg.getRatedCount());
            quality.setAvgPunctual(round1(avg.getAvgPunctual()));
            quality.setAvgStandard(round1(avg.getAvgStandard()));
            quality.setAvgSafety(round1(avg.getAvgSafety()));
            quality.setAvgAttitude(round1(avg.getAvgAttitude()));
        }

        List<ReportRescuerQuality> byRescuer = reportMapper.listQualityByRescuer(startDt, endDt);
        if (byRescuer == null) {
            byRescuer = new ArrayList<>();
        }
        for (ReportRescuerQuality row : byRescuer) {
            row.setRescuerName(blankToUnspecified(row.getRescuerName()));
            Double punctual = round1(row.getAvgPunctual());
            Double standard = round1(row.getAvgStandard());
            Double safety = round1(row.getAvgSafety());
            Double attitude = round1(row.getAvgAttitude());
            row.setAvgPunctual(punctual);
            row.setAvgStandard(standard);
            row.setAvgSafety(safety);
            row.setAvgAttitude(attitude);
            if (punctual == null || standard == null || safety == null || attitude == null) {
                row.setAvgOverall(null);
            } else {
                row.setAvgOverall(round1((punctual + standard + safety + attitude) / 4.0));
            }
        }
        quality.setByRescuer(byRescuer);
    }

    private void fillDetain(DetainBlock detain, LocalDateTime startDt, LocalDateTime endDt) {
        detain.setInbound(reportMapper.countDetainInbound(startDt, endDt));
        detain.setInYard(reportMapper.countDetainInYard(startDt, endDt));
        detain.setOutbound(reportMapper.countDetainOutbound(startDt, endDt));
        List<ReportLotDetain> byLot = reportMapper.listDetainByLot(startDt, endDt);
        detain.setByLot(byLot == null ? new ArrayList<>() : byLot);
    }

    private static Double round1(Double v) {
        if (v == null) {
            return null;
        }
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String blankToUnspecified(String name) {
        if (name == null || name.isBlank()) {
            return UNSPECIFIED_RESCUER;
        }
        return name;
    }

    private static void writeSummarySheet(Sheet sheet, ReportSummary s) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("指标");
        header.createCell(1).setCellValue("数值");
        writeMetric(sheet, 1, "工单总数", s.getDispatch().getTotal());
        writeMetric(sheet, 2, "已完成", s.getDispatch().getCompleted());
        writeMetric(sheet, 3, "处置中", s.getDispatch().getInProgress());
        writeMetric(sheet, 4, "中止", s.getDispatch().getAborted());
        writeMetric(sheet, 5, "已评价数", s.getQuality().getRatedCount());
        writeMetric(sheet, 6, "到达及时均分", s.getQuality().getAvgPunctual());
        writeMetric(sheet, 7, "处置规范均分", s.getQuality().getAvgStandard());
        writeMetric(sheet, 8, "操作安全均分", s.getQuality().getAvgSafety());
        writeMetric(sheet, 9, "服务态度均分", s.getQuality().getAvgAttitude());
        writeMetric(sheet, 10, "入库", s.getDetain().getInbound());
        writeMetric(sheet, 11, "在场", s.getDetain().getInYard());
        writeMetric(sheet, 12, "出库", s.getDetain().getOutbound());
    }

    private static void writeOrdersSheet(Sheet sheet, ReportSummary s) {
        writeHeader(sheet, "单号", "事故地址", "状态", "车牌", "施救员", "创建时间", "派单时间", "完成时间");
        int r = 1;
        for (ReportOrderRow order : s.getDispatch().getOrders()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(nullToEmpty(order.getOrderNo()));
            row.createCell(1).setCellValue(nullToEmpty(order.getAccidentAddress()));
            row.createCell(2).setCellValue(statusZh(order.getStatus()));
            row.createCell(3).setCellValue(nullToEmpty(order.getPlateNo()));
            row.createCell(4).setCellValue(nullToEmpty(order.getRescuerName()));
            row.createCell(5).setCellValue(formatTime(order.getCreateTime()));
            row.createCell(6).setCellValue(formatTime(order.getDispatchedAt()));
            row.createCell(7).setCellValue(formatTime(order.getCompletedAt()));
        }
    }

    private static void writeQualitySheet(Sheet sheet, ReportSummary s) {
        writeHeader(sheet, "施救员", "评价单数", "到达及时", "处置规范", "操作安全", "服务态度", "综合均分");
        int r = 1;
        for (ReportRescuerQuality q : s.getQuality().getByRescuer()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(nullToEmpty(q.getRescuerName()));
            row.createCell(1).setCellValue(q.getRatedCount());
            writeNumberOrBlank(row, 2, q.getAvgPunctual());
            writeNumberOrBlank(row, 3, q.getAvgStandard());
            writeNumberOrBlank(row, 4, q.getAvgSafety());
            writeNumberOrBlank(row, 5, q.getAvgAttitude());
            writeNumberOrBlank(row, 6, q.getAvgOverall());
        }
    }

    private static void writeDetainSheet(Sheet sheet, ReportSummary s) {
        writeHeader(sheet, "停车场", "入库", "在场", "出库");
        int r = 1;
        for (ReportLotDetain lot : s.getDetain().getByLot()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(nullToEmpty(lot.getParkingLotName()));
            row.createCell(1).setCellValue(lot.getInbound());
            row.createCell(2).setCellValue(lot.getInYard());
            row.createCell(3).setCellValue(lot.getOutbound());
        }
    }

    private static void writeHeader(Sheet sheet, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            row.createCell(i).setCellValue(titles[i]);
        }
    }

    private static void writeMetric(Sheet sheet, int rowIndex, String name, long value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(value);
    }

    private static void writeMetric(Sheet sheet, int rowIndex, String name, Double value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(name);
        writeNumberOrBlank(row, 1, value);
    }

    private static void writeNumberOrBlank(Row row, int col, Double value) {
        if (value == null) {
            row.createCell(col);
        } else {
            row.createCell(col).setCellValue(value);
        }
    }

    private static String statusZh(String status) {
        if (status == null) {
            return "";
        }
        return STATUS_ZH.getOrDefault(status, status);
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(EXPORT_TIME);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
