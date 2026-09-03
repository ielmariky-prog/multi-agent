package io.multiagent.core.service;

import io.multiagent.core.model.ExpenseItem;
import io.multiagent.core.model.ExpenseReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpensePdfService {

    private final ExpenseReportService expenseReportService;

    public byte[] buildPdf(String start, String end, String type, String currency, String company) {
        ExpenseReportResponse report = expenseReportService.report(start, end, type, currency, company);
        List<ExpenseItem> expenses = report.getExpenses() == null ? List.of() : report.getExpenses().stream()
                .sorted(java.util.Comparator.comparing((ExpenseItem e) -> parseDate(e.getDate()),
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String[] headers = {"date", "amount", "type", "paymentMode", "address", "description", "km"};

        try (PDDocument doc = new PDDocument()) {
            PDRectangle landscapeA4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            PDPage page = new PDPage(landscapeA4);
            doc.addPage(page);
            float margin = 30;
            float leading = 14f;
            float[] xCols = {
                    margin,        // date
                    margin + 72,   // amount
                    margin + 142,  // type
                    margin + 240,  // paymentMode
                    margin + 340,  // address
                    margin + 530,  // description
                    margin + 760   // km
            };

            PDPageContentStream cs = null;
            try {
                cs = new PDPageContentStream(doc, page);
                float y = drawExpensePageHeader(cs, page, margin, leading, report, xCols, headers);

                for (ExpenseItem e : expenses) {
                    if (y < margin + leading * 8) {
                        cs.close();
                        page = new PDPage(landscapeA4);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        y = drawExpensePageHeader(cs, page, margin, leading, report, xCols, headers);
                    }
                    writeExpenseRow(cs, y, xCols, toPdfColumns(e, df));
                    y -= leading;
                }

                // Synthèse détaillée en bas de page
                double totalPersonnelHorsKmLoc = expenses.stream()
                        .filter(ex -> "personnel".equalsIgnoreCase(safe(ex.getPaymentMode())))
                        .filter(ex -> !"frais_km".equalsIgnoreCase(safe(ex.getType())))
                        .filter(ex -> !"location".equalsIgnoreCase(safe(ex.getType())))
                        .mapToDouble(ex -> ex.getAmount() == null ? 0d : ex.getAmount())
                        .sum();
                double totalBusinessHorsKmLoc = expenses.stream()
                        .filter(ex -> "business".equalsIgnoreCase(safe(ex.getPaymentMode())))
                        .filter(ex -> !"frais_km".equalsIgnoreCase(safe(ex.getType())))
                        .filter(ex -> !"location".equalsIgnoreCase(safe(ex.getType())))
                        .mapToDouble(ex -> ex.getAmount() == null ? 0d : ex.getAmount())
                        .sum();
                double totalLocation = expenses.stream()
                        .filter(ex -> "location".equalsIgnoreCase(safe(ex.getType())))
                        .mapToDouble(ex -> ex.getAmount() == null ? 0d : ex.getAmount())
                        .sum();
                double totalKm = expenses.stream()
                        .filter(ex -> "frais_km".equalsIgnoreCase(safe(ex.getType())))
                        .mapToDouble(ex -> ex.getKm() == null ? 0d : ex.getKm())
                        .sum();
                double costKm = expenses.stream()
                        .filter(ex -> "frais_km".equalsIgnoreCase(safe(ex.getType())))
                        .mapToDouble(ex -> ex.getAmount() == null ? 0d : ex.getAmount())
                        .sum();
                double totalGeneral = expenses.stream()
                        .mapToDouble(ex -> ex.getAmount() == null ? 0d : ex.getAmount())
                        .sum();
                double totalRembPersonnel = expenses.stream()
                        .filter(ex -> "personnel".equalsIgnoreCase(safe(ex.getPaymentMode())))
                        .mapToDouble(ex -> ex.getAmount() == null ? 0d : ex.getAmount())
                        .sum();

                if (y < margin + leading * 10) {
                    cs.close();
                    page = new PDPage(landscapeA4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = page.getMediaBox().getHeight() - margin;
                } else {
                    y -= leading * 2;
                }
                cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                writeLine(cs, margin, y, "Synthese");
                y -= leading;
                cs.setFont(PDType1Font.HELVETICA, 10);
                writeLine(cs, margin, y, "Total dép. personnelles (hors km et location) : " + safeMoney(totalPersonnelHorsKmLoc));
                y -= leading;
                writeLine(cs, margin, y, "Total dép. business (hors km et location) : " + safeMoney(totalBusinessHorsKmLoc));
                y -= leading;
                writeLine(cs, margin, y, "Total location (domiciliation) : " + safeMoney(totalLocation));
                y -= leading;
                writeLine(cs, margin, y, "Km (dates de dépense, lun-ven) : " + safeMoney(totalKm));
                y -= leading;
                writeLine(cs, margin, y, "Coût km : " + safeMoney(costKm));
                y -= leading;
                writeLine(cs, margin, y, "Total général (perso + business + location + km) : " + safeMoney(totalGeneral));
                y -= leading * 2;
                cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                writeLine(cs, margin, y, "Total à rembourser (Personnel, incluant location & km) : " + safeMoney(totalRembPersonnel));
            } finally {
                if (cs != null) {
                    cs.close();
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Echec génération PDF : " + e.getMessage(), e);
        }
    }

    private String safe(Object o) {
        return o == null ? "" : o.toString();
    }

    private String safeMoney(Double d) {
        if (d == null) return "";
        return String.format(Locale.FRANCE, "%.2f", d);
    }

    private String safeText(String s) {
        if (s == null) return "";
        String normalized = s.replace('\u2192', '-'); // remplace la flèche par un tiret
        StringBuilder sb = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            // Accepter les caractères WinAnsi (<= 255) sinon remplacer par '?'
            if (c >= 32 && c <= 255) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private void writeLine(PDPageContentStream cs, float x, float y, String text) throws Exception {
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(safeText(text));
        cs.endText();
    }

    private void writeRow(PDPageContentStream cs, float y, float[] xCols, String[] values) throws Exception {
        for (int i = 0; i < values.length; i++) {
            cs.beginText();
            cs.newLineAtOffset(xCols[i], y);
            cs.showText(safeText(values[i]));
            cs.endText();
        }
    }

    private void writeExpenseHeader(PDPageContentStream cs, float y, float[] xCols, String[] headers) throws Exception {
        cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
        writeRow(cs, y, xCols, headers);
    }

    private float drawExpensePageHeader(
            PDPageContentStream cs,
            PDPage page,
            float margin,
            float leading,
            ExpenseReportResponse report,
            float[] xCols,
            String[] headers
    ) throws Exception {
        float y = page.getMediaBox().getHeight() - margin;
        cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
        writeLine(cs, margin, y, "Rapport de notes de frais");
        y -= leading;
        cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
        writeLine(cs, margin, y, safeText("Entreprise : " + report.getCompany()));
        y -= leading;
        writeLine(cs, margin, y, safeText("Periode : " + report.getStart() + " -> " + report.getEnd()));
        y -= leading * 2;
        writeExpenseHeader(cs, y, xCols, headers);
        return y - leading;
    }

    private void writeExpenseRow(PDPageContentStream cs, float y, float[] xCols, String[] values) throws Exception {
        cs.setFont(PDType1Font.HELVETICA, 9);
        writeRow(cs, y, xCols, values);
    }

    private String[] toPdfColumns(ExpenseItem expense, DateTimeFormatter df) {
        String dateStr = expense.getDate();
        if (dateStr != null && dateStr.length() >= 10) {
            try {
                dateStr = java.time.LocalDate.parse(dateStr.substring(0, 10)).format(df);
            } catch (Exception ignored) {
            }
        }
        return new String[]{
                safe(dateStr),
                safeMoney(expense.getAmount()),
                safe(expense.getType()),
                safe(expense.getPaymentMode()),
                truncate(expense.getAddress(), 28),
                truncate(expense.getDescription(), 34),
                safe(expense.getKm())
        };
    }

    private String truncate(String value, int maxLen) {
        String safe = safe(value);
        if (safe.length() <= maxLen) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private java.time.LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            if (date.length() >= 10) {
                return java.time.LocalDate.parse(date.substring(0, 10));
            }
            return java.time.LocalDate.parse(date);
        } catch (Exception e) {
            return null;
        }
    }
}
