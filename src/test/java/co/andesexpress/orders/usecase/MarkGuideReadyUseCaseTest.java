package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.EventType;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkGuideReadyUseCaseTest {

    static final Instant NOW = Instant.parse("2026-11-17T11:00:00Z");
    static final String KEY = "guias/order-1.pdf";

    FakeTransactionPort transaction;
    FakeOrderRepository repository;
    FakeOutboxRepository outbox;
    MarkGuideReadyUseCase useCase;

    @BeforeEach
    void setUp() {
        transaction = new FakeTransactionPort();
        repository = new FakeOrderRepository(transaction);
        outbox = new FakeOutboxRepository(transaction);
        useCase = new MarkGuideReadyUseCase(repository, outbox, transaction, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void pedidoGenerandoGuia_guardaLaClaveYUnEventoGuiaListaEnLaMismaTransaccion() {
        repository.save(TestData.orderIn(OrderStatus.GUIDE_GENERATING));
        repository.writesInTransaction.clear();

        Order order = useCase.markGuideReady("order-1", KEY);

        assertEquals(OrderStatus.GUIDE_READY, order.getStatus());
        assertEquals(KEY, order.getGuideKey());
        Order stored = repository.findById("order-1").orElseThrow();
        assertEquals(OrderStatus.GUIDE_READY, stored.getStatus());
        assertEquals(KEY, stored.getGuideKey());

        List<OutboxEvent> events = outbox.all();
        assertEquals(1, events.size());
        OutboxEvent event = events.get(0);
        assertEquals(EventType.GUIDE_READY, event.getEventType());
        assertEquals("GuiaLista", event.getEventType().wireName());
        assertEquals("order-1", event.getOrderId());
        assertEquals(TestData.customerEmail(), event.getCustomerEmail());
        assertEquals(NOW, event.getCreatedAt());
        assertTrue(event.isPending());
        assertEquals(0, event.getAttempts());

        assertEquals(1, transaction.transactions);
        assertEquals(List.of(true), repository.writesInTransaction);
        assertEquals(List.of(true), outbox.writesInTransaction);
    }

    @Test
    void claveVacia_lanzaIllegalArgumentYNoCambiaNada() {
        repository.save(TestData.orderIn(OrderStatus.GUIDE_GENERATING));

        assertThrows(IllegalArgumentException.class, () -> useCase.markGuideReady("order-1", " "));
        assertEquals(OrderStatus.GUIDE_GENERATING, repository.findById("order-1").orElseThrow().getStatus());
        assertTrue(outbox.all().isEmpty());
    }

    @Test
    void pedidoSoloConfirmado_lanzaExcepcionDeEstadoYNoGuardaEvento() {
        repository.save(TestData.orderIn(OrderStatus.CONFIRMED));

        assertThrows(IllegalStateException.class, () -> useCase.markGuideReady("order-1", KEY));
        assertTrue(outbox.all().isEmpty());
    }

    @Test
    void pedidoInexistente_lanzaOrderNotFound() {
        assertThrows(OrderNotFoundException.class, () -> useCase.markGuideReady("no-existe", KEY));
    }
}
