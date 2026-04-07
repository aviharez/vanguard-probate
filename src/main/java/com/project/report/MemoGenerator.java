package com.project.report;

import com.project.model.ValuationRequest;
import com.project.model.ValuationResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generates Valuation Memoranda in plain-text (.txt) and/or PDF formats.
 *
 * <p>Both formats share the same {@link #writeMemo} content logic; only
 * the output sink differs. The PDF version uses PDFBox with Courier to
 * preserve the 80-column fixed-width layout.</p>
 */
public class MemoGenerator {

    private static final int LINE_WIDTH = 80;
    private static final String BORDER = "=".repeat(LINE_WIDTH);
    private static final String DIVIDER = "-".repeat(LINE_WIDTH);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    // PDF layout constants
    private static final float PDF_FONT_SIZE = 9f;
    private static final float PDF_LEADING = 12f;
    private static final float PDF_MARGIN = 50f;

    private final NumberFormat currencyFmt;
    private final NumberFormat rateFmt;

    public MemoGenerator() {
        this.currencyFmt = NumberFormat.getNumberInstance(Locale.US);
        this.currencyFmt.setMinimumFractionDigits(2);
        this.currencyFmt.setMaximumFractionDigits(2);

        this.rateFmt = NumberFormat.getNumberInstance(Locale.US);
        this.rateFmt.setMinimumFractionDigits(6);
        this.rateFmt.setMaximumFractionDigits(6);
    }

    /**
     * Writes a plain-text memo to {@code outputPath}.
     *
     * @throws IOException if the file cannot be written
     */
    public void generate(ValuationResult result, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(outputPath.toFile(), StandardCharsets.UTF_8)) {
            writeMemo(result, pw);
        }
    }

    /**
     * Writes a PDF memo to {@code outputPath}.
     * The layout mirrors the text version, typeset in Courier at 9pt.
     *
     * @throws IOException if the file cannot be written or PDF creation fails.
     */
    public void generatePdf(ValuationResult result, Path outputPath) throws IOException {
        String content = memoAsString(result);
        String[] lines = content.split("\r?\n", -1);
        writePdf(lines, outputPath);
    }

    // Memo content (shared by txt and PDF paths)

    private String memoAsString(ValuationResult result) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            writeMemo(result, pw);
        }
        return sw.toString();
    }

    private void writeMemo(ValuationResult result, PrintWriter pw) {
        ValuationRequest req = result.getRequest();

        pw.println(BORDER);
        pw.println("VANGUARD CROSS-BORDER ESTATES");
        pw.println("ASSET VALUATION MEMORANDUM");
        pw.println(BORDER);
        pw.println();

        pw.println(field("CASE REFERENCE", req.getCaseReference()));
        pw.println(field("MEMO DATE", LocalDate.now().format(DATE_FMT)));
        pw.println(field("PREPARED BY", "Cross-Border Asset Valuation Tool"));
        pw.println();

        pw.println(DIVIDER);
        pw.println(field("ASSET DESCRIPTION", req.getAssetDescription()));
        pw.println(DIVIDER);
        pw.println();

        String srcSymbol = currencySymbol(req.getSourceCurrency());
        pw.println(field("SOURCE CURRENCY", req.getSourceCurrency() + "  (" + currencyName(req.getSourceCurrency()) + ")"));
        pw.println(field("ORIGINAL AMOUNT", srcSymbol + currencyFmt.format(req.getAmount())));
        pw.println();

        pw.println(field("VALUATION DATE (DATE OF DEATH)", req.getValuationDate().format(DATE_FMT) + "  [" + result.getRateDate().format(ISO_FMT) + "]"));
        pw.println(field("RATE DATE USED", result.getRateDate().format(DATE_FMT) + "  [" + result.getRateDate().format(ISO_FMT) + "]"));

        if (result.isDateFallbackApplied()) {
            pw.println();
            pw.println("  NOTE: The requested valuation date (" + req.getValuationDate() + ")");
            pw.println("        fell on a weekend or ECB non-publishing day. The nearest");
            pw.println("        preceding business day rate (" + result.getRateDate() + ") has");
            pw.println("        been applied in accordance with standard probate practice.");
        } else {
            pw.println(field("NOTE", "Rate date matches requested valuation date."));
        }
        pw.println();

        pw.println(field("EXCHANGE RATE", String.format("1 %s = %s USD", req.getSourceCurrency(), rateFmt.format(result.getRateUsed()))));
        pw.println(field("RATE SOURCE", "Frankfurter API (European Central Bank reference rates)"));
        pw.println();

        pw.println(DIVIDER);
        pw.println(field("USD EQUIVALENT VALUE", "$" + currencyFmt.format(result.getUsdValue())));
        pw.println(DIVIDER);
        pw.println();

        pw.println(BORDER);
        pw.println(center("CERTIFICATION"));
        pw.println(BORDER);
        pw.println();
        pw.println(wrap(
                "This valuation is based on the interbank foreign-exchange reference rates " +
                        "published by the European Central Bank (ECB) and sourced via the " +
                        "Frankfurter public API. The rate applied reflects the nearest ECB " +
                        "publishing day at or before the stated valuation date. This memorandum " +
                        "is generated solely for probate and legal proceedings purposes and does " +
                        "not constitute financial advice.", 2
        ));
        pw.println();
        pw.println(field("SIGNATURE LINE", "_".repeat(20)));
        pw.println(field("TITLE", "_".repeat(20)));
        pw.println(field("DATE", "_".repeat(20)));
        pw.println();
        pw.println(BORDER);
    }

    // PDF writter

    private void writePdf(String[] lines, Path outputPath) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.COURIER);

        float pageHeight = PDRectangle.LETTER.getHeight();
        float startY = pageHeight - PDF_MARGIN;
        float bottomBound = PDF_MARGIN;

        // delete existing file first so PDFBox doesn't emit an "overwriting" warning.
        Files.deleteIfExists(outputPath);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = addPage(doc);
            PDPageContentStream cs = openContentStream(doc, page, font, PDF_FONT_SIZE, PDF_MARGIN, startY);
            float yPos = startY;

            for (String rawLine : lines) {
                // Page break when we approach the bottom margin
                if (yPos - PDF_LEADING < bottomBound) {
                    cs.endText();
                    cs.close();
                    page = addPage(doc);
                    cs = openContentStream(doc, page, font, PDF_FONT_SIZE, PDF_MARGIN, startY);
                    yPos = startY;
                }

                String safeLine = toWinAnsiSafe(rawLine);
                cs.showText(safeLine);
                cs.newLineAtOffset(0, -PDF_LEADING);
                yPos -= PDF_LEADING;
            }

            cs.endText();
            cs.close();
            doc.save(outputPath.toFile());
        }
    }

    private PDPage addPage(PDDocument doc) {
        PDPage page = new PDPage(PDRectangle.LETTER);
        doc.addPage(page);
        return page;
    }

    private PDPageContentStream openContentStream(PDDocument doc, PDPage page,
                                                  PDType1Font font, float fontSize,
                                                  float x, float y) throws IOException {
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        return cs;
    }

    /**
     * Replaces characters outside the Windows-1252 range (used by PDFBox's
     * Type1/Courier font) with readable ASCII equivalents.
     */
    private static String toWinAnsiSafe(String text) {
        return text
                .replace("\u20AC", "EUR")
                .replace("\u20B9", "INR")
                .replace("\u20A9", "KRW")
                .replace("[^\u0000-\u00FF]", "?");
    }

    // Formatting helpers

    private static String center(String text) {
        int padding = Math.max(0, (LINE_WIDTH - text.length()) / 2);
        return " ".repeat(padding) + text;
    }

    private static String field(String label, String value) {
        return "  " + String.format("%-34s", label + ":") + value;
    }

    private static String wrap(String text, int indent) {
        int maxWidth = LINE_WIDTH - indent;
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        StringBuilder line = new StringBuilder();
        String prefix = " ".repeat(indent);

        for (String word : words) {
            if (line.length() + word.length() + (line.length() > 0 ? 1 : 0) > maxWidth) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(prefix).append(line);
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(prefix).append(line);
        }
        return sb.toString();
    }

    private static String currencySymbol(String code) {
        return switch (code) {
            case "GBP" -> "£";
            case "EUR" -> "€";
            case "JPY", "CNY" -> "¥";
            case "INR" -> "₹";
            case "KRW" -> "₩";
            case "CAD" -> "CA$";
            case "AUD" -> "A$";
            case "NZD" -> "NZ$";
            case "CHF" -> "CHF ";
            case "USD" -> "$";
            default -> code + " ";
        };
    }

    private static String currencyName(String code) {
        return switch (code) {
            case "GBP" -> "British Pound Sterling";
            case "EUR" -> "Euro";
            case "JPY" -> "Japanese Yen";
            case "CNY" -> "Chinese Yuan Renminbi";
            case "CHF" -> "Swiss Franc";
            case "CAD" -> "Canadian Dollar";
            case "AUD" -> "Australian Dollar";
            case "NZD" -> "New Zealand Dollar";
            case "HKD" -> "Hong Kong Dollar";
            case "SGD" -> "Singapore Dollar";
            case "SEK" -> "Swedish Krona";
            case "NOK" -> "Norwegian Krone";
            case "DKK" -> "Danish Krone";
            case "INR" -> "Indian Rupee";
            case "KRW" -> "South Korean Won";
            case "MXN" -> "Mexican Peso";
            case "BRL" -> "Brazilian Real";
            case "ZAR" -> "South African Rand";
            case "USD" -> "United States Dollar";
            default -> code;
        };
    }

}
