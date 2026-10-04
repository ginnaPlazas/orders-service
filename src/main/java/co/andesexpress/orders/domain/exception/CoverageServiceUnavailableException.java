package co.andesexpress.orders.domain.exception;

public class CoverageServiceUnavailableException extends RuntimeException {
    public CoverageServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}