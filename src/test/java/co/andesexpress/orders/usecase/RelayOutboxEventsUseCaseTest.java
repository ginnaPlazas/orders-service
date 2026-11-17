package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.model.EventType;
import co.andesexpress.orders.domain.model.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RelayOutboxEventsUseCaseTest {

    static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    FakeOutboxRepository outbox;
    FakeEventPublisher publisher;
    RelayOutboxEventsUseCase relay;

    @BeforeEach
    void setUp() {
        outbox = new FakeOutboxRepository();
        publisher = new FakeEventPublisher();
        relay = new RelayOutboxEventsUseCase(outbox, publisher, Clock.fixed(NOW, ZoneOffset.UTC), 50);
    }

    static OutboxEvent pending(String id, EventType type, String orderId, String createdAt) {
        return OutboxEvent.reconstruct(id, type, orderId, "cliente@andesexpress.com",
                Instant.parse(createdAt), null, 0, null);
    }

    static List<String> ids(List<OutboxEvent> events) {
        return events.stream().map(OutboxEvent::getId).toList();
    }

    @Test
    void publicaLosPendientesEnOrdenDeCreacionYLosMarcaPublicados() {
        // Se insertan desordenados; el relay debe respetar la fecha de creación.
        outbox.save(pending("e3", EventType.GUIDE_READY, "order-1", "2026-11-17T09:00:03Z"));
        outbox.save(pending("e1", EventType.ORDER_CONFIRMED, "order-1", "2026-11-17T09:00:01Z"));
        outbox.save(pending("e2", EventType.ORDER_CONFIRMED, "order-2", "2026-11-17T09:00:02Z"));
        outbox.save(OutboxEvent.reconstruct("e0", EventType.ORDER_CONFIRMED, "order-0", "c@a.com",
                Instant.parse("2026-11-17T09:00:00Z"), Instant.parse("2026-11-17T09:00:05Z"), 1, null));

        int published = relay.relayPendingEvents();

        assertEquals(3, published);
        assertEquals(List.of("e1", "e2", "e3"), ids(publisher.published));
        for (String id : List.of("e1", "e2", "e3")) {
            OutboxEvent event = outbox.store.get(id);
            assertFalse(event.isPending(), id);
            assertEquals(NOW, event.getPublishedAt(), id);
            assertEquals(1, event.getAttempts(), id);
            assertNull(event.getLastError(), id);
        }
        // El ya publicado no se vuelve a publicar ni se modifica.
        assertEquals(Instant.parse("2026-11-17T09:00:05Z"), outbox.store.get("e0").getPublishedAt());
        assertEquals(1, outbox.store.get("e0").getAttempts());
        assertEquals(0, relay.relayPendingEvents());
        assertEquals(3, publisher.published.size());
    }

    @Test
    void siElPublicadorFalla_elEventoSiguePendienteConIntentoYError() {
        outbox.save(pending("e1", EventType.ORDER_CONFIRMED, "order-1", "2026-11-17T09:00:01Z"));
        publisher.failure = new RuntimeException("Unable to execute HTTP request: sns.us-east-1.amazonaws.com");

        assertEquals(0, relay.relayPendingEvents());
        OutboxEvent event = outbox.store.get("e1");
        assertTrue(event.isPending());
        assertNull(event.getPublishedAt());
        assertEquals(1, event.getAttempts());
        assertEquals("Unable to execute HTTP request: sns.us-east-1.amazonaws.com", event.getLastError());

        assertEquals(0, relay.relayPendingEvents());
        assertEquals(2, outbox.store.get("e1").getAttempts());

        // Cuando SNS vuelve, el evento se publica en la siguiente ejecución.
        publisher.failure = null;
        assertEquals(1, relay.relayPendingEvents());
        OutboxEvent recovered = outbox.store.get("e1");
        assertFalse(recovered.isPending());
        assertEquals(NOW, recovered.getPublishedAt());
        assertEquals(3, recovered.getAttempts());
        assertNull(recovered.getLastError());
    }

    @Test
    void unaFallaDeUnPedidoNoBloqueaAOtrosNiDesordenaLosEventosDelMismoPedido() {
        outbox.save(pending("e1", EventType.ORDER_CONFIRMED, "order-1", "2026-11-17T09:00:01Z"));
        outbox.save(pending("e2", EventType.ORDER_CONFIRMED, "order-2", "2026-11-17T09:00:02Z"));
        outbox.save(pending("e3", EventType.GUIDE_READY, "order-1", "2026-11-17T09:00:03Z"));
        publisher.failForOrderId = "order-1";

        assertEquals(1, relay.relayPendingEvents());

        assertEquals(List.of("e2"), ids(publisher.published));
        assertEquals(1, outbox.store.get("e1").getAttempts());
        assertTrue(outbox.store.get("e1").isPending());
        // e3 no se intenta: publicarlo antes que e1 invertiría el orden de eventos del pedido.
        assertEquals(0, outbox.store.get("e3").getAttempts());
        assertTrue(outbox.store.get("e3").isPending());
    }

    @Test
    void respetaElTamanoDeLote() {
        relay = new RelayOutboxEventsUseCase(outbox, publisher, Clock.fixed(NOW, ZoneOffset.UTC), 2);
        outbox.save(pending("e1", EventType.ORDER_CONFIRMED, "order-1", "2026-11-17T09:00:01Z"));
        outbox.save(pending("e2", EventType.ORDER_CONFIRMED, "order-2", "2026-11-17T09:00:02Z"));
        outbox.save(pending("e3", EventType.ORDER_CONFIRMED, "order-3", "2026-11-17T09:00:03Z"));

        assertEquals(2, relay.relayPendingEvents());
        assertEquals(1, relay.relayPendingEvents());
        assertEquals(List.of("e1", "e2", "e3"), ids(publisher.published));
    }
}
