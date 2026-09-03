package io.multiagent.core.service;

import io.multiagent.core.model.ReceiptDuplicateInfo;
import io.multiagent.core.model.ReceiptUploadResponse;
import io.multiagent.core.model.ExpenseItem;
import io.multiagent.core.util.DateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptUploadService {

    private final ReceiptStorageService storage;
    private final OcrService ocr;
    private final OcrExtractionService extraction;
    private final DuplicateDetectorService duplicateDetector;
    private final KafkaTemplate<String, String> kafka;
    private final WeaviateService weaviateService;
    private final DateProvider dateProvider;
    private final ExpenseIdGenerator idGenerator;

    @Value("${ai-core.kafka-topics.reasoning-input:reasoning-input-topic}")
    private String reasoningTopic;

    @Async
    public CompletableFuture<ReceiptUploadResponse> handleUpload(MultipartFile file, String paymentMode) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Fichier manquant ou vide");
            }

            String id = UUID.randomUUID().toString();
            byte[] bytes = file.getBytes();
            String binaryHash = sha256(bytes);

            Path saved = storage.save(file, id);
            String ocrText = ocr.extractText(saved);
            String normalized = normalizeText(ocrText);
            String textHash = sha256(normalized.getBytes(StandardCharsets.UTF_8));

            ReceiptDuplicateInfo dup = duplicateDetector.track(id, binaryHash, textHash);
            String extracted = extraction.extractExpenseJson(ocrText);

            ReceiptUploadResponse response = ReceiptUploadResponse.builder()
                    .id(id)
                    .originalFilename(file.getOriginalFilename())
                    .binaryHash(binaryHash)
                    .textHash(textHash)
                    .binaryDuplicate(dup.isBinaryDuplicate())
                    .textDuplicate(dup.isTextDuplicate())
                    .duplicateOfBinary(dup.binaryOf())
                    .duplicateOfText(dup.textOf())
                    .ocrText(ocrText)
                    .extractedExpenseJson(extracted)
                    .build();

            // Indexer la dépense structurée si possible
            ExpenseItem expense = parseExpense(extracted);
            if (expense != null) {
                try {
                    LocalDate d = expense.getDate() != null ? LocalDate.parse(expense.getDate()) : dateProvider.todayUtc();
                    expense.setId(idGenerator.nextId(d));
                } catch (Exception e) {
                    log.warn("⚠️ Impossible de générer l'id incrémental (upload) : {}", e.getMessage());
                }
                // Fallbacks légers (on s’appuie sur le prompt pour setter payment/address, ici on met juste des valeurs par défaut si absentes)
                if (isBlank(expense.getPaymentMode())) {
                    expense.setPaymentMode("Personnel");
                }
                if (isBlank(expense.getAddress())) {
                    expense.setAddress("inconnue");
                }
                weaviateService.indexExpense(id, expense, "receipt", dup.isBinaryDuplicate() || dup.isTextDuplicate(), binaryHash);
            }

            kafka.send(reasoningTopic, ocrText);
            log.info("📤 OCR → reasoning topic={}, id={}", reasoningTopic, id);

            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            throw new IllegalStateException("Upload échoué: " + e.getMessage(), e);
        }
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Impossible de calculer SHA-256", e);
        }
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String lower = noAccents.toLowerCase(Locale.ROOT);
        String compact = lower.replaceAll("[^a-z0-9€., ]", " ");
        return compact.replaceAll("\\s+", " ").trim();
    }

    private ExpenseItem parseExpense(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            // Le LLM renvoie un objet JSON ; on l'entoure en tableau pour réutiliser fromJsonArray
            String arr = "[" + json + "]";
            return ExpenseItem.fromJsonArray(arr).stream().findFirst().orElse(null);
        } catch (Exception e) {
            log.warn("⚠️ Impossible de parser la dépense extraite: {}", e.getMessage());
            return null;
        }
    }

    private LocalDate resolveDate(String date) {
        if (date == null || date.isBlank()) {
            return dateProvider.todayUtc();
        }
        try {
            return LocalDate.parse(date);
        } catch (Exception ignored) {
        }
        try {
            return java.time.OffsetDateTime.parse(date).toLocalDate();
        } catch (Exception ignored) {
        }
        return dateProvider.todayUtc();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

}
