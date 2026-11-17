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

class ConfirmOrderUseCaseTest {

    static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    FakeTransactionPort transaction;
    FakeOrderRepository repository;
    FakeOutboxRepository outbox;
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    ConfirmOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        transaction = new FakeTransactionPort();
        repository = new FakeOrderRepository(transaction);
        outbox = new FakeOutboxRepository(transaction);
        useCase = new ConfirmOrderUseCase(repository, outbox, transaction, clock);
    }

    @Test
    void pedidoValidado_quedaGenerandoGuiaYGuardaPedidoConfirmadoEnLaMismaTransaccion() {
        repository.save(TestData.orderIn(OrderStatus.VALIDATED));
        repository.writesInTransaction.clear();

        Order order = useCase.confirmOrder("order-1");

        assertEquals(OrderStatus.GUIDE_GENERATING, order.getStatus());
        assertEquals(OrderStatus.GUIDE_GENERATING, repository.findById("order-1").orElseThrow().getStatus());

        List<OutboxEvent> events = outbox.all();
        assertEquals(1, events.size());
        OutboxEvent event = events.get(0);
        assertEquals(EventType.ORDER_CONFIRMED, event.getEventType());
        assertEquals("PedidoConfirmado", event.getEventType().wireName());
        assertEquals("order-1", event.getOrderId());
        assertEquals(TestData.customerEmail(), event.getCustomerEmail());
        assertEquals(NOW, event.getCreatedAt());
        assertTrue(event.isPending());
        assertEquals(0, event.getAttempts());
        assertNull(event.getLastError());

        // Estado y evento se escriben en una única transacción.
        assertEquals(1, transaction.transactions);
        assertEquals(List.of(true), repository.writesInTransaction);
        assertEquals(List.of(true), outbox.writesInTransaction);
    }

    @Test
    void confirmar_devuelveElPedidoAunqueElPublicadorEsteCaido() {
        repository.save(TestData.orderIn(OrderStatus.VALIDATED));
        FakeEventPublisher publisher = new FakeEventPublisher();
        publisher.failure = new RuntimeException("SNS no disponible");
        RelayOutboxEventsUseCase relay = new RelayOutboxEventsUseCase(outbox, publisher, clock, 50);

        Order order = useCase.confirmOrder("order-1");
        int published = relay.relayPendingEvents();

        assertEquals(OrderStatus.GUIDE_GENERATING, order.getStatus());
        assertEquals(0, published);
        OutboxEvent event = outbox.all().get(0);
        assertTrue(event.isPending());
        assertEquals(1, event.getAttempts());
        assertEquals("SNS no disponible", event.getLastError());
    }

    @Test
    void pedidoSinValidar_lanzaIllegalStateYNoGuardaEvento() {
        repository.save(TestData.orderIn(OrderStatus.PENDING_VALIDATION));

        assertThrows(IllegalStateException.class, () -> useCase.confirmOrder("order-1"));
        assertTrue(outbox.all().isEmpty());
        assertEquals(OrderStatus.PENDING_VALIDATION, repository.findById("order-1").orElseThrow().getStatus());
    }

    @Test
    void pedidoInexistente_lanzaOrderNotFound() {
        assertThrows(OrderNotFoundException.class, () -> useCase.confirmOrder("no-existe"));
        assertTrue(outbox.all().isEmpty());
    }
}
