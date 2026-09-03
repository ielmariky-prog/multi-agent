package io.multiagent.core.controller;

import io.multiagent.core.model.IntentResult;
import io.multiagent.core.model.ReasoningResult;
import io.multiagent.core.service.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/rag")
public class RAGController {

    private final RAGService ragService;

    // ===========================================================
    // 1️⃣ CREATE EXPENSE
    // ===========================================================
    @PostMapping("/create-expense")
    public ReasoningResult createExpense(
            @RequestBody String text,
            @RequestParam(defaultValue = "0.95") double confidence
    ) {
        log.info("🧾 /rag/create-expense | text={} chars, confidence={}", text.length(), confidence);

        IntentResult intent = IntentResult.builder()
                .intent("create_expense")
                .confidence(confidence)
                .originalText(text)
                .explanation("IA TEXT").build();

        return ragService.extractSingleExpense(text, intent);
    }

    // ===========================================================
    // 2️⃣ GENERATE EXPENSE REPORT
    // ===========================================================
    @PostMapping("/generate-report")
    public ReasoningResult generateExpenseReport(
            @RequestBody String text,
            @RequestParam(defaultValue = "0.95") double confidence
    ) {
        log.info("📊 /rag/generate-report | text={} chars, confidence={}", text.length(), confidence);
        IntentResult intent = IntentResult.builder()
                .intent("generate_expense_report")
                .confidence(confidence)
                .originalText(text)
                .explanation("IA TEXT").build();

        return ragService.extractExpenseReport(text, intent);
    }

    // ===========================================================
    // 3️⃣ HEALTH CHECK
    // ===========================================================
    @GetMapping("/health")
    public String health() {
        return "RAGController OK";
    }
}