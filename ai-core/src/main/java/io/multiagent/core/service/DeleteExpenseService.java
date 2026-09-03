package io.multiagent.core.service;

import io.multiagent.core.model.IntentResult;
import io.multiagent.core.model.ReasoningResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteExpenseService {

    private final WeaviateService weaviateService;

    public ReasoningResult deleteByText(String text, IntentResult intent) {
        ParsedDelete parsed = parse(text);
        // surcharge avec les entités éventuelles (ids, date) si présentes
        if (intent != null && intent.getEntities() != null) {
            Object companyObj = intent.getEntities().get("company");
            if (companyObj instanceof String s && !s.isBlank()) {
                parsed.company = s.trim();
            }
            Object idsObj = intent.getEntities().get("ids");
            if (idsObj instanceof Iterable<?> iter) {
                for (Object o : iter) {
                    addId(parsed, o);
                }
            }
            Object dateObj = intent.getEntities().get("date");
            if (dateObj instanceof String s && parsed.date == null) {
                try { parsed.date = LocalDate.parse(s); parsed.month = YearMonth.from(parsed.date); } catch (Exception ignored) {}
            }
            Object monthObj = intent.getEntities().get("month");
            if (monthObj instanceof String s && parsed.month == null) {
                try { parsed.month = YearMonth.parse(s); } catch (Exception ignored) {}
            }
        }
        if (parsed.company == null || parsed.company.isBlank()) {
            return ReasoningResult.error("Suppression impossible : le nom de la société/organisation doit être précisé.");
        }
        int deleted = 0;

        if (!parsed.expenseIds.isEmpty()) {
            if (parsed.month != null || parsed.date != null) {
                if (parsed.month == null && parsed.date != null) {
                    parsed.month = YearMonth.from(parsed.date);
                }
                for (Integer id : parsed.expenseIds) {
                    int d = weaviateService.deleteExpenseByIdAndMonth(id, parsed.month, parsed.company);
                    log.info("🗑️ DeleteExpenseService -> expenseId={}, month={}, company={}, deleted={}", id, parsed.month, parsed.company, d);
                    if (d >= 0) {
                        deleted += d;
                    }
                }
            } else {
                // pas de date/mois → suppression par id seul
                for (Integer id : parsed.expenseIds) {
                    int d = weaviateService.deleteExpenseById(id, parsed.company);
                    log.info("🗑️ DeleteExpenseService -> expenseId={}, company={}, deleted={}", id, parsed.company, d);
                    if (d >= 0) {
                        deleted += d;
                    }
                }
            }
        } else if (parsed.date != null) {
            deleted = weaviateService.deleteExpensesByDate(parsed.date, parsed.company);
            log.info("🗑️ DeleteExpenseService -> date={}, company={}, deleted={}", parsed.date, parsed.company, deleted);
        } else {
            return ReasoningResult.error("Impossible de déterminer la date ou l'id à supprimer");
        }

        var meta = new HashMap<String, Object>();
        meta.put("date", parsed.date != null ? parsed.date.toString() : null);
        meta.put("expenseIds", parsed.expenseIds);
        meta.put("month", parsed.month != null ? parsed.month.toString() : null);
        meta.put("company", parsed.company);
        meta.put("deleted", deleted);

        if (deleted == 0) {
            return ReasoningResult.error(
                    "Aucune note de frais supprimée pour ids=%s, month=%s, company=%s"
                            .formatted(parsed.expenseIds,
                                    parsed.month != null ? parsed.month : "null",
                                    parsed.company)
            );
        }

        return ReasoningResult.builder()
                .type("delete_expense")
                .status("EXPENSE_DELETED")
                .confidence(intent.getConfidence())
                .raw(text)
                .expenses(null)
                .metadata(meta)
                .build();
    }

    private ParsedDelete parse(String text) {
        ParsedDelete p = new ParsedDelete();
        if (text == null) return p;
        String lower = text.toLowerCase(Locale.ROOT);

        // ids groupés (ex: "numéro 31 et 34 et 58", "ids 12, 13, 14")
        Matcher groupedIds = Pattern.compile(
                "(?:note[s]? de frais|dépense[s]?|num(?:e|é)?ro|ids?|notes?)\\s+((?:\\d+[\\s,;etou-]*)+)"
        ).matcher(lower);
        while (groupedIds.find()) {
            Matcher numberMatcher = Pattern.compile("\\d+").matcher(groupedIds.group(1));
            while (numberMatcher.find()) {
                addId(p, numberMatcher.group());
            }
        }

        // ids simples (ex: "note de frais 6", "numéro 5")
        Matcher mid = Pattern.compile("(?:note[s]? de frais|dépense[s]?|num(?:e|é)?ro|id)\\s*(\\d+)").matcher(lower);
        while (mid.find()) {
            addId(p, mid.group(1));
        }
        // fallback : capturer tous les nombres courts non-année
        Matcher mAny = Pattern.compile("\\b(\\d{1,4})\\b").matcher(lower);
        while (mAny.find()) {
            String num = mAny.group(1);
            try {
                int val = Integer.parseInt(num);
                // ignorer les années probables
                if (val >= 1900 && val <= 2100) continue;
                addId(p, num);
            } catch (Exception ignored) {}
        }

        // dates explicites YYYY-MM-DD
        if (p.date == null) {
            Matcher dateIso = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})").matcher(text);
            if (dateIso.find()) {
                try { p.date = LocalDate.parse(dateIso.group(1)); } catch (Exception ignored) {}
            }
        }
        // dates type DD/MM/YYYY ou DD-MM-YYYY
        if (p.date == null) {
            Matcher fr = Pattern.compile("(\\d{2})[/-](\\d{2})[/-](\\d{4})").matcher(text);
            if (fr.find()) {
                try {
                    int d = Integer.parseInt(fr.group(1));
                    int mth = Integer.parseInt(fr.group(2));
                    int y = Integer.parseInt(fr.group(3));
                    p.date = LocalDate.of(y, mth, d);
                } catch (Exception ignored) {}
            }
        }

        // Si on a une date explicite mais pas de month, dérive year-month
        if (p.month == null && p.date != null) {
            p.month = YearMonth.from(p.date);
        }

        // société en texte brut si le classifieur ne l'a pas fournie
        if (p.company == null || p.company.isBlank()) {
            Matcher companyMatcher = Pattern.compile("(?i)\\bpour\\s+la\\s+soci(?:e|é)t(?:e|é)\\s+([^\\.,;]+)").matcher(text);
            if (companyMatcher.find()) {
                p.company = companyMatcher.group(1).trim();
            }
        }
        return p;
    }

    private void addId(ParsedDelete p, Object num) {
        if (num == null) return;
        try {
            int v;
            if (num instanceof Number n) {
                v = n.intValue();
            } else {
                v = Integer.parseInt(num.toString());
            }
            if (!p.expenseIds.contains(v)) {
                p.expenseIds.add(v);
            }
        } catch (Exception ignored) {}
    }

    private static class ParsedDelete {
        List<Integer> expenseIds = new ArrayList<>();
        LocalDate date;
        YearMonth month;
        String company;
    }
}
