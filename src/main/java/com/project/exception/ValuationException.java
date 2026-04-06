package com.project.exception;

/**
 * Thrown when a valuation cannot be completed e.g., API unreachable,
 * unsupported currency, or no historical rate found within the lookback window.
 */
public class ValuationException extends RuntimeException {

    public ValuationException(String message) {
        super(message);
    }

    public ValuationException(String message, Throwable cause) {
        super(message, cause);
    }

}
