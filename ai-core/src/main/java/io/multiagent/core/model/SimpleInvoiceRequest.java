package io.multiagent.core.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SimpleInvoiceRequest(
        String invoiceName,
        LocalDate invoiceDate,
        String billingMonth,
        String sellerCompanyName,
        String sellerAddress,
        String sellerRcs,
        String clientCompanyName,
        String clientAddress,
        String clientRcs,
        String invoiceTitle,
        Integer daysCount,
        BigDecimal unitPriceHt,
        BigDecimal totalHt,
        BigDecimal vatRate,
        BigDecimal totalTtc,
        String currency,
        LocalDate paymentDueDate,
        String latePaymentClause,
        String notes
) {
}
