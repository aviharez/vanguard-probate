package com.project.api;

import java.util.Map;

/**
 * Maps the JSON response body returned by the Frankfurter API.
 *
 * <p>When the requested date falls on a weekend or ECB non-publishing day,
 * Frankfurter automatically returns the nearest prior business day's rate;
 * the {@code date} field will reflect that adjusted date.</p>
 */
public class ExchangeRateResponse {

    /** The amount used as the base for the conversion (mirror request). */
    public double amount;

    /** The source (base) currency code, e.g., "GBP". */
    public String base;

    /**
     * The date for which the rate is valid. May differ from the requested
     * date when Frankfurter falls back to a prior business day.
     */
    public String date;

    /** Target currency rates keyed by ISO 4217 code, e.g. {"USD": 1.2456}. */
    public Map<String, Double> rates;

    /**
     * Present only on error responses, e.g. {"message": "no data for date"}.
     * A non-null value indicates the request failed.
     */
    public String message;

}
