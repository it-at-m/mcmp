package de.muenchen.mcmp.clients.report;

import de.muenchen.mcmp.security.HasApiRole;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/ping")
    public String ping() {
        return "Report API is accessible";
    }

    @GetMapping
    @HasApiRole
    public List<ReportServerDTO> getReport() {
        return reportService.buildReport();
    }
}