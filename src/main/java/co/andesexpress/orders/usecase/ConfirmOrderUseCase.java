package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.EventType;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OutboxEvent;
import co.andesexpress.orders.domain.port.in.ConfirmOrderInputPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import co.andesexpress.orders.domain.port.out.OutboxRepositoryPort;
import co.andesexpress.orders.domain.port.out.TransactionPort;

import java.time.Clock;

public class ConfirmOrderUseCase implements ConfirmOrderInputPort {

    private final OrderRepositoryPort orderRepository;
    private final OutboxRepositoryPort outbox;
    private final TransactionPort transaction;
    private final Clock clock;

    public ConfirmOrderUseCase(OrderRepositoryPort orderRepository, OutboxRepositoryPort outbox,
                               TransactionPort transaction, Clock clock) {
        this.orderRepository = orderRepository;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public Order confirmOrder(String orderId) {
        return transaction.inTransaction(() -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            order.confirm(); // valida internamente que esté en VALIDATED
            order.markGuideGenerating();
            Order saved = orderRepository.save(order);

            // HU-08: el evento PedidoConfirmado (Lambda de guía + Notifications) se guarda en el outbox
            // en la misma transacción; el relay lo publica en SNS después (al menos una vez).
            outbox.save(OutboxEvent.pending(EventType.ORDER_CONFIRMED, saved, clock.instant()));
            return saved;
        });
    }
}
