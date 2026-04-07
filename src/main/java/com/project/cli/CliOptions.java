package com.project.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Parses and holds resolved command-line arguments.
 *
 * <pre>
 * Usage:
 *      java -jar tool.jar
 *      java -jar tool.jar --input assets.csv           Interactive mode
 *      java -jar tool.jar --input assets.csv \         Batch mode
 *                         --output-dir ./memos \
 *                         --format pdf                 Batch, PDF in ./memos/
 *      java -jar tool.jar --help
 * </pre>
 */
public class CliOptions {

    private final Path inputFile;
    private final OutputConfig outputConfig;
    private final boolean helpRequested;

    private CliOptions(Path inputFile, OutputConfig outputConfig, boolean helpRequested) {
        this.inputFile = inputFile;
        this.outputConfig = outputConfig;
        this.helpRequested = helpRequested;
    }

    /** Parses {@code args} and returns the resolved options. */
    public static CliOptions parse(String[] args) {
        Path inputFile = null;
        Path outputDir = Paths.get("");
        OutputConfig.Format format = OutputConfig.Format.TXT;
        boolean helpRequested = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> helpRequested = true;
                case "--input", "-i" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--input requires a file path.");
                    inputFile = Paths.get(args[++i]);
                }
                case "--output-dir", "-o" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--output-dir requires a directory path.");
                    outputDir = Paths.get(args[++i]);
                }
                case "--format", "-f" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--format requires a value (txt, pdf, both).");
                    format = OutputConfig.Format.fromFlag(args[++i]);
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        return new CliOptions(inputFile, new OutputConfig(outputDir, format), helpRequested);
    }

    public static String helpText() {
        return """
                Usage:
                    java -jar vanguard-probate-1.0.0.jar [OPTIONS]
                    
                Options:
                    --input FILE        CSV file for batch mode (omit for interactive mode)
                    --output-dir DIR    Directory where memos are saved (default: current dir)
                    --format FORMAT     Output format: txt, pdf, both (default: txt)
                    --help              Show this message
                    
                CSV Format (first row must be the header):
                    case_reference,asset_description,valuation_date,currency,amount
                    
                Example:
                    java -jar vanguard-probate-1.0.0.jar --input assets.csv --output-dir ./memos --format both 
                """;
    }

    public boolean isBatchMode() {
        return inputFile != null;
    }

    public Path getInputFile() {
        return inputFile;
    }

    public OutputConfig getOutputConfig() {
        return outputConfig;
    }

    public boolean isHelpRequested() {
        return helpRequested;
    }

}
