package io.multiagent.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.multiagent.core.model.IntentRequest;
import io.multiagent.core.model.InvoiceLookupRequest;
import io.multiagent.core.model.ReasoningResult;
import io.multiagent.core.model.SimpleInvoiceRequest;
import io.multiagent.core.service.ReasoningService;
import io.multiagent.core.service.SimpleInvoiceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/invoices")
public class SimpleInvoiceController {

    private final SimpleInvoiceService invoiceService;
    private final ReasoningService reasoningService;
    private final ObjectMapper objectMapper;

    public SimpleInvoiceController(
            SimpleInvoiceService invoiceService,
            ReasoningService reasoningService,
            ObjectMapper objectMapper
    ) {
        this.invoiceService = invoiceService;
        this.reasoningService = reasoningService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody SimpleInvoiceRequest request) {
        try {
            var generated = invoiceService.generate(request);
            return ResponseEntity.ok(Map.of(
                    "message", "Invoice files generated successfully",
                    "pdfPath", generated.pdfPath().toString(),
                    "excelPath", generated.excelPath().toString(),
                    "invoiceName", generated.invoice().invoiceName() == null ? "" : generated.invoice().invoiceName()
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to generate invoice files: " + e.getMessage());
        }
    }

    @PostMapping("/generate/from-text")
    public ResponseEntity<?> generateFromText(@RequestBody IntentRequest request) {
        try {
            SimpleInvoiceRequest invoiceRequest = extractInvoiceRequest(request.getText());
            var generated = invoiceService.generate(invoiceRequest, request.getText());
            return ResponseEntity.ok(Map.of(
                    "message", "Invoice files generated successfully from text",
                    "pdfPath", generated.pdfPath().toString(),
                    "excelPath", generated.excelPath().toString(),
                    "invoiceName", generated.invoice().invoiceName() == null ? "" : generated.invoice().invoiceName()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to generate invoice from text: " + e.getMessage());
        }
    }

    @PostMapping(value = "/generate/pdf/from-text", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generatePdfFromText(@RequestBody IntentRequest request) throws Exception {
        SimpleInvoiceRequest invoiceRequest = extractInvoiceRequest(request.getText());
        var generated = invoiceService.generate(invoiceRequest, request.getText());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + generated.pdfPath().getFileName() + "\"");
        return ResponseEntity.ok().headers(headers).body(generated.pdfBytes());
    }

    @PostMapping(
            value = "/generate/excel/from-text",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> generateExcelFromText(@RequestBody IntentRequest request) throws Exception {
        SimpleInvoiceRequest invoiceRequest = extractInvoiceRequest(request.getText());
        var generated = invoiceService.generate(invoiceRequest, request.getText());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + generated.excelPath().getFileName() + "\"");
        return ResponseEntity.ok().headers(headers).body(generated.excelBytes());
    }

    private SimpleInvoiceRequest extractInvoiceRequest(String text) throws Exception {
        ReasoningResult reasoningResult = reasoningService.process(text);
        if (!"generate_invoice".equals(reasoningResult.getType())) {
            throw new IllegalStateException("Le texte n'a pas produit une facture. Type obtenu: " + reasoningResult.getType());
        }
        Object rawJson = reasoningResult.getMetadata() == null ? null : reasoningResult.getMetadata().get("invoiceDataJson");
        if (rawJson == null || rawJson.toString().isBlank()) {
            throw new IllegalStateException("invoiceDataJson absent dans le resultat de reasoning");
        }
        return objectMapper.readValue(rawJson.toString(), SimpleInvoiceRequest.class);
    }

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generatePdf(@RequestBody InvoiceLookupRequest request) throws IOException {
        var generated = invoiceService.generateFromWeaviate(request);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + generated.pdfPath().getFileName() + "\"");
        return ResponseEntity.ok().headers(headers).body(generated.pdfBytes());
    }

    @PostMapping(
            value = "/excel",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> generateExcel(@RequestBody InvoiceLookupRequest request) throws IOException {
        var generated = invoiceService.generateFromWeaviate(request);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + generated.excelPath().getFileName() + "\"");
        return ResponseEntity.ok().headers(headers).body(generated.excelBytes());
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteInvoice(@RequestBody InvoiceLookupRequest request) {
        try {
            int deleted = invoiceService.deleteFromWeaviate(request);
            if (deleted <= 0) {
                return ResponseEntity.internalServerError().body(
                        "Aucune facture supprimée pour invoiceName=%s, sellerCompanyName=%s, billingMonth=%s"
                                .formatted(request.invoiceName(), request.sellerCompanyName(), request.billingMonth())
                );
            }
            return ResponseEntity.ok(Map.of(
                    "message", "Invoice deleted successfully",
                    "deleted", deleted,
                    "invoiceName", request.invoiceName() == null ? "" : request.invoiceName()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to delete invoice: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<?> deleteAll(
            @RequestParam(value = "sellerCompanyName", required = false) String sellerCompanyName) {
        log.warn("🗑️ /invoices/delete-all appelé — sellerCompanyName={}", sellerCompanyName);
        int deleted = invoiceService.deleteAllFromWeaviate(sellerCompanyName);
        return ResponseEntity.ok(Map.of(
                "deleted", deleted,
                "sellerCompanyName", sellerCompanyName == null ? "toutes" : sellerCompanyName
        ));
    }
}
