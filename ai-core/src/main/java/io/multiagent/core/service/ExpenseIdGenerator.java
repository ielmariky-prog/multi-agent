package io.multiagent.core.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Génère un identifiant incrémental réinitialisé chaque mois (année-mois).
 * Incrémente à partir du max existant dans Weaviate pour éviter les resets après redémarrage.
 */
public class ExpenseIdGenerator {

    private final ConcurrentHashMap<YearMonth, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final WeaviateService weaviateService;

    public ExpenseIdGenerator(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
    }

    public int nextId(LocalDate date) {
        YearMonth ym = YearMonth.from(date);
        return counters.computeIfAbsent(ym, this::initCounter).incrementAndGet();
    }

    private AtomicInteger initCounter(YearMonth ym) {
        int start = 0;
        try {
            start = weaviateService.findMaxExpenseId(ym);
        } catch (Exception ignored) {
        }
        return new AtomicInteger(start);
    }
}
