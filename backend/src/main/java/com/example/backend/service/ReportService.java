package com.example.backend.service;

import com.example.backend.dto.ReportSummary;
import com.example.backend.mapper.ReportMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Service
public class ReportService {

    @Autowired
    private ReportMapper reportMapper;

    public ReportSummary summary(String from, String to) {
        LocalDate[] range = parseRange(from, to);
        ReportSummary out = new ReportSummary();
        out.setFrom(range[0].toString());
        out.setTo(range[1].toString());
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
}
