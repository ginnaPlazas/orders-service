package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.EventType;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OutboxEvent;
import co.andesexpress.orders.domain.port.in.MarkGuideReadyInputPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import co.andesexpress.orders.domain.port.out.OutboxRepositoryPort;
import co.andesexpress.orders.domain.port.out.TransactionPort;

import java.time.Clock;

public class MarkGuideReadyUseCase implements MarkGuideReadyInputPort {

    private final OrderRepositoryPort orderRepository;
    private final OutboxRepositoryPort outbox;
    private final TransactionPort transaction;
    private final Clock clock;

    public MarkGuideReadyUseCase(OrderRepositoryPort orderRepository, OutboxRepositoryPort outbox,
                                 TransactionPort transaction, Clock clock) {
        this.orderRepository = orderRepository;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public Order markGuideReady(String orderId, String guideKey) {
        return transaction.inTransaction(() -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            // Valida que esté en GUIDE_GENERATING y que la clave no esté vacía.
            // Idempotente: la misma clave sobre un pedido ya listo no guarda ni emite otro GuiaLista.
            if (!order.markGuideReady(guideKey)) {
                return order;
            }
            Order saved = orderRepository.save(order);

            // RF-5: notificar al cliente que la guía está lista (evento GuiaLista vía outbox).
            outbox.save(OutboxEvent.pending(EventType.GUIDE_READY, saved, clock.instant()));
            return saved;
        });
    }
}
