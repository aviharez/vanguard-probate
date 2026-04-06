package com.project.model;

import java.time.LocalDate;

/**
 * Encapsulates all inputs supplied by the paralegal for a single asset valuation.
 */
public class ValuationRequest {

    private final LocalDate valuationDate;
    private final String sourceCurrency;
    private final double amount;
    private final String caseReference;
    private final String assetDescription;

    public ValuationRequest(LocalDate valuationDate,
                            String sourceCurrency,
                            double amount,
                            String caseReference,
                            String assetDescription) {
        this.valuationDate = valuationDate;
        this.sourceCurrency = sourceCurrency.toUpperCase().trim();
        this.amount = amount;
        this.caseReference = caseReference.trim();
        this.assetDescription = assetDescription.trim();
    }

    public LocalDate getValuationDate() {
        return valuationDate;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public double getAmount() {
        return amount;
    }

    public String getCaseReference() {
        return caseReference;
    }

    public String getAssetDescription() {
        return assetDescription;
    }

}
