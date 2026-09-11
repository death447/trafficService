package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.ReportSummary;
import com.example.backend.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/report")
@CrossOrigin(origins = "*")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<ReportSummary> summary(@RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        try {
            return Result.success(reportService.summary(from, to));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('report:query')")
    public void export(@RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       HttpServletResponse response) throws Exception {
        try {
            byte[] bytes = reportService.export(from, to);
            String filename = "report_" + from + "_" + to + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        } catch (RuntimeException e) {
            response.setStatus(200);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String message = e.getMessage() != null ? e.getMessage() : "导出失败";
            response.getWriter().write("{\"code\":400,\"message\":\"" +
                    message.replace("\"", "'") + "\",\"data\":null}");
        }
    }
}
