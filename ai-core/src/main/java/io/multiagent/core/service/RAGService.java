package io.multiagent.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.multiagent.core.client.LLMAIClient;
import io.multiagent.core.kafka.AuditEventPublisher;
import io.multiagent.core.model.ExpenseItem;
import io.multiagent.core.model.IntentResult;
import io.multiagent.core.model.RAGChunk;
import io.multiagent.core.model.RAGResponse;
import io.multiagent.core.model.ReRankResult;
import io.multiagent.core.model.ReasoningResult;
import io.multiagent.core.model.ExpenseReportResponse;
import io.multiagent.core.util.DateProvider;
import io.multiagent.core.util.LLMUtils;
import jakarta.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.InputStream;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates the full RAG flow (rewrite → search → rerank → reasoning LLM).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {

    private final QueryRewriteService rewriteService;
    private final SemanticSearchService semanticSearch;
    private final ReRankService rerankService;
    private final LLMAIClient llm;
    private final DateProvider dateProvider;
    private final ExpenseReportService expenseReportService;
    private final WeaviateService weaviateService;
    private final ExpenseIdGenerator idGenerator;
    private final ExpensePdfService expensePdfService;
    private final AuditEventPublisher auditPublisher;
    @Value("${ai-core.expense.km-rate-7cv:0.661}")
    private double kmRate;
    @Value("${ai-core.expense.km-annual:11000}")
    private int kmAnnual;
    @Value("${ai-core.company-name:}")
    private String defaultCompanyName;
    @Value("${AI_CORE_PROMPT_SINGLE_EXPENSE:}")
    private String singleExpensePromptEnv;
    @Value("${AI_CORE_PROMPT_EXPENSE_LIST:}")
    private String expenseListPromptEnv;
    @Value("${AI_CORE_PROMPT_INVOICE:}")
    private String invoicePromptEnv;
    private String singleExpensePromptTemplate;
    private String expenseListPromptTemplate;
    private String invoicePromptTemplate;

    @PostConstruct
    public void loadPrompts() {
        try {
            singleExpensePromptTemplate = resolvePrompt(singleExpensePromptEnv, "AI_CORE_PROMPT_SINGLE_EXPENSE");
            expenseListPromptTemplate = resolvePrompt(expenseListPromptEnv, "AI_CORE_PROMPT_EXPENSE_LIST");
            invoicePromptTemplate = resolvePrompt(invoicePromptEnv, "AI_CORE_PROMPT_INVOICE");
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger les prompts RAG", e);
        }
    }

    private String resolvePrompt(String envValue, String envName) throws Exception {
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String classpathFile = switch (envName) {
            case "AI_CORE_PROMPT_SINGLE_EXPENSE" -> "prompts/prompt_single_expense.txt";
            case "AI_CORE_PROMPT_EXPENSE_LIST"   -> "prompts/prompt_expense_list.txt";
            case "AI_CORE_PROMPT_INVOICE"        -> "prompts/prompt_invoice.txt";
            default -> throw new IllegalStateException("Prompt inconnu : " + envName);
        };
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathFile)) {
            if (in == null) throw new IllegalStateException("Fichier prompt introuvable : " + classpathFile);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    // ===========================================================
    // 1️⃣ Pipeline : créer UNE note de frais
    // ===========================================================
    public ReasoningResult extractSingleExpense(String text, IntentResult intent) {

        log.info("🔵 Pipeline: create_expense");
        long start = System.currentTimeMillis();
        // 1. Réécriture de requête
        String rewritten = rewriteService.rewrite(text);
        log.info("rewritten: {}",rewritten);
        // 2. Semantic search
        List<String> docs = semanticSearch.searchTopK(rewritten, 5);

        // 3. RERANK (renvoie List<ReRankResult>)
       // List<ReRankResult> rankedResults = rerankService.rerank(rewritten, docs);
        List<String> sortedDocs = rerankService.rerankAndExtractTopK(rewritten, docs, 3);

        // 4. Extraction single expense
       
        String systemPrompt = singleExpensePromptTemplate.formatted(dateProvider.todayUtc().toString());
        String userPrompt = buildSingleExpenseUserPrompt(text, sortedDocs);
        log.info("single-expense system prompt:\n{}", systemPrompt);
        log.info("single-expense user prompt:\n{}", userPrompt);
        String json = llm.extractJSON(systemPrompt, userPrompt);
        log.info("extractJSON: {}",json);
        List<ExpenseItem> items = ExpenseItem.fromJsonArray("[" + json + "]");
        if (items.isEmpty()) {
            log.error("❌ Impossible d'extraire une dépense structurée du JSON retourné par le LLM pour le texte : {}", text);
            return ReasoningResult.error("Le LLM n'a pas pu extraire de dépense valide à partir du texte fourni.");
        }
        ExpenseItem expense = items.get(0);

        enrichFromText(expense, text);
        log.info("expense after enrich: {}", expense);
        normalizeExpense(expense, text);
        enrichKm(expense, text);
        // Si paymentMode toujours vide, valeur par défaut = Personnel
        if (isBlank(expense.getPaymentMode())) {
            expense.setPaymentMode("Personnel");
        }
        // Société par défaut si absente
        if (isBlank(expense.getCompany()) && !isBlank(defaultCompanyName)) {
            expense.setCompany(defaultCompanyName);
        }
        // Bloque la création si aucune société n'est fournie et qu'aucun défaut n'est configuré
        if (isBlank(expense.getCompany())) {
            String msg = "Société/organisation manquante : indiquez le nom ou configurez AI_CORE_COMPANY_NAME";
            log.error("❌ {}", msg);
            return ReasoningResult.error(msg);
        }
        // Fallbacks génériques
        if (isBlank(expense.getCurrency())) {
            expense.setCurrency("EUR");
        }
        if (isBlank(expense.getAddress())) {
            expense.setAddress("Paris, France");
        }
        List<ExpenseItem> expandedExpenses = isKmMonthly(expense, text)
                ? expandKmMonthly(expense)
                : List.of(expense);

        List<ExpenseItem> validExpenses = new java.util.ArrayList<>();
        for (ExpenseItem exp : expandedExpenses) {
            // Génération id incrémental (reset mensuel)
            try {
                LocalDate d = resolveDate(exp.getDate());
                int id = idGenerator.nextId(d);
                // sécurise en cas de décalage : si l'id généré est <= max existant, on pousse max+1
                try {
                    int max = weaviateService.findMaxExpenseId(java.time.YearMonth.from(d));
                    if (id <= max) {
                        id = max + 1;
                    }
                } catch (Exception ignored) {}
                exp.setId(id);
            } catch (Exception e) {
                log.warn("⚠️ Impossible de générer l'id incrémental: {}", e.getMessage());
            }
            if (exp == null
                    || exp.getAmount() == null
                    || isBlank(exp.getCurrency())
                    || isBlank(exp.getType())
                    || isBlank(exp.getPaymentMode())
                    || isBlank(exp.getAddress())) {
                log.warn("⚠️ Expense invalide (champ obligatoire manquant) après extraction: {}", exp);
                return ReasoningResult.error("Expense invalide: amount/currency/type/paymentMode/address requis");
            }

            try {
                weaviateService.indexExpense(null, exp, "text", false, null);
                validExpenses.add(exp);
            } catch (Exception e) {
                log.warn("⚠️ Indexation expense Weaviate échouée: {}", e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;

        auditPublisher.publish(
                "create_expense",
                validExpenses.isEmpty() ? "PARTIAL" : "SUCCESS",
                llm.getLlmModel(),
                "expenses_created=" + validExpenses.size(),
                duration,
                Map.of("expenseCount", validExpenses.size(), "rewrittenQuery", rewritten)
        );

        return ReasoningResult.builder()
                .type("create_expense")
                .status("EXPENSE_CREATED")
                .confidence(intent.getConfidence())
                .raw(text)
                .expenses(validExpenses)
                .metadata(Map.of(
                        "rewrittenQuery", rewritten,
                        "candidateChunks", docs.size(),
                        "topSorted", sortedDocs.size(),
                        "processingMs", duration
                ))
                .build();
    }

    // ===========================================================
    // 2️⃣ Pipeline : générer un RAPPORT
    // ===========================================================
    public ReasoningResult extractExpenseReport(String text, IntentResult intent) {

        log.info("🟢 Pipeline: generate_expense_report");
        long start = System.currentTimeMillis();
        // 1. Réécriture
        String rewritten = rewriteService.rewrite(text);

        // 2. Extraction de période (structurée)
        var range = semanticSearch.extractDateRange(rewritten);

        String company = extractCompany(intent);
        // 3. Requête structurée Weaviate
        ExpenseReportResponse report;
        try {
            report = expenseReportService.report(
                    range.start().toString(),
                    range.end().toString(),
                    null,
                    null,
                    company
            );
        } catch (IllegalStateException e) {
            log.error("❌ company/organisation manquante pour le rapport", e);
            return ReasoningResult.error(e.getMessage());
        }
        String pdfPath = generatePdfToTemp(range.start().toString(), range.end().toString(), company);

        List<ExpenseItem> expenses = report.getExpenses();

        long duration = System.currentTimeMillis() - start;

        auditPublisher.publish(
                "generate_expense_report",
                "SUCCESS",
                llm.getLlmModel(),
                "expenses_found=" + (expenses == null ? 0 : expenses.size()),
                duration,
                Map.of("start", report.getStart() == null ? "" : report.getStart(),
                       "end",   report.getEnd()   == null ? "" : report.getEnd(),
                       "company", company)
        );

        return ReasoningResult.builder()
                .type("generate_expense_report")
                .status("EXPENSE_REPORT")
                .confidence(intent.getConfidence())
                .raw(text)
                .expenses(expenses)
                .metadata(Map.of(
                        "rewrittenQuery", rewritten,
                        "foundExpenses", expenses == null ? 0 : expenses.size(),
                        "start", report.getStart(),
                        "end", report.getEnd(),
                        "totalsByCurrency", report.getTotalsByCurrency(),
                        "totalsByType", report.getTotalsByType(),
                        "processingMs", duration,
                        "pdfPath", pdfPath
                ))
                .build();
    }

    // ===========================================================
    // 3️⃣ Pipeline : extraire les données d'une FACTURE
    // ===========================================================
    public ReasoningResult extractInvoiceData(String text, IntentResult intent) {
        log.info("🔵 Pipeline: generate_invoice");
        long start = System.currentTimeMillis();

        // Pas de RAG pour la facture, juste une extraction structurée.
        // Le prompt est conçu pour prendre le texte brut et retourner du JSON.
        String systemPrompt = invoicePromptTemplate;
        String userPrompt = text;

        log.info("invoice-extraction system prompt:\n{}", systemPrompt);

        String json = llm.extractJSON(systemPrompt, userPrompt);
        log.info("extractJSON for invoice: {}", json);

        long duration = System.currentTimeMillis() - start;

        auditPublisher.publish(
                "generate_invoice",
                "SUCCESS",
                llm.getLlmModel(),
                json,
                duration,
                Map.of()
        );

        return ReasoningResult.builder()
                .type("generate_invoice")
                .status("INVOICE_DATA_EXTRACTED")
                .confidence(intent.getConfidence())
                .raw(text)
                .metadata(Map.of(
                        "invoiceDataJson", json,
                        "processingMs", duration
                ))
                .build();
    }


    // ===========================================================
    // PROMPTS
    // ===========================================================

    private String buildSingleExpenseUserPrompt(String original, List<String> context) {
        String ctx = context == null || context.isEmpty() ? "n/a" : String.join("\n---\n", context);
        return "TEXTE:\n" + (original == null ? "" : original)
                + "\n\nCONTEXTE:\n" + ctx;
    }

    private String buildExpenseListPrompt(List<String> docs) {
        return expenseListPromptTemplate.formatted(String.join("\n---\n", docs));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fallback minimal : on n’enrichit plus amount/currency/type/date/address,
     * on se contente de renseigner l’originalText si absent.
     * L’objectif est d’éviter d’indexer des valeurs erronées quand le LLM est incomplet.
     */
    private void enrichFromText(ExpenseItem e, String raw) {
        if (e == null) return;
        if (isBlank(e.getOriginalText())) {
            e.setOriginalText(raw);
        }
    }

    /**
     * Corrige certaines sorties LLM pour les aligner sur les règles métier.
     * La location/domiciliation reste une dépense personnelle remboursable.
     */
    private void normalizeExpense(ExpenseItem e, String raw) {
        if (e == null) {
            return;
        }

        String type = e.getType();
        String description = e.getDescription();
        boolean locationExpense = containsAny(type, "location", "domiciliation", "rent", "loyer")
                || containsAny(description, "domiciliation", "location", "rent", "loyer")
                || containsAny(raw, "domiciliation", "location", "rent", "loyer", "je loue");

        if (locationExpense) {
            e.setType("location");
            e.setPaymentMode("Personnel");
            if (isBlank(e.getDescription())) {
                e.setDescription("Location / domiciliation");
            }
        }
    }

    /**
     * Enrichit les frais km si l’utilisateur mentionne un kilométrage ou si le type frais_km est déjà détecté.
     */
    private void enrichKm(ExpenseItem e, String raw) {
        if (e == null) return;
        if (!"frais_km".equalsIgnoreCase(e.getType() == null ? "" : e.getType())) {
            return; // détection laissée au prompt
        }
        // Si le LLM fournit un km, on calcule le montant à partir du barème ; sinon on ne devine rien.
        if (e.getKm() != null) {
            if (e.getAmount() == null) {
                double costPerKm = computeCostPerKm(kmAnnual, kmRate);
                e.setAmount(round2(costPerKm * e.getKm()));
            }
            if (isBlank(e.getCurrency())) {
                e.setCurrency("EUR");
            }
        }
        if (isBlank(e.getDescription())) {
            e.setDescription("Frais km automatiques" + (e.getKm() != null ? " (" + e.getKm().intValue() + " km)" : ""));
        }
        if (isBlank(e.getPaymentMode())) {
            e.setPaymentMode("Personnel");
        }
        if (isBlank(e.getAddress())) {
            e.setAddress("lieu de déplacement");
        }
    }

    private boolean isKmMonthly(ExpenseItem e, String raw) {
        if (e == null) return false;
        if (!"frais_km".equalsIgnoreCase(e.getType())) return false;
        String t = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        boolean monthWord = t.contains("mois") || t.contains("month");
        boolean explicitDay = Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(t).find()
                || Pattern.compile("\\d{1,2}\\s*[/-]\\s*\\d{1,2}\\s*[/-]\\s*\\d{2,4}").matcher(t).find();
        return monthWord && !explicitDay;
    }

    private List<ExpenseItem> expandKmMonthly(ExpenseItem base) {
        List<ExpenseItem> list = new ArrayList<>();
        LocalDate refDate = resolveDate(base.getDate());
        YearMonth ym = YearMonth.from(refDate);
        Set<MonthDay> holidays = frenchFixedHolidays();

        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate d = ym.atDay(day);
            if (isWorkingDay(d, holidays)) {
                ExpenseItem clone = copyExpense(base);
                clone.setDate(d.toString());
                list.add(clone);
            }
        }
        return list;
    }

    private ExpenseItem copyExpense(ExpenseItem src) {
        ExpenseItem e = new ExpenseItem();
        e.setId(src.getId());
        e.setAmount(src.getAmount());
        e.setCurrency(src.getCurrency());
        e.setStatus(src.getStatus());
        e.setType(src.getType());
        e.setKm(src.getKm());
        e.setDate(src.getDate());
        e.setDescription(src.getDescription());
        e.setOriginalText(src.getOriginalText());
        e.setPaymentMode(src.getPaymentMode());
        e.setAddress(src.getAddress());
        e.setCompany(src.getCompany());
        return e;
    }

    private boolean isWorkingDay(LocalDate d, Set<MonthDay> holidays) {
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(MonthDay.from(d));
    }

    private Set<MonthDay> frenchFixedHolidays() {
        Set<MonthDay> h = new HashSet<>();
        h.add(MonthDay.of(1, 1));
        h.add(MonthDay.of(5, 1));
        h.add(MonthDay.of(5, 8));
        h.add(MonthDay.of(7, 14));
        h.add(MonthDay.of(8, 15));
        h.add(MonthDay.of(11, 1));
        h.add(MonthDay.of(11, 11));
        h.add(MonthDay.of(12, 25));
        return h;
    }

    private double computeCostPerKm(int kmAnnual, double kmRate) {
        if (kmAnnual <= 0) {
            return kmRate;
        }
        double d = kmAnnual;
        double annualCost;
        if (d <= 5000) {
            annualCost = d * 0.697;
        } else if (d <= 20000) {
            annualCost = (d * 0.394) + 1515;
        } else {
            annualCost = d * 0.470;
        }
        return annualCost / d;
    }

    private double round2(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
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

    private String extractCompany(IntentResult intent) {
        if (intent != null && intent.getEntities() != null) {
            Object c = intent.getEntities().get("company");
            if (c instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        if (defaultCompanyName != null && !defaultCompanyName.isBlank()) {
            return defaultCompanyName;
        }
        throw new IllegalStateException("Le nom de société / organisation est requis pour générer un rapport.");
    }

    // Suppression de l'expansion mensuelle côté code : la gestion des mentions de mois est déléguée au prompt.

    /**
     * Génère un PDF du rapport et le sauvegarde dans /tmp, renvoie le chemin.
     */
    private String generatePdfToTemp(String start, String end, String company) {
        try {
            String safeCompany = (company == null || company.isBlank()) ? "no-company" : company.replaceAll("[^a-zA-Z0-9_-]", "-");
            String label;
            try {
                YearMonth ym = YearMonth.parse(start.substring(0, 7));
                boolean sameMonth = ym.equals(YearMonth.parse(end.substring(0, 7)));
                if (sameMonth) {
                    label = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH).toLowerCase(Locale.ROOT);
                } else {
                    label = start + "_to_" + end;
                }
            } catch (Exception ignored) {
                label = start + "_to_" + end;
            }
            String filename = "/tmp/rapport-" + label + "-" + safeCompany + ".pdf";
            byte[] pdfBytes = expensePdfService.buildPdf(start, end, null, null, company);
            Files.write(Path.of(filename), pdfBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("📄 Rapport PDF généré: {}", filename);
            return filename;
        } catch (Exception e) {
            log.warn("⚠️ Impossible de générer le PDF vers /tmp: {}", e.getMessage());
            return null;
        }
    }

}
