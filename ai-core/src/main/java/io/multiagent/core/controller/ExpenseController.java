package io.multiagent.core.controller;

import io.multiagent.core.model.ExpenseReportResponse;
import io.multiagent.core.service.ExpenseReportService;
import io.multiagent.core.service.ExpensePdfService;
import io.multiagent.core.service.WeaviateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
@Slf4j
public class ExpenseController {

    private final ExpenseReportService reportService;
    private final ExpensePdfService expensePdfService;
    private final WeaviateService weaviateService;

    @GetMapping("/report")
    public ExpenseReportResponse report(
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "company", required = false) String company
    ) {
        log.info("📊 /expenses/report start={} end={} type={} currency={} company={}", start, end, type, currency, company);
        try {
            return reportService.report(start, end, type, currency, company);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping(value = "/report/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> reportExcel(
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "company", required = false) String company
    ) {
        log.info("📊 /expenses/report/excel start={} end={} type={} currency={} company={}", start, end, type, currency, company);
        byte[] bytes;
        try {
            bytes = reportService.buildExcel(start, end, type, currency, company);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rapport-notes-frais.xlsx\"");
        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }

    @GetMapping(value = "/report/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> reportPdf(
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "company", required = false) String company
    ) {
        log.info("📊 /expenses/report/pdf start={} end={} type={} currency={} company={}", start, end, type, currency, company);
        byte[] bytes;
        try {
            bytes = expensePdfService.buildPdf(start, end, type, currency, company);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rapport-notes-frais.pdf\"");
        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }

    /**
     * Génère un PDF pour un mois donné (YYYY-MM) et une société.
     */
    @GetMapping(value = "/report/pdf/month", produces = "application/pdf")
    public ResponseEntity<byte[]> reportPdfForMonth(
            @RequestParam("month") String month,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "company", required = false) String company
    ) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètre month attendu au format YYYY-MM", e);
        }
        String start = ym.atDay(1).toString();
        String end = ym.atEndOfMonth().toString();
        log.info("📊 /expenses/report/pdf/month month={} start={} end={} type={} currency={} company={}", month, start, end, type, currency, company);
        return reportPdf(start, end, type, currency, company);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteByDate(@RequestParam("date") String date) {
        LocalDate d = LocalDate.parse(date);
        int deleted = weaviateService.deleteExpensesByDate(d);
        return ResponseEntity.ok(java.util.Map.of("date", date, "deleted", deleted));
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<?> deleteAll(
            @RequestParam(value = "company", required = false) String company) {
        log.warn("🗑️ /expenses/delete-all appelé — company={}", company);
        int deleted = weaviateService.deleteAllExpenses(company);
        return ResponseEntity.ok(java.util.Map.of("deleted", deleted, "company", company == null ? "toutes" : company));
    }
}
