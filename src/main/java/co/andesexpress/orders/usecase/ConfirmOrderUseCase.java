package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.port.in.ConfirmOrderInputPort;
import co.andesexpress.orders.domain.port.out.EventPublisherPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfirmOrderUseCase implements ConfirmOrderInputPort {

    private static final Logger log = LoggerFactory.getLogger(ConfirmOrderUseCase.class);

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

        // Reintento seguro: el pedido ya fue confirmado y está generando la guía. Esto pasa si el
        // cliente confirma dos veces, o si la publicación del evento falló justo después de guardar
        // (el pedido quedaría atascado). Se vuelve a publicar el evento; los consumidores toleran
        // duplicados (Notifications por pedido+tipo, la guía se sobrescribe, markGuideReady es idempotente).
        if (order.getStatus() == OrderStatus.GUIDE_GENERATING) {
            log.info("Pedido {} ya estaba generando guía, se vuelve a publicar el evento", order.getId());
            eventPublisher.publishOrderConfirmed(order);
            return order;
        }

        order.confirm(); // valida internamente que esté en VALIDATED
        order.markGuideGenerating();
        order = orderRepository.save(order);

        // HU-08: dispara el evento que arranca la Lambda (generación de guía) y Notifications
        eventPublisher.publishOrderConfirmed(order);

        return order;
    }
}
