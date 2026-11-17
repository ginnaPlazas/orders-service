package co.andesexpress.orders.domain.exception;

/**
 * Otra petición modificó el mismo pedido al mismo tiempo y su escritura ganó.
 * Quien la recibe debe releer el pedido guardado en lugar de sobrescribirlo.
 */
public class ConcurrentOrderUpdateException extends RuntimeException {

    private final String orderId;

    public ConcurrentOrderUpdateException(String orderId, Throwable cause) {
        super("El pedido " + orderId + " fue modificado por una petición concurrente", cause);
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
