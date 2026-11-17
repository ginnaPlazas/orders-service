package co.andesexpress.orders.drivenadapters.outbox;

import co.andesexpress.orders.domain.model.Address;
import co.andesexpress.orders.domain.model.EventType;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.OutboxEvent;
import co.andesexpress.orders.domain.model.Product;
import co.andesexpress.orders.domain.port.in.ConfirmOrderInputPort;
import co.andesexpress.orders.domain.port.in.RelayOutboxEventsInputPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import co.andesexpress.orders.domain.port.out.OutboxRepositoryPort;
import co.andesexpress.orders.domain.port.out.TransactionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox real sobre H2, sin AWS: el relay programado está apagado y el tópico SNS sin configurar,
 * así que publicar falla de forma controlada y el evento debe quedar pendiente en la base.
 */
@SpringBootTest(properties = {"orders.outbox.relay.enabled=false", "aws.sns.order-events-topic-arn="})
class OutboxPersistenceIntegrationTest {

    @Autowired OrderRepositoryPort orderRepository;
    @Autowired OutboxRepositoryPort outbox;
    @Autowired TransactionPort transaction;
    @Autowired ConfirmOrderInputPort confirmOrder;
    @Autowired RelayOutboxEventsInputPort relay;

    Order newOrder(OrderStatus status) {
        String id = UUID.randomUUID().toString();
        return Order.reconstruct(id, "key-" + id, "cliente@andesexpress.com", new Address(5, 1), new Address(15, 2),
                List.of(new Product("Caja", new BigDecimal("2.5"))), status, null, null, null);
    }

    List<OutboxEvent> pendingFor(String orderId) {
        return outbox.findPending(1000).stream().filter(e -> e.getOrderId().equals(orderId)).toList();
    }

    @Test
    void confirmar_persisteElEstadoYElEventoYSnsCaidoLoDejaPendienteConError() {
        Order order = orderRepository.save(newOrder(OrderStatus.VALIDATED));

        Order confirmed = confirmOrder.confirmOrder(order.getId());

        assertEquals(OrderStatus.GUIDE_GENERATING, confirmed.getStatus());
        // Lectura dentro de una transacción: los productos del pedido se cargan de forma perezosa.
        assertEquals(OrderStatus.GUIDE_GENERATING, transaction.inTransaction(
                () -> orderRepository.findById(order.getId()).orElseThrow().getStatus()));
        List<OutboxEvent> pending = pendingFor(order.getId());
        assertEquals(1, pending.size());
        assertEquals(EventType.ORDER_CONFIRMED, pending.get(0).getEventType());
        assertEquals("cliente@andesexpress.com", pending.get(0).getCustomerEmail());
        assertEquals(0, pending.get(0).getAttempts());

        relay.relayPendingEvents();

        OutboxEvent afterRelay = pendingFor(order.getId()).get(0);
        assertTrue(afterRelay.isPending());
        assertEquals(1, afterRelay.getAttempts());
        assertTrue(afterRelay.getLastError().contains("ORDER_EVENTS_TOPIC_ARN"), afterRelay.getLastError());
    }

    @Test
    void siLaTransaccionFalla_niElPedidoNiElEventoQuedanGuardados() {
        Order order = newOrder(OrderStatus.VALIDATED);

        assertThrows(IllegalStateException.class, () -> transaction.inTransaction(() -> {
            orderRepository.save(order);
            outbox.save(OutboxEvent.pending(EventType.ORDER_CONFIRMED, order, java.time.Instant.now()));
            throw new IllegalStateException("falla simulada antes del commit");
        }));

        assertTrue(orderRepository.findById(order.getId()).isEmpty());
        assertTrue(pendingFor(order.getId()).isEmpty());
    }

    @Test
    void eventoMarcadoPublicado_yaNoApareceComoPendiente() {
        Order order = orderRepository.save(newOrder(OrderStatus.VALIDATED));
        OutboxEvent event = outbox.save(OutboxEvent.pending(EventType.GUIDE_READY, order, java.time.Instant.now()));

        event.markPublished(java.time.Instant.now());
        outbox.save(event);

        assertTrue(pendingFor(order.getId()).isEmpty());
    }
}
