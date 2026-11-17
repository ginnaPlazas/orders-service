package co.andesexpress.orders.domain.exception;

/**
 * Otro pedido ya ocupa esta clave de idempotencia (por ejemplo, una petición concurrente
 * hizo commit primero). Quien la recibe debe aplicar las reglas de reintento al pedido guardado.
 */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateIdempotencyKeyException(String idempotencyKey, Throwable cause) {
        super("Ya existe un pedido con la clave de idempotencia " + idempotencyKey, cause);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
