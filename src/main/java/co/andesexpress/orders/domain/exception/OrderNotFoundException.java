package co.andesexpress.orders.domain.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("El pedido con id " + orderId + " no existe");
    }
}