package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.port.in.ConfirmOrderInputPort;
import co.andesexpress.orders.domain.port.out.EventPublisherPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

public class ConfirmOrderUseCase implements ConfirmOrderInputPort {

    private final OrderRepositoryPort orderRepository;
    private final EventPublisherPort eventPublisher;

    public ConfirmOrderUseCase(OrderRepositoryPort orderRepository, EventPublisherPort eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Order confirmOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.confirm(); // valida internamente que esté en VALIDATED
        order = orderRepository.save(order);

        // HU-08: dispara el evento que arranca la Lambda (generación de guía) y Notifications
        eventPublisher.publishOrderConfirmed(order.getId());

        return order;
    }
}