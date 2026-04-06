package com.project.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.project.exception.ValuationException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * HTTP client for the Frankfurter API.
 *
 * <p>Frankfurter is a free, no-authentication API that proxies historical
 * foreign-exchange rates published by the European Central Bank (ECB).
 * Rates are available from 1999-01-04 onwards for ~33 major currencies.</p>
 */
public class FrankfurterClient {

    private static final String BASE_URL = "https://api.frankfurter.app";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String TARGET_CCY = "USD";

    private final HttpClient httpClient;
    private final Gson gson;

    public FrankfurterClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.gson = new Gson();
    }

    /**
     * Fetches the USD exchange rate for {@code amount} units of {@code fromCurrency}
     * on {@code date}.
     *
     * <p>Frankfurter automatically resolves the nearest prior ECB publishing day
     * when {@code date} is a weekend or holiday. The returned
     * {@link ExchangeRateResponse#date} reflects that actual rate date./p>
     *
     * @param date              the requested valuation date.
     * @param fromCurrency      ISO 4217 source currency code (e.g. "GBP")
     * @param amount            the asset value in {@code fromCurrency}
     * @return                  the parsed response, {@link Optional#empty()} if the API
     *                          returned an explicit "no data" error for this date.
     */
    public Optional<ExchangeRateResponse> fetchRate(LocalDate date,
                                                    String fromCurrency,
                                                    double amount) {
        String url = String.format("%s/%s?from=%s&to=%s&amount=%.6f",
                BASE_URL, date,
                URLEncoder.encode(fromCurrency.toUpperCase(), StandardCharsets.UTF_8),
                TARGET_CCY, amount);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ValuationException("Unable to reach the Frankfurter API. Check your internet connection.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValuationException("Request to Frankfurter API was interrupted.", e);
        }

        ExchangeRateResponse parsed;
        try {
            parsed = gson.fromJson(response.body(), ExchangeRateResponse.class);
        } catch (JsonSyntaxException e) {
            throw new ValuationException("Unexpected response format from Frankfurter API: " + response.body(), e);
        }

        if (parsed == null) {
            throw new ValuationException("Frankfurter API returned an empty response for date " + date + ".");
        }

        // A 404 with a "message" field means no ECB data exists for this date
        if (response.statusCode() == 404 || parsed.message != null) {
            return Optional.empty();
        }

        if (response.statusCode() != 200) {
            throw new ValuationException(String.format("Frankfurter API returned HTTP %d: %s", response.statusCode(), response.body()));
        }

        if (parsed.rates == null || !parsed.rates.containsKey(TARGET_CCY)) {
            throw new ValuationException(
                    "Frankfurter API response did not include a USD rate. " +
                            "Verify that '" + fromCurrency + "' is a supported currency."
            );
        }

        return Optional.of(parsed);
    }

    /**
     * Returns the set of ISO 4217 currency codes supported by Frankfurter,
     * sorted alphabetically.
     *
     * @throws ValuationException if the API is unreachable
     */
    public Set<String> fetchSupportedCurrencies() {
        String url = BASE_URL + "/currencies";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ValuationException("Unable to reach the Frankfurter API. Check your internet connection.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValuationException("Request was interrupted.", e);
        }

        if (response.statusCode() != 200) {
            throw new ValuationException(
                    "Failed to retrieve currency list from Frankfurter API (HTTP " +
                            response.statusCode() + ")."
            );
        }

        Map<String, String> currencyMap = gson.fromJson(response.body(), Map.class);

        if (currencyMap == null) {
            throw new ValuationException("Frankfurter API returned an empty currency list.");
        }


        // USD is always a valid target; add it explicitly (Frankfurter excludes
        // the base currency from the /currencies list when USD is the target).
        TreeSet<String> codes = new TreeSet<>(currencyMap.keySet());
        codes.add("USD");
        return codes;
    }

}
