package io.multiagent.core.model;

public record InvoiceLookupRequest(
        String billingMonth,
        String sellerCompanyName,
        String invoiceName
) {
}
