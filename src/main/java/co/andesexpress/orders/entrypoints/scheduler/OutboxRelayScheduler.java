package co.andesexpress.orders.entrypoints.scheduler;

import co.andesexpress.orders.domain.port.in.RelayOutboxEventsInputPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada programado que dispara el relay del outbox. Nunca deja escapar excepciones:
 * si AWS o la base de datos no responden se registra el error y se reintenta en la siguiente ejecución.
 * Se desactiva con {@code orders.outbox.relay.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "orders.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final RelayOutboxEventsInputPort relayOutboxEvents;

    public OutboxRelayScheduler(RelayOutboxEventsInputPort relayOutboxEvents) {
        this.relayOutboxEvents = relayOutboxEvents;
    }

    @Scheduled(initialDelayString = "${orders.outbox.relay.initial-delay-ms:5000}",
               fixedDelayString = "${orders.outbox.relay.interval-ms:5000}")
    public void relay() {
        try {
            int published = relayOutboxEvents.relayPendingEvents();
            if (published > 0) {
                log.info("Relay del outbox: {} evento(s) publicados", published);
            }
        } catch (RuntimeException e) {
            log.error("Relay del outbox falló, se reintentará en la siguiente ejecución: {}", e.getMessage(), e);
        }
    }
}
