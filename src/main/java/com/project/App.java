package com.project;

import com.project.batch.CsvBatchProcessor;
import com.project.cli.CliOptions;
import com.project.cli.OutputConfig;
import com.project.exception.ValuationException;
import com.project.model.ValuationRequest;
import com.project.model.ValuationResult;
import com.project.report.MemoGenerator;
import com.project.service.ValuationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for the Cross-Border Asset Valuation CLI.
 *
 * <h2>Interactive mode (default)</h2>
 * <pre>java -jar vanguard-probate-1.0.0.jar</pre>
 *
 * <h2>Batch mode</h2>
 * <pre>java -jar vanguard-probate-1.0.0.jar --input assets.csv [--output-dir ./memos] [--format pdf]</pre>
 *
 */
public class App {

    private static final String BANNER =
            "\n========================================================================\n" +
              "         VANGUARD CROSS-BORDER ESTATES -- Asset Valuation Tool       \n" +
              "========================================================================\n" +
              "  Powered by the Frankfurter API (European Central Bank reference rates)\n" +
              "  No API key required. Historical rates available from 1999-01-04.\n" +
              "========================================================================\n";

    public static void main( String[] args ) {
        // suppress PDFBox/FontBox verbose logging
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.SEVERE);
        Logger.getLogger("prg.apache.fontbox").setLevel(Level.SEVERE);
        Logger.getLogger("org.apache.pdfbox.pdmodel.PDDocument").setLevel(Level.SEVERE);

        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.err.println(CliOptions.helpText());
            System.exit(1);
            return;
        }

        if (options.isHelpRequested()) {
            System.out.println(CliOptions.helpText());
            return;
        }

        System.out.println(BANNER);

        ValuationService service = new ValuationService();
        MemoGenerator memoGen = new MemoGenerator();
        OutputConfig outputConfig = options.getOutputConfig();

        // Ensure output directory exists
        if (!outputConfig.getOutputDir().toString().isBlank()) {
            try {
                Files.createDirectories(outputConfig.getOutputDir());
            } catch (IOException e) {
                System.err.println("[ERROR] Cannot create output directory '" + outputConfig.getOutputDir() + "': " + e.getMessage());
                System.exit(1);
            }
        }

        if (options.isBatchMode()) {
            runBatchMode(options.getInputFile(), outputConfig, service, memoGen);
        } else {
            runInteractiveMode(outputConfig, service, memoGen);
        }
    }

    // Batch mode

    private static void runBatchMode(Path inputFile, OutputConfig outputConfig,
                                     ValuationService service, MemoGenerator memoGen) {
        System.out.println("  Batch mode -- reading: " + inputFile.toAbsolutePath());
        System.out.println("  Output dir : " + resolveOutputDir(outputConfig));
        System.out.println("  Format     : " + outputConfig.getFormat().name());
        System.out.println();

        List<ValuationRequest> requests;
        try {
            requests = new CsvBatchProcessor().parse(inputFile);
        } catch (IOException e) {
            System.err.println("[ERROR] Cannot read CSV file: " + e.getMessage());
            System.exit(1);
            return;
        } catch (CsvBatchProcessor.CsvParseException e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.printf("  Found %d asset(s) to process.%n%n", requests.size());

        int success = 0;
        int failure = 0;

        for (int i = 0; i < requests.size(); i++) {
            ValuationRequest req = requests.get(i);
            System.out.printf("  [%d/%d] %s - %s %s on %s%n",
                    i + 1, requests.size(),
                    req.getCaseReference(),
                    req.getSourceCurrency(),
                    formatAmount(req.getAmount()),
                    req.getValuationDate());

            try {
                ValuationResult result = service.valuate(req);
                writeMemos(result, outputConfig, memoGen);
                printResultLine(result);
                success++;
            } catch (ValuationException e) {
                System.err.println("       [ERROR] " + e.getMessage());
                failure++;
            } catch (IOException e) {
                System.err.println("       [ERROR] Could not write memo: " + e.getMessage());
                failure++;
            }
        }

        System.out.println();
        System.out.printf("  Done. %d succeeded, %d failed.%n%n", success, failure);
    }

    // Interactive mode

    private static void runInteractiveMode(OutputConfig outputConfig,
                                           ValuationService service,
                                           MemoGenerator memoGen) {
        Scanner scanner = new Scanner(System.in);
        NumberFormat numFmt = numberFormat();

        while (true) {
            try {
                String caseRef = prompt(scanner,
                        "Case Reference (e.g., Estate of J. Smith -- Case No. 2024-PRB-001): ");
                if (caseRef.isBlank()) {
                    System.err.println("  [!] Case reference cannot be empty.\n");
                    continue;
                }

                String assetDesc = prompt(scanner,
                        "Asset Description (e.g., Barclays Bank Account, London): ");
                if (assetDesc.isBlank()) {
                    System.err.println("  [!] Asset description cannot be empty.\n");
                    continue;
                }

                String rawDate = prompt(scanner,
                        "Date of Death / Valuation Date (YYYY-MM-DD): ");
                LocalDate valuationDate;
                try {
                    valuationDate = LocalDate.parse(rawDate.trim());
                } catch (DateTimeParseException e) {
                    System.err.println("  [!] Invalid date. Use YYYY-MM-DD (e.g., 2023-11-14).\n");
                    continue;
                }
                if (valuationDate.isAfter(LocalDate.now())) {
                    System.err.println("  [!] Valuation date cannot be in the future.\n");
                    continue;
                }
                if (valuationDate.isBefore(LocalDate.of(1999, 1, 4))) {
                    System.err.println(" [!} ECB rate history begins on 1999-01-04.\n");
                    continue;
                }

                String currency = prompt(scanner,
                        "Source Currency Code (ISO 4217, e.g., GBP, EUR, JPY): ");
                if (currency.isBlank() || currency.trim().length() != 3) {
                    System.err.println("  [!] Currency code must be 3 letters.\n");
                    continue;
                }

                String rawAmount = prompt(scanner,
                        "Asset Amount in " + currency.toUpperCase() + ": ");
                double amount;
                try {
                    amount = Double.parseDouble(rawAmount.trim().replace(",", ""));
                } catch (NumberFormatException e) {
                    System.err.println("  [!] Invalid amount. Enter a number (e.g., 125000).\n");
                    continue;
                }
                if (amount <= 0) {
                    System.err.println("  [!] Amount must be a positive number.\n");
                    continue;
                }

                System.out.println("\n Fetching exchange rate from Frankfurter API...");

                ValuationRequest request = new ValuationRequest(valuationDate, currency, amount, caseRef, assetDesc);
                ValuationResult result = service.valuate(request);

                System.out.println();
                System.out.println("  +-----------------------------------------------------+");
                System.out.printf ("  | %-51s |%n", "VALUATION RESULT");
                System.out.println("  +-----------------------------------------------------+");
                System.out.printf ("  | Source Amount : %-35s |%n", currency.toUpperCase() + " " + numFmt.format(amount));
                System.out.printf ("  | Rate Date     : %-35s |%n", result.getRateDate());
                System.out.printf ("  | Rate Used     : 1 %s = %-27s |%n", result.getRequest().getSourceCurrency(), String.format("%.6f USD", result.getRateUsed()));
                System.out.printf ("  | USD VALUE     : %-35s |%n", numFmt.format(result.getUsdValue()));
                if (result.isDateFallbackApplied()) {
                    System.out.printf("  |  *** Adjusted from %s (non-business day) %s |%n", result.getRequest().getValuationDate(), " ".repeat(9) + "***");
                }
                System.out.println("  +-----------------------------------------------------+");
                System.out.println();

                writeMemos(result, outputConfig, memoGen);
            } catch (ValuationException e) {
                System.err.println("\n  [ERROR] " + e.getMessage() + "\n");
            } catch (IOException e) {
                System.err.println("\n  [ERROR] Could not write memo: " + e.getMessage() + "\n");
            }

            String again = prompt(scanner, "Valuate another asset? (y/n): ");
            if (!again.trim().equalsIgnoreCase("y")) {
                System.out.println("\n  Session ended.\n");
                break;
            }
            System.out.println();
        }
        scanner.close();
    }

    // Shared helpers

    /**
     * Writes memo in all configured formats and prints the saved paths.
     */
    private static void writeMemos(ValuationResult result, OutputConfig outputConfig,
                                   MemoGenerator memoGen) throws IOException {
        ValuationRequest req = result.getRequest();
        String baseName = buildBaseName(req);
        Path dir = outputConfig.getOutputDir();

        if (outputConfig.getFormat().writesTxt()) {
            Path txtPath = dir.resolve(baseName + ".txt");
            memoGen.generate(result, txtPath);
            System.out.println("  Memo (TXT) saved: " + txtPath.toAbsolutePath());
        }

        if (outputConfig.getFormat().writesPdf()) {
            Path pdfPath = dir.resolve(baseName + ".pdf");
            memoGen.generatePdf(result, pdfPath);
            System.out.println("  Memo (PDF) saved: " + pdfPath.toAbsolutePath());
        }
    }

    private static void printResultLine(ValuationResult result) {
        String flag = result.isDateFallbackApplied() ? " [rate date adjusted to " + result.getRateDate() + "]" : "";
        System.out.printf("       USD $%s%s%n", numberFormat().format(result.getUsdValue()), flag);
    }

    private static String buildBaseName(ValuationRequest req) {
        String safeCaseRef = req.getCaseReference()
                .replaceAll("[^A-Za-z0-9\\s\\-]", "")
                .trim()
                .replaceAll("\\s+", "_");
        if (safeCaseRef.length() > 40) safeCaseRef = safeCaseRef.substring(0, 40);
        return String.format("Valuation_Memo_%s_%s_%s", safeCaseRef, req.getValuationDate(), req.getSourceCurrency().toUpperCase());
    }

    private static String resolveOutputDir(OutputConfig outputConfig) {
        String d = outputConfig.getOutputDir().toString();
        return d.isBlank() ? "(current directory)" : outputConfig.getOutputDir().toAbsolutePath().toString();
    }

    private static String formatAmount(double amount) {
        return numberFormat().format(amount);
    }

    private static NumberFormat numberFormat() {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f;
    }

    private static String prompt(Scanner scanner, String message) {
        System.out.print("  > " + message);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }
}
