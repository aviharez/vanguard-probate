package com.project.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Holds resolved output settings derived from command-line arguments.
 */
public class OutputConfig {

    public enum Format {
        TXT("txt"),
        PDF("pdf"),
        BOTH("both");

        private final String flag;

        Format(String flag) {
            this.flag = flag;
        }

        public static Format fromFlag(String value) {
            for (Format f : values()) {
                if (f.flag.equalsIgnoreCase(value)) return f;
            }
            throw new IllegalArgumentException("Unknown format '" + value + "'. Use: txt, pdf, or both.");
        }

        public boolean writesTxt() {
            return this == TXT || this == BOTH;
        }

        public boolean writesPdf() {
            return this == PDF || this == BOTH;
        }
    }

    private final Path outputDir;
    private final Format format;

    public OutputConfig(Path outputDir, Format format) {
        this.outputDir = outputDir;
        this.format = format;
    }

    /** Default: current working directory, TXT only. */
    public static OutputConfig defaults() {
        return new OutputConfig(Paths.get(""), Format.TXT);
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public Format getFormat() {
        return format;
    }

}
