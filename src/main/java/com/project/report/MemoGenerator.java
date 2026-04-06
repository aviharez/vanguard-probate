package com.project.report;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MemoGenerator {

    private static final int LINE_WIDTH = 80;
    private static final String BORDER = "=".repeat(LINE_WIDTH);
    private static final String DIVIDER = "-".repeat(LINE_WIDTH);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

}
