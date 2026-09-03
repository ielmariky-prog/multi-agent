package io.multiagent.core.service;

import io.multiagent.core.client.LLMAIClient;
import io.multiagent.core.util.DateProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OcrExtractionService {

    private final LLMAIClient llm;
    private final DateProvider dateProvider;

    @Value("${AI_CORE_PROMPT_OCR_SINGLE_EXPENSE:}")
    private String ocrPromptEnv;

    private String ocrPromptTemplate;

    @PostConstruct
    public void loadPrompt() {
        try {
            if (ocrPromptEnv != null && !ocrPromptEnv.isBlank()) {
                ocrPromptTemplate = ocrPromptEnv;
            } else {
                ocrPromptTemplate = loadFromClasspath("prompts/prompt_ocr_single_expense.txt");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger le prompt OCR", e);
        }
    }

    private String loadFromClasspath(String path) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Fichier prompt introuvable : " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String extractExpenseJson(String ocrText) {
        String today = dateProvider.todayUtc().toString();
        String augmented = prependDateHint(ocrText);
        String systemPrompt = ocrPromptTemplate.formatted(today);
        return llm.extractJSON(systemPrompt, augmented);
    }

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{2}[/-]\\d{2}[/-]\\d{4})(?:\\s+\\d{2}:\\d{2}:\\d{2})?|"
                    + "(\\d{4}-\\d{2}-\\d{2})(?:\\s+\\d{2}:\\d{2}:\\d{2})?",
            Pattern.CASE_INSENSITIVE);

    private String prependDateHint(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher m = DATE_PATTERN.matcher(text);
        if (m.find()) {
            String dateFound = m.group().trim();
            return "Date détectée (ne pas remplacer) : " + dateFound + "\n" + text;
        }
        return text;
    }
}
