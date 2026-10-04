package co.andesexpress.orders.domain.exception;

public class DestinationRejectedException extends RuntimeException {
    public DestinationRejectedException(String message) {
        super(message);
    }
}