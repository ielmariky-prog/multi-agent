package io.multiagent.core.service;

import io.multiagent.core.client.LLMAIClient;
import io.multiagent.core.model.ExpenseItem;
import io.multiagent.core.model.InvoiceLookupRequest;
import io.multiagent.core.model.SellerProfile;
import io.multiagent.core.model.SimpleInvoiceRequest;
import io.multiagent.core.weaviate.WeaviateResponseParser;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.filters.Operator;
import io.weaviate.client.v1.filters.WhereFilter;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.argument.WhereArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import io.weaviate.client.v1.schema.model.Property;
import io.weaviate.client.v1.schema.model.Schema;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * WeaviateService (2025)
 *
 * - Encapsule toutes les interactions avec Weaviate
 * - Crée le schéma si nécessaire
 * - Indexe des chunks (texte + embedding)
 * - Effectue des recherches vectorielles
 */
@Slf4j
@Service
public class WeaviateService {

    private final WeaviateClient client;
    private final LLMAIClient llm;
    private final String className;
    private final String expenseClassName;
    private final String invoiceClassName;
    private final String sellerProfileClassName;
    private final int schemaInitMaxAttempts;
    private final Duration schemaInitBackoff;

    public WeaviateService(
            @Value("${weaviate.scheme:http}") String scheme,
            @Value("${weaviate.host:localhost:8080}") String host,
            @Value("${weaviate.class-name:DocumentChunk}") String className,
            @Value("${weaviate.expense-class:Expense}") String expenseClassName,
            @Value("${weaviate.invoice-class:Invoice}") String invoiceClassName,
            @Value("${weaviate.seller-profile-class:SellerProfile}") String sellerProfileClassName,
            @Value("${weaviate.schema-init.max-attempts:6}") int schemaInitMaxAttempts,
            @Value("${weaviate.schema-init.backoff:10s}") Duration schemaInitBackoff,
            LLMAIClient llm) {
        this.llm = llm;
        this.className = className;
        this.expenseClassName = expenseClassName;
        this.invoiceClassName = invoiceClassName;
        this.sellerProfileClassName = sellerProfileClassName;
        this.schemaInitMaxAttempts = Math.max(1, schemaInitMaxAttempts);
        this.schemaInitBackoff = schemaInitBackoff == null ? Duration.ofSeconds(10) : schemaInitBackoff;
        this.client = new WeaviateClient(new Config(scheme, sanitizeHost(host)));
    }

    @PostConstruct
    public void initSchema() {
        for (int attempt = 1; attempt <= schemaInitMaxAttempts; attempt++) {
            try {
                synchronizeSchema();
                return;
            } catch (Exception e) {
                if (attempt == schemaInitMaxAttempts) {
                    log.error("❌ Erreur initSchema Weaviate après {} tentatives: {}", attempt, e.getMessage(), e);
                } else {
                    log.warn("⏳ Weaviate indisponible (tentative {}/{}): {}. Nouvelle tentative dans {}...",
                            attempt, schemaInitMaxAttempts, e.getMessage(), schemaInitBackoff);
                    waitBeforeRetry();
                }
            }
        }
    }

    /**
     * Indexe un chunk dans Weaviate en calculant automatiquement l'embedding.
     */
    public void indexChunk(String text, String source) {
        indexChunk(null, text, source);
    }

    public void indexChunk(String id, String text, String source) {
        float[] embedding = llm.embed(text);
        Float[] vector = toFloatArray(embedding);
        persistChunk(id, text, source, vector);
    }

    /**
     * Indexe un chunk avec un embedding déjà calculé.
     */
    public void indexChunk(String id, String text, List<Double> vector) {
        persistChunk(id, text, null, toFloatArray(vector));
    }

    public void indexExpense(String id, ExpenseItem item, String source, boolean duplicate, String hash) {
        try {
            if (item == null
                    || item.getAmount() == null
                    || isNullOrBlank(item.getCurrency())
                    || isNullOrBlank(item.getType())
                    || isNullOrBlank(item.getPaymentMode())
                    || isNullOrBlank(item.getAddress())) {
                log.warn("⚠️ indexExpense ignoré: champs obligatoires manquants (amount/currency/type/paymentMode/address) pour {}", item);
                return;
            }
            String text = buildExpenseText(item, source, duplicate);
            List<Double> vector = llm.embed(llm.getEmbeddingModel(), text);
            log.debug("expenseITEM: {}", item);
            Map<String, Object> props = new HashMap<>();
            props.put("amount", item.getAmount());
            props.put("currency", item.getCurrency());
            props.put("type", item.getType());
            props.put("expenseId", item.getId());
            if (item.getKm() != null) {
                props.put("km", item.getKm());
            }
            String isoDate = null;
            if (item.getDate() != null && !item.getDate().isBlank()) {
                try {
                    LocalDate ld = LocalDate.parse(item.getDate());
                    isoDate = formatRfc3339(ld);
                } catch (Exception ignored) { }
            }
            props.put("date", isoDate);
            props.put("dateText", isoDate);
            props.put("description", item.getDescription());
            props.put("originalText", item.getOriginalText());
            props.put("source", source);
            props.put("duplicateFlag", duplicate);
            props.put("hash", hash);
            props.put("text", text);
            props.put("paymentMode", item.getPaymentMode());
            props.put("address", item.getAddress());
            if (item.getCompany() != null) {
                props.put("company", item.getCompany());
            }

            var creator = client.data().creator()
                    .withClassName(expenseClassName)
                    .withProperties(props)
                    .withVector(toFloatArray(vector));

            if (id != null && !id.isBlank()) {
                creator = creator.withID(id);
            }

            var result = creator.run();
            if (result.hasErrors()) {
                log.error("❌ Weaviate indexExpense error: {}", result.getError());
            } else {
                log.info("📥 Weaviate: expense indexée (id={})", id);
            }

        } catch (Exception e) {
            log.error("❌ Exception indexExpense: {}", e.getMessage(), e);
        }
    }

    public void indexSimpleInvoice(String id, SimpleInvoiceRequest invoice, String sourceText, String pdfPath, String excelPath) {
        try {
            if (invoice == null
                    || isNullOrBlank(invoice.sellerCompanyName())
                    || isNullOrBlank(invoice.clientCompanyName())
                    || invoice.invoiceDate() == null) {
                log.warn("⚠️ indexSimpleInvoice ignoré: champs obligatoires manquants (sellerCompanyName/clientCompanyName/invoiceDate) pour {}", invoice);
                return;
            }

            String text = buildInvoiceText(invoice, sourceText, pdfPath, excelPath);
            List<Double> vector = llm.embed(llm.getEmbeddingModel(), text);
            Map<String, Object> props = new HashMap<>();
            props.put("invoiceName", invoice.invoiceName());
            props.put("invoiceDate", formatRfc3339(invoice.invoiceDate()));
            props.put("billingMonth", invoice.billingMonth());
            props.put("sellerCompanyName", invoice.sellerCompanyName());
            props.put("sellerAddress", invoice.sellerAddress());
            props.put("sellerRcs", invoice.sellerRcs());
            props.put("clientCompanyName", invoice.clientCompanyName());
            props.put("clientAddress", invoice.clientAddress());
            props.put("clientRcs", invoice.clientRcs());
            props.put("invoiceTitle", invoice.invoiceTitle());
            props.put("daysCount", invoice.daysCount());
            props.put("unitPriceHt", invoice.unitPriceHt());
            props.put("totalHt", invoice.totalHt());
            props.put("vatRate", invoice.vatRate());
            props.put("totalTtc", invoice.totalTtc());
            props.put("currency", invoice.currency());
            props.put("paymentDueDate", invoice.paymentDueDate() == null ? null : formatRfc3339(invoice.paymentDueDate()));
            props.put("latePaymentClause", invoice.latePaymentClause());
            props.put("notes", invoice.notes());
            props.put("sourceText", sourceText);
            props.put("pdfPath", pdfPath);
            props.put("excelPath", excelPath);
            props.put("text", text);

            var creator = client.data().creator()
                    .withClassName(invoiceClassName)
                    .withProperties(props)
                    .withVector(toFloatArray(vector));

            if (id != null && !id.isBlank()) {
                creator = creator.withID(id);
            }

            var result = creator.run();
            if (result.hasErrors()) {
                log.error("❌ Weaviate indexSimpleInvoice error: {}", result.getError());
            } else {
                log.info("📥 Weaviate: facture indexée (invoiceName={})", invoice.invoiceName());
            }
        } catch (Exception e) {
            log.error("❌ Exception indexSimpleInvoice: {}", e.getMessage(), e);
        }
    }

    public List<SimpleInvoiceRequest> findInvoices(InvoiceLookupRequest request) {
        if (request == null
                || isNullOrBlank(request.billingMonth())
                || isNullOrBlank(request.sellerCompanyName())) {
            return List.of();
        }
        try {
            var response = client.data().objectsGetter()
                    .withClassName(invoiceClassName)
                    .withLimit(1000)
                    .run();

            if (response.hasErrors() || response.getResult() == null) {
                log.error("❌ findInvoices fetch error: {}", response.getError());
                return List.of();
            }

            return response.getResult().stream()
                    .filter(object -> object != null && object.getProperties() != null)
                    .map(object -> toSimpleInvoiceRequest(object.getProperties()))
                    .filter(invoice -> invoice != null
                            && request.billingMonth().equalsIgnoreCase(safeString(invoice.billingMonth()))
                            && request.sellerCompanyName().trim().equalsIgnoreCase(safeString(invoice.sellerCompanyName()).trim())
                            && matchesInvoiceName(request.invoiceName(), invoice.invoiceName()))
                    .sorted(Comparator.comparing(
                            invoice -> safeString(invoice.invoiceName()),
                            String.CASE_INSENSITIVE_ORDER
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("❌ findInvoices exception: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public SellerProfile findSellerProfile(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return null;
        }
        try {
            var response = client.data().objectsGetter()
                    .withClassName(sellerProfileClassName)
                    .withLimit(100)
                    .run();
            if (response.hasErrors() || response.getResult() == null) {
                log.error("❌ findSellerProfile fetch error: {}", response.getError());
                return null;
            }
            return response.getResult().stream()
                    .filter(object -> object != null && object.getProperties() != null)
                    .map(object -> toSellerProfile(object.getProperties()))
                    .filter(profile -> profile != null && companyName.trim().equalsIgnoreCase(safeString(profile.companyName()).trim()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("❌ findSellerProfile exception: {}", e.getMessage(), e);
            return null;
        }
    }

    public int deleteInvoices(InvoiceLookupRequest request) {
        if (request == null
                || isNullOrBlank(request.invoiceName())
                || isNullOrBlank(request.sellerCompanyName())) {
            return 0;
        }
        try {
            var response = client.data().objectsGetter()
                    .withClassName(invoiceClassName)
                    .withLimit(1000)
                    .run();

            if (response.hasErrors() || response.getResult() == null) {
                log.error("❌ deleteInvoices fetch error: {}", response.getError());
                return -1;
            }

            int deleted = 0;
            for (var object : response.getResult()) {
                if (object == null || object.getProperties() == null) {
                    continue;
                }
                Map<String, Object> props = object.getProperties();
                if (!matchesInvoiceDeletion(props, request)) {
                    continue;
                }

                var deleteResult = client.data().deleter()
                        .withClassName(invoiceClassName)
                        .withID(object.getId())
                        .run();
                if (deleteResult.hasErrors() || !Boolean.TRUE.equals(deleteResult.getResult())) {
                    log.error("❌ Weaviate invoice delete error for id={}: {}", object.getId(), deleteResult.getError());
                } else {
                    deleted++;
                }
            }
            log.info("🗑️ Weaviate invoice delete -> invoiceName={}, sellerCompanyName={}, billingMonth={}, deleted={}",
                    request.invoiceName(), request.sellerCompanyName(), request.billingMonth(), deleted);
            return deleted;
        } catch (Exception e) {
            log.error("❌ deleteInvoices exception: {}", e.getMessage(), e);
            return -1;
        }
    }

    private String buildExpenseText(ExpenseItem item, String source, boolean duplicate) {
        return "date=%s | amount=%s %s | type=%s | paymentMode=%s | address=%s | desc=%s | source=%s | duplicate=%s | original=%s"
                .formatted(
                        item.getDate(),
                        item.getAmount(),
                        item.getCurrency(),
                        item.getType(),
                        item.getPaymentMode(),
                        item.getAddress(),
                        item.getDescription(),
                        source,
                        duplicate,
                        item.getOriginalText()
                );
    }

    private String buildInvoiceText(SimpleInvoiceRequest invoice, String sourceText, String pdfPath, String excelPath) {
        return "invoiceName=%s | invoiceDate=%s | billingMonth=%s | seller=%s | sellerRcs=%s | client=%s | clientRcs=%s | title=%s | days=%s | unitPriceHt=%s | totalHt=%s | totalTtc=%s | currency=%s | dueDate=%s | pdfPath=%s | excelPath=%s | source=%s"
                .formatted(
                        invoice.invoiceName(),
                        invoice.invoiceDate(),
                        invoice.billingMonth(),
                        invoice.sellerCompanyName(),
                        invoice.sellerRcs(),
                        invoice.clientCompanyName(),
                        invoice.clientRcs(),
                        invoice.invoiceTitle(),
                        invoice.daysCount(),
                        invoice.unitPriceHt(),
                        invoice.totalHt(),
                        invoice.totalTtc(),
                        invoice.currency(),
                        invoice.paymentDueDate(),
                        pdfPath,
                        excelPath,
                        sourceText
                );
    }

    private SimpleInvoiceRequest toSimpleInvoiceRequest(Map<String, Object> props) {
        if (props == null) {
            return null;
        }
        return new SimpleInvoiceRequest(
                safeString(props.get("invoiceName")),
                parseLocalDateValue(props.get("invoiceDate")),
                safeString(props.get("billingMonth")),
                safeString(props.get("sellerCompanyName")),
                safeString(props.get("sellerAddress")),
                safeString(props.get("sellerRcs")),
                safeString(props.get("clientCompanyName")),
                safeString(props.get("clientAddress")),
                safeString(props.get("clientRcs")),
                safeString(props.get("invoiceTitle")),
                parseInteger(props.get("daysCount")),
                parseBigDecimal(props.get("unitPriceHt")),
                parseBigDecimal(props.get("totalHt")),
                parseBigDecimal(props.get("vatRate")),
                parseBigDecimal(props.get("totalTtc")),
                safeString(props.get("currency")),
                parseLocalDateValue(props.get("paymentDueDate")),
                safeString(props.get("latePaymentClause")),
                safeString(props.get("notes"))
        );
    }

    private SellerProfile toSellerProfile(Map<String, Object> props) {
        if (props == null) {
            return null;
        }
        return new SellerProfile(
                safeString(props.get("companyName")),
                safeString(props.get("address")),
                safeString(props.get("rcs")),
                safeString(props.get("iban")),
                safeString(props.get("bic")),
                safeString(props.get("email")),
                safeString(props.get("capital"))
        );
    }

    private boolean matchesInvoiceName(String requestedInvoiceName, String storedInvoiceName) {
        if (requestedInvoiceName == null || requestedInvoiceName.isBlank()) {
            return true;
        }
        if (storedInvoiceName == null || storedInvoiceName.isBlank()) {
            return false;
        }
        return requestedInvoiceName.trim().equalsIgnoreCase(storedInvoiceName.trim());
    }

    private boolean matchesInvoiceDeletion(Map<String, Object> props, InvoiceLookupRequest request) {
        String storedInvoiceName = safeString(props.get("invoiceName"));
        String storedSeller = safeString(props.get("sellerCompanyName"));
        String storedBillingMonth = safeString(props.get("billingMonth"));

        if (!request.invoiceName().trim().equalsIgnoreCase(storedInvoiceName.trim())) {
            return false;
        }
        if (!request.sellerCompanyName().trim().equalsIgnoreCase(storedSeller.trim())) {
            return false;
        }
        if (request.billingMonth() == null || request.billingMonth().isBlank()) {
            return true;
        }
        return request.billingMonth().trim().equalsIgnoreCase(storedBillingMonth.trim());
    }

    private String safeString(Object value) {
        return value == null ? "" : value.toString();
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private java.math.BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number number) {
                return java.math.BigDecimal.valueOf(number.doubleValue());
            }
            return new java.math.BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseLocalDateValue(Object value) {
        if (value == null) {
            return null;
        }
        String raw = value.toString().trim();
        if (raw.isBlank()) {
            return null;
        }
        try {
            if (raw.length() >= 10 && Character.isDigit(raw.charAt(0))) {
                return LocalDate.parse(raw.substring(0, 10));
            }
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }

    private Date toDate(String iso) {
        try {
            return Date.from(java.time.Instant.parse(iso));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Supprime TOUTES les dépenses, filtre optionnel sur la société.
     * Réservé au manager — aucun filtre de date.
     */
    public int deleteAllExpenses(String company) {
        return deleteMatchingExpenses(
                props -> matchesCompany(props.get("company"), company),
                "deleteAll company=" + company
        );
    }

    /**
     * Supprime TOUTES les factures, filtre optionnel sur la société émettrice.
     * Réservé au manager — aucun filtre de mois ni de nom.
     */
    public int deleteAllInvoices(String sellerCompanyName) {
        try {
            var response = client.data().objectsGetter()
                    .withClassName(invoiceClassName)
                    .withLimit(1000)
                    .run();

            if (response.hasErrors() || response.getResult() == null) {
                log.error("❌ deleteAllInvoices fetch error: {}", response.getError());
                return -1;
            }

            int deleted = 0;
            for (var object : response.getResult()) {
                if (object == null || object.getProperties() == null) continue;
                if (sellerCompanyName != null && !sellerCompanyName.isBlank()) {
                    String stored = safeString(object.getProperties().get("sellerCompanyName"));
                    if (!sellerCompanyName.trim().equalsIgnoreCase(stored.trim())) continue;
                }
                var del = client.data().deleter()
                        .withClassName(invoiceClassName)
                        .withID(object.getId())
                        .run();
                if (!del.hasErrors() && Boolean.TRUE.equals(del.getResult())) deleted++;
            }
            log.info("🗑️ deleteAllInvoices → sellerCompanyName={}, deleted={}", sellerCompanyName, deleted);
            return deleted;
        } catch (Exception e) {
            log.error("❌ deleteAllInvoices exception: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Supprime toutes les dépenses à la date exacte (YYYY-MM-DD).
     * @return nombre supprimé (si fourni), sinon -1 en cas d'erreur.
     */
    public int deleteExpensesByDate(LocalDate date) {
        return deleteExpensesByDate(date, null);
    }

    public int deleteExpensesByDate(LocalDate date, String company) {
        if (date == null) return 0;
        try {
            int deleted = deleteExpensesByDateSafely(date, company);
            if (deleted == 0 && company != null) {
                log.warn("⚠️ deleteExpensesByDate aucun résultat avec company='{}', tentative sans filtre company (legacy)", company);
                deleted = deleteExpensesByDateSafely(date, null);
            }
            return deleted;
        } catch (Exception e) {
            log.error("❌ Exception deleteExpensesByDate: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Supprime une dépense par expenseId et mois (YearMonth).
     */
    public int deleteExpenseByIdAndMonth(int expenseId, YearMonth ym) {
        return deleteExpenseByIdAndMonth(expenseId, ym, null);
    }

    public int deleteExpenseByIdAndMonth(int expenseId, YearMonth ym, String company) {
        try {
            int deleted = deleteExpenseByIdAndMonthSafely(expenseId, ym, company);
            if (deleted == 0 && company != null) {
                log.warn("⚠️ deleteExpenseByIdAndMonth aucun résultat avec company='{}', tentative sans filtre company (legacy)", company);
                deleted = deleteExpenseByIdAndMonthSafely(expenseId, ym, null);
            }
            return deleted;
        } catch (Exception e) {
            log.error("❌ deleteExpenseByIdAndMonth exception: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Supprime toutes les dépenses correspondant à un expenseId (toutes dates).
     */
    public int deleteExpenseById(int expenseId) {
        return deleteExpenseById(expenseId, null);
    }

    public int deleteExpenseById(int expenseId, String company) {
        try {
            int deleted = deleteExpenseByIdSafely(expenseId, company);
            if (deleted == 0 && company != null) {
                log.warn("⚠️ deleteExpenseById aucun résultat avec company='{}', tentative sans filtre company (legacy)", company);
                deleted = deleteExpenseByIdSafely(expenseId, null);
            }
            return deleted;
        } catch (Exception e) {
            log.error("❌ deleteExpenseById exception: {}", e.getMessage(), e);
            return -1;
        }
    }

    private int deleteWithDateRange(String startIso, String endIso, String company) {
        var ops = new ArrayList<WhereFilter>();
        ops.add(WhereFilter.builder()
                .path("date")
                .operator(Operator.GreaterThanEqual)
                .valueDate(toDate(startIso))
                .build());
        ops.add(WhereFilter.builder()
                .path("date")
                .operator(Operator.LessThan)
                .valueDate(toDate(endIso))
                .build());
        var where = andFilters(ops, company);
        return runDelete(where);
    }

    private int deleteWithDateRangeText(String startIso, String endIso, String company) {
        LocalDate start = LocalDate.parse(startIso.substring(0, 10));
        LocalDate endExclusive = LocalDate.parse(endIso.substring(0, 10));
        var where = andFilters(List.of(dateTextRangeFilter(start, endExclusive)), company);
        return runDelete(where);
    }

    private int deleteOnDateText(String isoDay, String company) {
        LocalDate day = LocalDate.parse(isoDay);
        var where = andFilters(List.of(dateTextExactDayFilter(day)), company);
        return runDelete(where);
    }

    private WhereFilter dateTextRangeFilter(LocalDate start, LocalDate endExclusive) {
        if (start.plusMonths(1).equals(endExclusive) && start.getDayOfMonth() == 1 && endExclusive.getDayOfMonth() == 1) {
            return monthTextFilter(YearMonth.from(start));
        }
        if (start.plusDays(1).equals(endExclusive)) {
            return dateTextExactDayFilter(start);
        }
        return WhereFilter.builder()
                .path("dateText")
                .operator(Operator.Like)
                .valueText(start.toString() + "*")
                .build();
    }

    private WhereFilter dateTextExactDayFilter(LocalDate day) {
        String isoPrefix = day.toString();
        String englishLong = day.getMonth().getDisplayName(TextStyle.FULL, java.util.Locale.ENGLISH)
                + " " + day.getDayOfMonth() + ", " + day.getYear();
        return WhereFilter.builder()
                .operator(Operator.Or)
                .operands(new WhereFilter[]{
                        WhereFilter.builder()
                                .path("dateText")
                                .operator(Operator.Like)
                                .valueText(isoPrefix + "*")
                                .build(),
                        WhereFilter.builder()
                                .path("dateText")
                                .operator(Operator.Like)
                                .valueText("*" + englishLong + "*")
                                .build()
                })
                .build();
    }

    private WhereFilter monthTextFilter(YearMonth ym) {
        String isoPrefix = ym.toString();
        String englishMonth = ym.getMonth().getDisplayName(TextStyle.FULL, java.util.Locale.ENGLISH);
        String englishPattern = "*" + englishMonth + "*" + ym.getYear() + "*";
        return WhereFilter.builder()
                .operator(Operator.Or)
                .operands(new WhereFilter[]{
                        WhereFilter.builder()
                                .path("dateText")
                                .operator(Operator.Like)
                                .valueText(isoPrefix + "*")
                                .build(),
                        WhereFilter.builder()
                                .path("dateText")
                                .operator(Operator.Like)
                                .valueText(englishPattern)
                                .build()
                })
                .build();
    }

    private int deleteExpenseByIdAndMonthSafely(int expenseId, YearMonth ym, String company) {
        try {
            int deleted = deleteMatchingExpenses(props ->
                    matchesExpenseId(props.get("expenseId"), expenseId)
                            && matchesCompany(props.get("company"), company)
                            && matchesMonth(props, ym)
            , "expenseId=%s month=%s company=%s".formatted(expenseId, ym, company));
            log.info("🗑️ Weaviate safe delete by expenseId/month -> expenseId={}, month={}, company={}, deleted={}",
                    expenseId, ym, company, deleted);
            return deleted;
        } catch (Exception e) {
            log.error("❌ deleteExpenseByIdAndMonthSafely exception: {}", e.getMessage(), e);
            return -1;
        }
    }

    private int deleteExpenseByIdSafely(int expenseId, String company) {
        return deleteMatchingExpenses(props ->
                matchesExpenseId(props.get("expenseId"), expenseId)
                        && matchesCompany(props.get("company"), company)
        , "expenseId=%s company=%s".formatted(expenseId, company));
    }

    private int deleteExpensesByDateSafely(LocalDate date, String company) {
        return deleteMatchingExpenses(props ->
                matchesCompany(props.get("company"), company)
                        && matchesDay(props, date)
        , "date=%s company=%s".formatted(date, company));
    }

    private int deleteMatchingExpenses(java.util.function.Predicate<Map<String, Object>> predicate, String debugContext) {
        try {
            var response = client.data().objectsGetter()
                    .withClassName(expenseClassName)
                    .withLimit(1000)
                    .run();

            if (response.hasErrors() || response.getResult() == null) {
                log.error("❌ deleteMatchingExpenses fetch error: {}", response.getError());
                return -1;
            }

            int deleted = 0;
            for (var object : response.getResult()) {
                if (object == null || object.getProperties() == null) {
                    continue;
                }
                Map<String, Object> props = object.getProperties();
                log.debug("🔎 deleteMatchingExpenses candidate [{}] -> weaviateId={}, expenseId={}, company={}, date={}, dateText={}",
                        debugContext,
                        object.getId(),
                        props.get("expenseId"),
                        props.get("company"),
                        props.get("date"),
                        props.get("dateText"));
                if (!predicate.test(props)) {
                    continue;
                }

                var deleteResult = client.data().deleter()
                        .withClassName(expenseClassName)
                        .withID(object.getId())
                        .run();
                if (deleteResult.hasErrors() || !Boolean.TRUE.equals(deleteResult.getResult())) {
                    log.error("❌ Weaviate single delete error for id={}: {}", object.getId(), deleteResult.getError());
                } else {
                    deleted++;
                }
            }
            log.info("🔎 deleteMatchingExpenses summary [{}] -> deleted={}", debugContext, deleted);
            return deleted;
        } catch (Exception e) {
            log.error("❌ deleteMatchingExpenses exception: {}", e.getMessage(), e);
            return -1;
        }
    }

    private boolean matchesExpenseId(Object rawExpenseId, int expected) {
        if (rawExpenseId == null) {
            return false;
        }
        try {
            if (rawExpenseId instanceof Number n) {
                return n.intValue() == expected;
            }
            return Integer.parseInt(rawExpenseId.toString()) == expected;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchesCompany(Object rawCompany, String expectedCompany) {
        if (expectedCompany == null || expectedCompany.isBlank()) {
            return true;
        }
        if (rawCompany == null) {
            return false;
        }
        return rawCompany.toString().trim().equalsIgnoreCase(expectedCompany.trim());
    }

    private boolean matchesMonth(Map<String, Object> props, YearMonth ym) {
        YearMonth parsed = extractYearMonth(props.get("date"));
        if (parsed == null) {
            parsed = extractYearMonth(props.get("dateText"));
        }
        return ym.equals(parsed);
    }

    private boolean matchesDay(Map<String, Object> props, LocalDate day) {
        LocalDate parsed = extractLocalDate(props.get("date"));
        if (parsed == null) {
            parsed = extractLocalDate(props.get("dateText"));
        }
        return day.equals(parsed);
    }

    private LocalDate extractLocalDate(Object rawDate) {
        if (rawDate == null) {
            return null;
        }
        String value = rawDate.toString().trim();
        if (value.isBlank()) {
            return null;
        }
        try {
            if (value.length() >= 10 && Character.isDigit(value.charAt(0))) {
                return LocalDate.parse(value.substring(0, 10));
            }
        } catch (DateTimeParseException ignored) {
        }
        try {
            DateTimeFormatter english = DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm:ss a", Locale.ENGLISH);
            String normalized = value.replace('\u202f', ' ');
            return java.time.LocalDateTime.parse(normalized, english).toLocalDate();
        } catch (Exception ignored) {
        }
        return null;
    }

    private YearMonth extractYearMonth(Object rawDate) {
        LocalDate parsed = extractLocalDate(rawDate);
        return parsed == null ? null : YearMonth.from(parsed);
    }

    private int runDelete(WhereFilter where) {
        try {
            var delete = client.batch().objectsBatchDeleter()
                    .withClassName(expenseClassName)
                    .withWhere(where)
                    .withOutput("minimal")
                    .run();
            if (delete.hasErrors()) {
                log.error("❌ Weaviate delete error: {}", delete.getError());
                return -1;
            }
            if (delete.getResult() != null && delete.getResult().getResults() != null) {
                var results = delete.getResult().getResults();
                Long matches = results.getMatches();
                Long successful = results.getSuccessful();
                log.info("🗑️ Weaviate delete → matches={}, successful={}", matches, successful);
                return successful != null ? successful.intValue() : (matches != null ? matches.intValue() : 0);
            }
            return 0;
        } catch (Exception e) {
            log.error("❌ Exception runDelete: {}", e.getMessage(), e);
            return -1;
        }
    }

    private WhereFilter andFilters(List<WhereFilter> filters, String company) {
        var ops = new ArrayList<>(filters);
        if (company != null && !company.isBlank()) {
            ops.add(WhereFilter.builder()
                    .path("company")
                    .operator(Operator.Equal)
                    .valueText(company)
                    .build());
        }
        if (ops.size() == 1) {
            return ops.get(0);
        }
        return WhereFilter.builder()
                .operator(Operator.And)
                .operands(ops.toArray(new WhereFilter[0]))
                .build();
    }

    private void persistChunk(String id, String text, String source, Float[] vector) {
        try {
            Map<String, Object> props = new HashMap<>();
            props.put("text", text);
            if (source != null) {
                props.put("source", source);
            }
            props.put("timestamp", new Date().toString());

            var creator = client.data().creator()
                    .withClassName(className)
                    .withProperties(props)
                    .withVector(vector);

            if (id != null && !id.isBlank()) {
                creator = creator.withID(id);
            }

            var result = creator.run();
            if (result.hasErrors()) {
                log.error("❌ Weaviate indexChunk error: {}", result.getError());
            } else {
                log.info("📥 Weaviate: chunk indexé (id={})", id);
            }

        } catch (Exception e) {
            log.error("❌ Exception indexChunk: {}", e.getMessage(), e);
        }
    }

    private String sanitizeHost(String rawHost) {
        if (rawHost == null) {
            return null;
        }
        String sanitized = rawHost.trim();
        if (sanitized.isEmpty()) {
            return sanitized;
        }
        if (sanitized.startsWith("http://")) {
            sanitized = sanitized.substring("http://".length());
        } else if (sanitized.startsWith("https://")) {
            sanitized = sanitized.substring("https://".length());
        }
        if (sanitized.endsWith("/")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized;
    }

    private void synchronizeSchema() {
        Result<Schema> schemaResult = client.schema().getter().run();
        if (schemaResult.hasErrors()) {
            throw new IllegalStateException("Weaviate schema getter error: " + schemaResult.getError());
        }

        Schema schema = schemaResult.getResult();
        boolean docExists = schema != null
                && schema.getClasses() != null
                && schema.getClasses().stream().anyMatch(c -> className.equals(c.getClassName()));

        if (!docExists) {
            log.info("🧱 Weaviate: création de la classe '{}'", className);

            WeaviateClass clazz = WeaviateClass.builder()
                    .className(className)
                    .description("Chunk de texte indexé pour RAG")
                    .vectorizer("none") // vecteurs fournis par LLMAIClient
                    .properties(List.of(
                            Property.builder()
                                    .name("text")
                                    .dataType(List.of("text"))
                                    .description("Contenu du chunk")
                                    .build(),
                            Property.builder()
                                    .name("source")
                                    .dataType(List.of("string"))
                                    .description("Origine du chunk")
                                    .build(),
                            Property.builder()
                                    .name("timestamp")
                                    .dataType(List.of("string"))
                                    .description("Horodatage d'indexation")
                                    .build()
                    ))
                    .build();

            Result<Boolean> creationResult = client.schema().classCreator()
                    .withClass(clazz)
                    .run();
            if (creationResult.hasErrors()) {
                throw new IllegalStateException("Weaviate class creation error: " + creationResult.getError());
            }
        } else {
            log.info("✅ Weaviate: classe '{}' déjà présente", className);
        }

        boolean expenseExists = schema != null
                && schema.getClasses() != null
                && schema.getClasses().stream().anyMatch(c -> expenseClassName.equals(c.getClassName()));

        if (!expenseExists) {
            log.info("🧱 Weaviate: création de la classe '{}'", expenseClassName);

            WeaviateClass expense = WeaviateClass.builder()
                    .className(expenseClassName)
                    .description("Dépenses structurées (notes de frais)")
                    .vectorizer("none")
                    .properties(List.of(
                            Property.builder().name("amount").dataType(List.of("number")).description("Montant").build(),
                            Property.builder().name("currency").dataType(List.of("string")).description("Devise").build(),
                            Property.builder().name("type").dataType(List.of("string")).description("Type dépense").build(),
                            Property.builder().name("km").dataType(List.of("number")).description("Kilométrage (frais km)").build(),
                            Property.builder().name("expenseId").dataType(List.of("int")).description("Id incrémental mensuel").build(),
                            Property.builder().name("date").dataType(List.of("date")).description("Date ISO").build(),
                            Property.builder().name("dateText").dataType(List.of("string")).description("Date ISO (texte)").build(),
                            Property.builder().name("description").dataType(List.of("text")).description("Description").build(),
                            Property.builder().name("originalText").dataType(List.of("text")).description("Texte source").build(),
                            Property.builder().name("source").dataType(List.of("string")).description("receipt|text").build(),
                            Property.builder().name("paymentMode").dataType(List.of("string")).description("Personnel|Business").build(),
                            Property.builder().name("address").dataType(List.of("text")).description("Adresse/lieu").build(),
                            Property.builder().name("company").dataType(List.of("string")).description("Société/organisation").build(),
                            Property.builder().name("duplicateFlag").dataType(List.of("boolean")).description("Doublon").build(),
                            Property.builder().name("hash").dataType(List.of("string")).description("Hash binaire/texte").build(),
                            Property.builder().name("text").dataType(List.of("text")).description("Texte concaténé pour embedding").build()
                    ))
                    .build();

            Result<Boolean> creationExpense = client.schema().classCreator()
                    .withClass(expense)
                    .run();
            if (creationExpense.hasErrors()) {
                throw new IllegalStateException("Weaviate expense class creation error: " + creationExpense.getError());
            }
        } else {
            log.info("✅ Weaviate: classe '{}' déjà présente", expenseClassName);
            ensureExpenseProperties(schemaResult.getResult(), expenseClassName);
        }

        boolean invoiceExists = schema != null
                && schema.getClasses() != null
                && schema.getClasses().stream().anyMatch(c -> invoiceClassName.equals(c.getClassName()));

        if (!invoiceExists) {
            log.info("🧱 Weaviate: création de la classe '{}'", invoiceClassName);

            WeaviateClass invoice = WeaviateClass.builder()
                    .className(invoiceClassName)
                    .description("Factures simples générées")
                    .vectorizer("none")
                    .properties(List.of(
                            Property.builder().name("invoiceName").dataType(List.of("string")).description("Numéro de facture").build(),
                            Property.builder().name("invoiceDate").dataType(List.of("date")).description("Date de facture").build(),
                            Property.builder().name("billingMonth").dataType(List.of("string")).description("Mois de facturation YYYY-MM").build(),
                            Property.builder().name("sellerCompanyName").dataType(List.of("string")).description("Société émettrice").build(),
                            Property.builder().name("sellerAddress").dataType(List.of("text")).description("Adresse émetteur").build(),
                            Property.builder().name("sellerRcs").dataType(List.of("string")).description("RCS émetteur").build(),
                            Property.builder().name("clientCompanyName").dataType(List.of("string")).description("Société cliente").build(),
                            Property.builder().name("clientAddress").dataType(List.of("text")).description("Adresse client").build(),
                            Property.builder().name("clientRcs").dataType(List.of("string")).description("RCS client").build(),
                            Property.builder().name("invoiceTitle").dataType(List.of("string")).description("Intitulé de facture").build(),
                            Property.builder().name("daysCount").dataType(List.of("int")).description("Nombre de jours").build(),
                            Property.builder().name("unitPriceHt").dataType(List.of("number")).description("Prix HT par jour").build(),
                            Property.builder().name("totalHt").dataType(List.of("number")).description("Total HT").build(),
                            Property.builder().name("vatRate").dataType(List.of("number")).description("Taux de TVA").build(),
                            Property.builder().name("totalTtc").dataType(List.of("number")).description("Total TTC").build(),
                            Property.builder().name("currency").dataType(List.of("string")).description("Devise").build(),
                            Property.builder().name("paymentDueDate").dataType(List.of("date")).description("Date d'échéance").build(),
                            Property.builder().name("latePaymentClause").dataType(List.of("text")).description("Clause pénalités de retard").build(),
                            Property.builder().name("notes").dataType(List.of("text")).description("Notes").build(),
                            Property.builder().name("sourceText").dataType(List.of("text")).description("Texte source").build(),
                            Property.builder().name("pdfPath").dataType(List.of("string")).description("Chemin PDF").build(),
                            Property.builder().name("excelPath").dataType(List.of("string")).description("Chemin Excel").build(),
                            Property.builder().name("text").dataType(List.of("text")).description("Texte concaténé pour embedding").build()
                    ))
                    .build();

            Result<Boolean> creationInvoice = client.schema().classCreator()
                    .withClass(invoice)
                    .run();
            if (creationInvoice.hasErrors()) {
                throw new IllegalStateException("Weaviate invoice class creation error: " + creationInvoice.getError());
            }
        } else {
            log.info("✅ Weaviate: classe '{}' déjà présente", invoiceClassName);
            ensureInvoiceProperties(schemaResult.getResult(), invoiceClassName);
        }

        boolean sellerProfileExists = schema != null
                && schema.getClasses() != null
                && schema.getClasses().stream().anyMatch(c -> sellerProfileClassName.equals(c.getClassName()));

        if (!sellerProfileExists) {
            log.info("🧱 Weaviate: création de la classe '{}'", sellerProfileClassName);
            WeaviateClass sellerProfile = WeaviateClass.builder()
                    .className(sellerProfileClassName)
                    .description("Profils des sociétés émettrices")
                    .vectorizer("none")
                    .properties(List.of(
                            Property.builder().name("companyName").dataType(List.of("string")).description("Nom de la société").build(),
                            Property.builder().name("address").dataType(List.of("text")).description("Adresse").build(),
                            Property.builder().name("rcs").dataType(List.of("string")).description("RCS").build(),
                            Property.builder().name("iban").dataType(List.of("string")).description("IBAN").build(),
                            Property.builder().name("bic").dataType(List.of("string")).description("BIC").build(),
                            Property.builder().name("email").dataType(List.of("string")).description("Email").build(),
                            Property.builder().name("capital").dataType(List.of("string")).description("Capital").build()
                    ))
                    .build();

            Result<Boolean> creationSellerProfile = client.schema().classCreator()
                    .withClass(sellerProfile)
                    .run();
            if (creationSellerProfile.hasErrors()) {
                throw new IllegalStateException("Weaviate seller profile class creation error: " + creationSellerProfile.getError());
            }
        } else {
            ensureSellerProfileProperties(schemaResult.getResult(), sellerProfileClassName);
        }
    }

    private void ensureSellerProfileProperties(Schema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
            return;
        }
        WeaviateClass sellerProfile = schema.getClasses().stream()
                .filter(c -> className.equals(c.getClassName()))
                .findFirst()
                .orElse(null);
        if (sellerProfile == null || sellerProfile.getProperties() == null) {
            return;
        }
        var existing = new ArrayList<String>();
        sellerProfile.getProperties().forEach(p -> existing.add(p.getName()));

        List<Property> desired = List.of(
                Property.builder().name("iban").dataType(List.of("string")).description("IBAN").build(),
                Property.builder().name("bic").dataType(List.of("string")).description("BIC").build()
        );

        for (Property prop : desired) {
            if (!existing.contains(prop.getName())) {
                Result<Boolean> creation = client.schema().propertyCreator()
                        .withClassName(className)
                        .withProperty(prop)
                        .run();
                if (creation.hasErrors()) {
                    log.warn("⚠️ Impossible d'ajouter la propriété '{}' dans '{}': {}", prop.getName(), className, creation.getError());
                } else {
                    log.info("➕ Propriété '{}' ajoutée dans la classe '{}'", prop.getName(), className);
                }
            }
        }
    }

    /**
     * Si la classe Expense existe déjà mais sans certains champs (ex: dateText),
     * on ajoute dynamiquement les propriétés manquantes.
     */
    private void ensureExpenseProperties(Schema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
            return;
        }
        WeaviateClass expense = schema.getClasses().stream()
                .filter(c -> className.equals(c.getClassName()))
                .findFirst()
                .orElse(null);
        if (expense == null || expense.getProperties() == null) {
            return;
        }
        var existing = new ArrayList<String>();
        expense.getProperties().forEach(p -> existing.add(p.getName()));

        List<Property> desired = List.of(
                Property.builder().name("dateText").dataType(List.of("string")).description("Date ISO (texte)").build(),
                Property.builder().name("text").dataType(List.of("text")).description("Texte concaténé pour embedding").build(),
                Property.builder().name("hash").dataType(List.of("string")).description("Hash binaire/texte").build(),
                Property.builder().name("paymentMode").dataType(List.of("string")).description("Personnel|Business").build(),
                Property.builder().name("address").dataType(List.of("text")).description("Adresse/lieu").build(),
                Property.builder().name("expenseId").dataType(List.of("int")).description("Id incrémental mensuel").build(),
                Property.builder().name("km").dataType(List.of("number")).description("Kilométrage (frais km)").build(),
                Property.builder().name("company").dataType(List.of("string")).description("Société/organisation").build()
        );

        for (Property prop : desired) {
            if (!existing.contains(prop.getName())) {
                Result<Boolean> creation = client.schema().propertyCreator()
                        .withClassName(className)
                        .withProperty(prop)
                        .run();
                if (creation.hasErrors()) {
                    log.warn("⚠️ Impossible d'ajouter la propriété '{}' dans '{}': {}", prop.getName(), className, creation.getError());
                } else {
                    log.info("➕ Propriété '{}' ajoutée dans la classe '{}'", prop.getName(), className);
                }
            }
        }
    }

    private void ensureInvoiceProperties(Schema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
            return;
        }
        WeaviateClass invoice = schema.getClasses().stream()
                .filter(c -> className.equals(c.getClassName()))
                .findFirst()
                .orElse(null);
        if (invoice == null || invoice.getProperties() == null) {
            return;
        }
        var existing = new ArrayList<String>();
        invoice.getProperties().forEach(p -> existing.add(p.getName()));

        List<Property> desired = List.of(
                Property.builder().name("billingMonth").dataType(List.of("string")).description("Mois de facturation YYYY-MM").build(),
                Property.builder().name("sellerCompanyName").dataType(List.of("string")).description("Société émettrice").build(),
                Property.builder().name("sellerAddress").dataType(List.of("text")).description("Adresse émetteur").build(),
                Property.builder().name("sellerRcs").dataType(List.of("string")).description("RCS émetteur").build(),
                Property.builder().name("clientCompanyName").dataType(List.of("string")).description("Société cliente").build(),
                Property.builder().name("clientAddress").dataType(List.of("text")).description("Adresse client").build(),
                Property.builder().name("clientRcs").dataType(List.of("string")).description("RCS client").build(),
                Property.builder().name("invoiceTitle").dataType(List.of("string")).description("Intitulé de facture").build(),
                Property.builder().name("daysCount").dataType(List.of("int")).description("Nombre de jours").build(),
                Property.builder().name("unitPriceHt").dataType(List.of("number")).description("Prix HT par jour").build(),
                Property.builder().name("totalHt").dataType(List.of("number")).description("Total HT").build(),
                Property.builder().name("vatRate").dataType(List.of("number")).description("Taux de TVA").build(),
                Property.builder().name("totalTtc").dataType(List.of("number")).description("Total TTC").build(),
                Property.builder().name("paymentDueDate").dataType(List.of("date")).description("Date d'échéance").build(),
                Property.builder().name("latePaymentClause").dataType(List.of("text")).description("Clause pénalités de retard").build(),
                Property.builder().name("sourceText").dataType(List.of("text")).description("Texte source").build(),
                Property.builder().name("pdfPath").dataType(List.of("string")).description("Chemin PDF").build(),
                Property.builder().name("excelPath").dataType(List.of("string")).description("Chemin Excel").build(),
                Property.builder().name("text").dataType(List.of("text")).description("Texte concaténé pour embedding").build()
        );

        for (Property prop : desired) {
            if (!existing.contains(prop.getName())) {
                Result<Boolean> creation = client.schema().propertyCreator()
                        .withClassName(className)
                        .withProperty(prop)
                        .run();
                if (creation.hasErrors()) {
                    log.warn("⚠️ Impossible d'ajouter la propriété '{}' dans '{}': {}", prop.getName(), className, creation.getError());
                } else {
                    log.info("➕ Propriété '{}' ajoutée dans la classe '{}'", prop.getName(), className);
                }
            }
        }
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(schemaInitBackoff.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public List<ExpenseItem> findExpensesBetween(LocalDate start, LocalDate end) {
        return findExpensesBetween(start, end, null, null);
    }

    public List<ExpenseItem> findExpensesBetween(LocalDate start, LocalDate end, String type, String currency) {
        try {
            String startIso = formatRfc3339(start);
            String endExclusiveIso = formatRfc3339(end.plusDays(1));

            WhereFilter afterStart = WhereFilter.builder()
                    .path("date")
                    .operator(Operator.GreaterThanEqual)
                    .valueDate(toDate(startIso))
                    .build();

            WhereFilter beforeEnd = WhereFilter.builder()
                    .path("date")
                    .operator(Operator.LessThan)
                    .valueDate(toDate(endExclusiveIso))
                    .build();

            List<WhereFilter> filters = new ArrayList<>();
            filters.add(afterStart);
            filters.add(beforeEnd);
            if (type != null && !type.isBlank()) {
                filters.add(WhereFilter.builder()
                        .path("type")
                        .operator(Operator.Equal)
                        .valueText(type)
                        .build());
            }
            if (currency != null && !currency.isBlank()) {
                filters.add(WhereFilter.builder()
                        .path("currency")
                        .operator(Operator.Equal)
                        .valueText(currency.toUpperCase())
                        .build());
            }

            WhereFilter combined = filters.size() == 1
                    ? filters.get(0)
                    : WhereFilter.builder().operator(Operator.And).operands(filters.toArray(new WhereFilter[0])).build();

            Result<GraphQLResponse> response = client.graphQL().get()
                    .withClassName(expenseClassName)
                    .withWhere(WhereArgument.builder().filter(combined).build())
                    .withFields(
                            Field.builder().name("expenseId").build(),
                            Field.builder().name("amount").build(),
                            Field.builder().name("currency").build(),
                            Field.builder().name("type").build(),
                            Field.builder().name("km").build(),
                            Field.builder().name("date").build(),
                            Field.builder().name("dateText").build(),
                            Field.builder().name("description").build(),
                            Field.builder().name("originalText").build(),
                            Field.builder().name("paymentMode").build(),
                            Field.builder().name("address").build(),
                            Field.builder().name("company").build(),
                            Field.builder().name("duplicateFlag").build(),
                            Field.builder().name("source").build()
                    )
                    .withLimit(200)
                    .run();

            if (response.hasErrors()) {
                log.error("❌ Weaviate findExpensesBetween error: {}", response.getError());
            } else {
                List<ExpenseItem> parsed = WeaviateResponseParser.extractExpenseItems(response.getResult(), expenseClassName);
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }

            // Fallback string-based if no results (cas schéma legacy avec date en string)
            log.warn("⚠️ Weaviate date query returned empty; trying fallback on dateText (string)");

            WhereFilter afterStartText = WhereFilter.builder()
                    .path("dateText")
                    .operator(Operator.GreaterThanEqual)
                    .valueText(start.toString())
                    .build();
            WhereFilter beforeEndText = WhereFilter.builder()
                    .path("dateText")
                    .operator(Operator.LessThan)
                    .valueText(end.plusDays(1).toString())
                    .build();

            List<WhereFilter> filtersText = new ArrayList<>();
            filtersText.add(afterStartText);
            filtersText.add(beforeEndText);
            if (type != null && !type.isBlank()) {
                filtersText.add(WhereFilter.builder()
                        .path("type")
                        .operator(Operator.Equal)
                        .valueText(type)
                        .build());
            }
            if (currency != null && !currency.isBlank()) {
                filtersText.add(WhereFilter.builder()
                        .path("currency")
                        .operator(Operator.Equal)
                        .valueText(currency.toUpperCase())
                        .build());
            }

            WhereFilter combinedText = filtersText.size() == 1
                    ? filtersText.get(0)
                    : WhereFilter.builder().operator(Operator.And).operands(filtersText.toArray(new WhereFilter[0])).build();

            Result<GraphQLResponse> responseText = client.graphQL().get()
                    .withClassName(expenseClassName)
                    .withWhere(WhereArgument.builder().filter(combinedText).build())
                    .withFields(
                            Field.builder().name("expenseId").build(),
                            Field.builder().name("amount").build(),
                            Field.builder().name("currency").build(),
                            Field.builder().name("type").build(),
                            Field.builder().name("km").build(),
                            Field.builder().name("date").build(),
                            Field.builder().name("dateText").build(),
                            Field.builder().name("description").build(),
                            Field.builder().name("originalText").build(),
                            Field.builder().name("duplicateFlag").build(),
                            Field.builder().name("paymentMode").build(),
                            Field.builder().name("address").build(),
                            Field.builder().name("company").build(),
                            Field.builder().name("source").build()
                    )
                    .withLimit(200)
                    .run();

            if (responseText.hasErrors()) {
                log.error("❌ Fallback dateText error: {}", responseText.getError());
                return List.of();
            }

            return WeaviateResponseParser.extractExpenseItems(responseText.getResult(), expenseClassName);

        } catch (Exception e) {
            log.error("❌ Exception findExpensesBetween: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private String formatRfc3339(LocalDate date) {
        return date.atTime(LocalTime.MIDNIGHT)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }

    /**
     * Recherche le max expenseId pour un mois donné (permet de reprendre après redémarrage).
     */
    public int findMaxExpenseId(YearMonth ym) {
        try {
            String startIso = formatRfc3339(ym.atDay(1));
            String endIso = formatRfc3339(ym.plusMonths(1).atDay(1));

            WhereFilter afterStart = WhereFilter.builder()
                    .path("date")
                    .operator(Operator.GreaterThanEqual)
                    .valueDate(toDate(startIso))
                    .build();

            WhereFilter beforeEnd = WhereFilter.builder()
                    .path("date")
                    .operator(Operator.LessThan)
                    .valueDate(toDate(endIso))
                    .build();

            WhereFilter combined = WhereFilter.builder()
                    .operator(Operator.And)
                    .operands(new WhereFilter[]{afterStart, beforeEnd})
                    .build();

            Result<GraphQLResponse> response = client.graphQL().get()
                    .withClassName(expenseClassName)
                    .withWhere(WhereArgument.builder().filter(combined).build())
                    .withFields(
                            Field.builder().name("expenseId").build()
                    )
                    .withLimit(500)
                    .run();

            if (response.hasErrors()) {
                log.warn("⚠️ findMaxExpenseId erreur: {}", response.getError());
                return 0;
            }
            List<ExpenseItem> items = WeaviateResponseParser.extractExpenseItems(response.getResult(), expenseClassName);
            return items.stream()
                    .filter(e -> e.getId() != null)
                    .mapToInt(ExpenseItem::getId)
                    .max()
                    .orElse(0);
        } catch (Exception e) {
            log.warn("⚠️ findMaxExpenseId exception: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Recherche les K chunks les plus proches d’un embedding.
     *
     * @param vector embedding de la requête
     * @param k      nombre de résultats
     * @return       liste des champs 'text' des objets trouvés
     */

    public List<String> searchByVector(List<Double> vector, int k) {
        try {
            Float[] weaviateVector = toFloatArray(vector);
            NearVectorArgument nearVector = NearVectorArgument.builder()
                    .vector(weaviateVector)
                    .build();

            Result<GraphQLResponse> response = client.graphQL().get()
                    .withClassName(className)
                    .withFields(Field.builder().name("text").build())
                    .withNearVector(nearVector)
                    .withLimit(k)
                    .run();

            if (response.hasErrors()) {
                log.error("❌ Weaviate search error: {}", response.getError());
                return List.of();
            }

            GraphQLResponse gql = response.getResult();
            if (gql == null || gql.getData() == null) {
                return List.of();
            }

            Object get = gql.getData();
            if (!(get instanceof Map<?, ?> getMap)) {
                return List.of();
            }

            Object raw = getMap.get(className);
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }

            List<String> results = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> obj) {
                    Object text = obj.get("text");
                    if (text != null) {
                        results.add(text.toString());
                    }
                }
            }

            return results;

        } catch (Exception e) {
            log.error("❌ Weaviate searchByVector error: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private Float[] toFloatArray(float[] values) {
        Float[] result = new Float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
    }

    private Float[] toFloatArray(List<Double> vector) {
        if (vector == null) {
            return new Float[0];
        }
        Float[] result = new Float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            Double value = vector.get(i);
            result[i] = value == null ? 0f : value.floatValue();
        }
        return result;
    }
}
