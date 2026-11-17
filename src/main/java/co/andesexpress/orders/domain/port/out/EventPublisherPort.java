package co.andesexpress.orders.domain.port.out;

import co.andesexpress.orders.domain.model.OutboxEvent;

public interface EventPublisherPort {
    /** Publica el evento; lanza una excepción si el broker no lo aceptó. */
    void publish(OutboxEvent event);
}
