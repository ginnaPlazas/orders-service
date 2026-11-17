package co.andesexpress.orders.domain.model;

/** Eventos de dominio que Orders publica; {@code wireName} es el valor del atributo SNS {@code eventType}. */
public enum EventType {
    ORDER_CONFIRMED("PedidoConfirmado"),
    GUIDE_READY("GuiaLista");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
