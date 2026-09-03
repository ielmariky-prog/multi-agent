package io.multiagent.core.service;

import io.multiagent.core.model.ExpenseItem;
import io.multiagent.core.model.ExpenseReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseExcelService {

    @Value("${ai-core.expense.km-per-day:50}")
    private int kmPerDay;

    @Value("${ai-core.expense.km-rate-7cv:0.661}")
    private double kmRate;

    @Value("${ai-core.expense.km-annual:11000}")
    private int kmAnnual;

    private final KmCalculatorService kmCalculator;

    public byte[] buildExcel(ExpenseReportResponse report) {
        // Km uniquement sur les dates des dépenses, en jours ouvrés (lun-ven), pas de doublon
        log.info("buildExcel: {}",report.getExpenses());
        List<java.time.LocalDate> workdays = computeKmDates(report);
        double totalKm = workdays.size() * (double) kmPerDay;
        // Coût km selon barème fiscal 7CV
        double costPerKm = computeCostPerKm();
        double kmCost = round2(costPerKm * totalKm);
        try (Workbook wb = new XSSFWorkbook()) {
            CreationHelper helper = wb.getCreationHelper();
            Sheet detail = wb.createSheet("Depenses");
            Sheet recap = wb.createSheet("Synthese");

            // Styles
            CellStyle header = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00 €"));

            // Entête personnalisée (4 premières lignes)
            Row title = detail.createRow(0);
            title.createCell(0).setCellValue("Rapport de notes de frais");
            title.getCell(0).setCellStyle(header);
            detail.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

            Row company = detail.createRow(1);
            company.createCell(0).setCellValue("Entreprise : " + nvl(report.getCompany(), "Non fournie"));
            company.getCell(0).setCellStyle(header);
            detail.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 3));

            String monthLabel = formatMonthLabel(report);
            Row monthRow = detail.createRow(2);
            monthRow.createCell(0).setCellValue("Mois : " + monthLabel);
            monthRow.getCell(0).setCellStyle(header);
            detail.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 0, 3));

            Row spacer = detail.createRow(3);

            // Headers
            String[] cols = {
                    "id", "date", "amount", "currency", "type", "paymentMode", "address", "description", "km"
            };
            Row h = detail.createRow(4);
            for (int i = 0; i < cols.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(header);
            }

            // Dropdowns
            DataValidationHelper dvHelper = detail.getDataValidationHelper();
            DataValidationConstraint payConstraint = dvHelper.createExplicitListConstraint(new String[]{"Personnel", "Business"});
            DataValidation payValidation = dvHelper.createValidation(payConstraint, new CellRangeAddressList(5, 500, 5, 5));
            payValidation.setSuppressDropDownArrow(true);
            detail.addValidationData(payValidation);

            // Data rows (triées par date croissante)
            boolean hasKmFromReport = false;
            int rowIdx = 5;
            List<ExpenseItem> sortedExpenses = report.getExpenses() == null
                    ? List.of()
                    : report.getExpenses().stream()
                        .sorted(java.util.Comparator.comparing(e -> parseDate(e.getDate()), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                        .toList();
            for (ExpenseItem e : sortedExpenses) {
                if ("frais_km".equalsIgnoreCase(nvl(e.getType(), ""))) {
                    hasKmFromReport = true;
                }
                log.info("report.getExpenses(): {}",e);
                Row r = detail.createRow(rowIdx);
                // id
                if (e.getId() != null) {
                    r.createCell(0).setCellValue(e.getId());
                } else {
                    r.createCell(0).setBlank();
                }
                // date
                r.createCell(1).setCellValue(formatDateForExcel(parseDate(e.getDate())));
                // amount
                Cell ca = r.createCell(2);
                if (e.getAmount() != null) {
                    ca.setCellValue(e.getAmount());
                    ca.setCellStyle(money);
                }
                // currency
                r.createCell(3).setCellValue(nvl(e.getCurrency(), ""));
                // type
                r.createCell(4).setCellValue(nvl(e.getType(), ""));
                // paymentMode
                r.createCell(5).setCellValue(nvl(e.getPaymentMode(), ""));
                // address
                r.createCell(6).setCellValue(nvl(e.getAddress(), ""));
                // description
                r.createCell(7).setCellValue(nvl(e.getDescription(), ""));
                // km (vide pour les dépenses classiques)
                if (e.getKm() != null) {
                    r.createCell(8).setCellValue(e.getKm());
                } else {
                    r.createCell(8).setBlank();
                }
               
                rowIdx++;
            }
            // Pas d'ajout automatique de lignes km : on respecte les données en base
            // Recap sheet
            Row rh = recap.createRow(0);
            rh.createCell(0).setCellValue("Période");
            rh.createCell(1).setCellValue(report.getStart().toString() + " → " + report.getEnd().toString());

            Row r1 = recap.createRow(2);
            r1.createCell(0).setCellValue("Total dép. personnelles (hors km et location)");
            r1.createCell(1).setCellFormula("SUMIFS(Depenses!C:C,Depenses!F:F,\"Personnel\",Depenses!E:E,\"<>frais_km\",Depenses!E:E,\"<>location\")");
            Row r2 = recap.createRow(3);
            r2.createCell(0).setCellValue("Total dép. business (hors km et location)");
            r2.createCell(1).setCellFormula("SUMIFS(Depenses!C:C,Depenses!F:F,\"Business\",Depenses!E:E,\"<>frais_km\",Depenses!E:E,\"<>location\")");
            Row rLoc = recap.createRow(4);
            rLoc.createCell(0).setCellValue("Total location (domiciliation)");
            rLoc.createCell(1).setCellFormula("SUMIFS(Depenses!C:C,Depenses!E:E,\"location\")");
            Row r3 = recap.createRow(5);
            r3.createCell(0).setCellValue("Km (dates de dépense, lun-ven)");
            r3.createCell(1).setCellValue(totalKm);
            Row r4 = recap.createRow(6);
            r4.createCell(0).setCellValue("Coût km");
            r4.createCell(1).setCellValue(kmCost);
            Row r5 = recap.createRow(7);
            r5.createCell(0).setCellValue("Total général (perso + business + location + km)");
            r5.createCell(1).setCellFormula("SUM(B3,B4,B5,B7)");

            // Total remboursement (somme des dépenses personnelles uniquement, hors km et location)
            Row r6 = recap.createRow(9);
            Cell totalRembLabel = r6.createCell(0);
            totalRembLabel.setCellValue("Total à rembourser (Personnel, incluant location & km)");
            CellStyle redBold = wb.createCellStyle();
            Font redBoldFont = wb.createFont();
            redBoldFont.setBold(true);
            redBoldFont.setColor(IndexedColors.RED.getIndex());
            redBold.setFont(redBoldFont);
            totalRembLabel.setCellStyle(redBold);

            Cell totalRembValue = r6.createCell(1);
            totalRembValue.setCellFormula("SUMIFS(Depenses!C:C,Depenses!F:F,\"Personnel\")");
            CellStyle redBoldMoney = wb.createCellStyle();
            redBoldMoney.cloneStyleFrom(money);
            Font redBoldFont2 = wb.createFont();
            redBoldFont2.setBold(true);
            redBoldFont2.setColor(IndexedColors.RED.getIndex());
            redBoldMoney.setFont(redBoldFont2);
            totalRembValue.setCellStyle(redBoldMoney);

            // Autosize
            for (int i = 0; i < cols.length; i++) {
                detail.autoSizeColumn(i);
            }
            recap.autoSizeColumn(0);
            recap.autoSizeColumn(1);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Échec génération Excel : " + e.getMessage(), e);
        }
    }

    private String nvl(String s, String def) {
        return s == null ? def : s;
    }

    private List<java.time.LocalDate> computeKmDates(ExpenseReportResponse report) {
        java.util.Set<java.time.LocalDate> unique = new java.util.HashSet<>();
        if (report.getExpenses() != null) {
            for (io.multiagent.core.model.ExpenseItem e : report.getExpenses()) {
                if (!"frais_km".equalsIgnoreCase(nvl(e.getType(), ""))) {
                    continue;
                }
                java.time.LocalDate d = parseDate(e.getDate());
                if (d != null) {
                    unique.add(d);
                }
            }
        }
        return unique.stream().sorted().toList();
    }

    private java.time.LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            if (date.contains("T")) {
                return java.time.LocalDate.parse(date.substring(0, 10));
            }
            return java.time.LocalDate.parse(date);
        } catch (Exception e) {
            return null;
        }
    }

    private double computeCostPerKm() {
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

    private String formatDateForExcel(java.time.LocalDate date) {
        if (date == null) return "";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
        return date.format(fmt);
    }

    private String formatMonthLabel(ExpenseReportResponse report) {
        if (report.getStart() == null) return "";
        java.time.LocalDate start = report.getStart();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
        return start.format(fmt);
    }
}
