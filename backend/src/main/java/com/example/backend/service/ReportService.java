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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        throw new UnsupportedOperationException("export");
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
}
