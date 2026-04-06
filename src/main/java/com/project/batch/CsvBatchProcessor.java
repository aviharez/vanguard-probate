package com.project.batch;

import com.project.model.ValuationRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a CSV file and produces a list of {@link ValuationRequest} objects.
 *
 * <h2>Expected CSV format</h2>
 * <p>The first row must be the header (it is skipped). Columns:</p>
 * <pre>
 * case_reference,asset_description,valuation_date,currency,amount
 * </pre>
 *
 * <ul>
 *     <li>Fields may be quoted with double-quotes.</li>
 *     <li>Quoted fields may contain commas.</li>
 *     <li>Empty rows and rows beginning with {@code #} are skipped.</li>
 * </ul>
 */
public class CsvBatchProcessor {

    /**
     * Parses the CSV at {@code path} and returns a validated list of requests.
     *
     * @param path path to the CSV file
     * @return list of requests, one per data row
     * @throws IOException if the file cannot be read
     * @throws CsvParseException if (any row is malformed or contains invalid data
     */
    public List<ValuationRequest> parse(Path path) throws IOException {
        List<String> rawLines = Files.readAllLines(path, StandardCharsets.UTF_8);

        if (rawLines.isEmpty()) {
            throw new CsvParseException("CSV file is empty.");
        }

        List<ValuationRequest> requests = new ArrayList<>();
        boolean headerSkipped = false;

        for (int lineNum = 1; lineNum <= rawLines.size(); lineNum++) {
            String raw = rawLines.get(lineNum - 1).trim();

            // Skip blank lines and comment lines
            if (raw.isEmpty() || raw.startsWith("#")) continue;

            // Skip header row (first non-blank, non-comment line)
            if (!headerSkipped) {
                headerSkipped = true;
                continue;
            }

            List<String> fields = splitCsvRow(raw, lineNum);

            if (fields.size() != 5) {
                throw new CsvParseException(String.format(
                        "Line %d: expected 5 columns (case_reference, asset_description, " +
                                "valuation_date, currency, amount) but found %d.",
                        lineNum, fields.size()
                ));
            }

            String caseRef = fields.get(0).trim();
            String assetDesc = fields.get(1).trim();
            String rawDate = fields.get(2).trim();
            String currency = fields.get(3).trim();
            String rawAmount = fields.get(4).trim().replace(",", "");

            if (caseRef.isBlank())
                throw new CsvParseException("Line " + lineNum + ": case_reference is empty.");
            if (assetDesc.isBlank())
                throw new CsvParseException("Line " + lineNum + ": asset_description is empty.");
            if (currency.isBlank() || currency.length() != 3)
                throw new CsvParseException("Line " + lineNum + ": currency must be a 3-letter ISO 4217 code.");

            LocalDate date;
            try {
                date = LocalDate.parse(rawDate);
            } catch (DateTimeParseException e) {
                throw new CsvParseException("Line " + lineNum + ": invalid date '" + rawDate + "'. Use YYYY-MM-DD.");
            }

            if (date.isAfter(LocalDate.now())) {
                throw new CsvParseException("Line " + lineNum + ": valuation_date " + rawDate + " i in the future.");
            }
            if (date.isBefore(LocalDate.of(1999, 1, 4))) {
                throw new CsvParseException("Line " + lineNum + ": valueation_date " + rawDate + " is before ECB history (1999-01-04).");
            }

            double amount;
            try {
                amount = Double.parseDouble(rawAmount);
            } catch (NumberFormatException e) {
                throw new CsvParseException("Line " + lineNum + ": invalid amount '" + fields.get(4) + "'.");
            }
            if (amount <= 0) {
                throw new CsvParseException("Line " + lineNum + ": amount must be positive.");
            }

            requests.add(new ValuationRequest(date, currency, amount, caseRef, assetDesc));
        }

        if (requests.isEmpty()) {
            throw new CsvParseException("CSV file contains no data rows.");
        }

        return requests;
    }

    /**
     * Splits a single CSV row into fields, respecting double-quoted fields
     * that mau contains commas.
     */
    private List<String> splitCsvRow(String row, int lineNum) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (inQuotes) {
            throw new CsvParseException("Line " + lineNum + ": unclosed quote in CSV row.");
        }

        fields.add(current.toString());
        return fields;
    }

    /** Thrown when the CSV file cannot be parsed */
    public static class CsvParseException extends RuntimeException {
        public CsvParseException(String message) {
            super(message);
        }
    }

}
