package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.model.OutboxEvent;
import co.andesexpress.orders.domain.port.in.RelayOutboxEventsInputPort;
import co.andesexpress.orders.domain.port.out.EventPublisherPort;
import co.andesexpress.orders.domain.port.out.OutboxRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

/**
 * Relay del outbox: publica los eventos pendientes en orden de creación y solo los marca
 * publicados cuando el broker los aceptó. Si falla, el evento queda pendiente (intento + error)
 * y se reintenta en la siguiente ejecución. Entrega al menos una vez: los consumidores toleran duplicados.
 */
public class RelayOutboxEventsUseCase implements RelayOutboxEventsInputPort {

    private static final Logger log = LoggerFactory.getLogger(RelayOutboxEventsUseCase.class);

    private final OutboxRepositoryPort outbox;
    private final EventPublisherPort publisher;
    private final Clock clock;
    private final int batchSize;

    public RelayOutboxEventsUseCase(OutboxRepositoryPort outbox, EventPublisherPort publisher, Clock clock, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("El tamaño de lote del relay debe ser mayor que cero");
        }
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Override
    public int relayPendingEvents() {
        int published = 0;
        // Pedidos con un evento fallido en esta ejecución: sus eventos posteriores esperan para no invertir el orden.
        Set<String> blockedOrders = new HashSet<>();

        for (OutboxEvent event : outbox.findPending(batchSize)) {
            if (blockedOrders.contains(event.getOrderId())) {
                continue;
            }
            try {
                publisher.publish(event);
            } catch (RuntimeException e) {
                event.registerFailure(e.getMessage());
                outbox.save(event);
                blockedOrders.add(event.getOrderId());
                log.warn("No se pudo publicar el evento {} ({}) del pedido {}, intento {}: {}",
                        event.getId(), event.getEventType().wireName(), event.getOrderId(), event.getAttempts(),
                        e.getMessage());
                continue;
            }
            event.markPublished(clock.instant());
            outbox.save(event);
            published++;
            log.info("Evento {} publicado para orderId={}", event.getEventType().wireName(), event.getOrderId());
        }
        return published;
    }
}
