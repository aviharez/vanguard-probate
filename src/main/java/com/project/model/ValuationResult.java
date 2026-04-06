package com.project.model;

import java.time.LocalDate;

/**
 * Holds the computed result of a single asset valuation.
 */
public class ValuationResult {

    private final ValuationRequest request;

    /** The date for which the exchange rate is actually valid. */
    private final LocalDate rateDate;

    /** The USD/source-currency rate (i.e. 1 unit of source = rateUsed USD). */
    private final double rateUsed;

    /** Final value in USD, rounded to two decimal places. */
    private final double usdValue;

    /**
     * True when {@code rateDate} differs from the requested valuation date,
     * meaning Frankfutter fell back to a prior ECB publishing day.
     */
    private final boolean dateFallbackApplied;

    public ValuationResult(ValuationRequest request,
                           LocalDate rateDate,
                           double rateUsed,
                           double usdValue,
                           boolean dateFallbackApplied) {
        this.request = request;
        this.rateDate = rateDate;
        this.rateUsed = rateUsed;
        this.usdValue = Math.round(usdValue * 100.0) / 100.0;
        this.dateFallbackApplied = dateFallbackApplied;
    }

    public ValuationRequest getRequest() {
        return request;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public double getRateUsed() {
        return rateUsed;
    }

    public double getUsdValue() {
        return usdValue;
    }

    public boolean isDateFallbackApplied() {
        return dateFallbackApplied;
    }

}
