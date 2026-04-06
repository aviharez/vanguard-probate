package com.project.service;

import com.project.api.ExchangeRateResponse;
import com.project.api.FrankfurterClient;
import com.project.exception.ValuationException;
import com.project.model.ValuationRequest;
import com.project.model.ValuationResult;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates currency lookups and applies business-day fallback logic.
 *
 * <h2>Fallback strategy</h2>
 * <ol>
 *     <li>Attempt the exact requested date.</li>
 *     <li>Frankfurter already resolves weekeds/ECB holidays internally and
 *         returns the nearest prior rate. The response {@code date} field
 *         will reveal whether a fallback occurred.</li>
 *     <li>If Frankfurter returns an explicit "no data" error (HTTP 404 /
 *         {@code message} present), the service walks back day-by-day up to
 *         {@value #MAX_LOOKBACK_DAYS} calender days, ensuring correctness
 *         for obscure edge cases (e.g., dates before ECB publishing began).</li>
 * </ol>
 */
public class ValuationService {

    /** Maximum number of calendar days to look back when the API has no data. */
    private static final int MAX_LOOKBACK_DAYS = 7;

    private final FrankfurterClient client;

    public ValuationService() {
        this.client = new FrankfurterClient();
    }

    /**
     * Validates the currency code, fetches the historical exchange rate, and
     * returns a fully populated {@link ValuationResult}.
     *
     * @param request the asset details supplied by the paralegal
     * @return the computed valuation
     */
    public ValuationResult valuate(ValuationRequest request) {
        validateCurrency(request.getSourceCurrency());

        // if the source is USD, the rate is trivially 1:1.
        if ("USD".equals(request.getSourceCurrency())) {
            return new ValuationResult(
                    request,
                    request.getValuationDate(),
                    1.0,
                    request.getAmount(),
                    false
            );
        }

        ExchangeRateResponse response = fetchWithFallback(request);

        LocalDate rateDate = LocalDate.parse(response.date);
        double rateUsed = response.rates.get("USD") / response.amount;
        double usdValue = response.rates.get("USD");
        boolean fallback = !rateDate.equals(request.getValuationDate());

        return new ValuationResult(request, rateDate, rateUsed, usdValue, fallback);
    }

    /**
     * Attempts the requested date first; walks backwards up to
     * {@value #MAX_LOOKBACK_DAYS} days if the API explicitly returns no data.
     */
    private ExchangeRateResponse fetchWithFallback(ValuationRequest request) {
        LocalDate date = request.getValuationDate();

        // First attempt: let Frankfurter apply its own weekend/holiday resolution.
        Optional<ExchangeRateResponse> primary = client.fetchRate(date, request.getSourceCurrency(), request.getAmount());

        if (primary.isPresent()) {
            return primary.get();
        }

        // Manual fallback for edge cases (e.g., very old dates, API gaps).
        for (int i = 1; i <= MAX_LOOKBACK_DAYS; i++) {
            LocalDate candidate = date.minusDays(i);
            Optional<ExchangeRateResponse> result = client.fetchRate(candidate, request.getSourceCurrency(), request.getAmount());
            if (result.isPresent()) {
                return result.get();
            }
        }

        throw new ValuationException(String.format(
                "No exchange rate data available for %s within %d days prior to %s. " +
                        "The ECB does not publish rates for dates before 1999-01-04.",
                request.getSourceCurrency(), MAX_LOOKBACK_DAYS, date
        ));
    }

    /**
     * Validates that the supplied code is recognized by the Frankfurter API.
     * Fetches the live currency list so validation stays in sync with the API.
     *
     * @throws ValuationException for unknown or unsupported currency code
     */
    private void validateCurrency(String currencyCode) {
        Set<String> supported = client.fetchSupportedCurrencies();
        if (!supported.contains(currencyCode)) {
            throw new ValuationException(String.format(
                    "'%s' is not a recognized currency code. " +
                            "Supported codes: %s",
                    currencyCode, supported
            ));
        }
    }

}
