package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.port.in.MarkGuideReadyInputPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkGuideReadyUseCase implements MarkGuideReadyInputPort {

    private static final Logger log = LoggerFactory.getLogger(MarkGuideReadyUseCase.class);

    private final OrderRepositoryPort orderRepository;

    public MarkGuideReadyUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Order markGuideReady(String orderId, String guideUrl) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Idempotencia: la cola entrega "al menos una vez", así que la Lambda puede llamar dos veces.
        // La segunda llamada no es un error: se devuelve el pedido tal como quedó (conserva la primera URL).
        if (order.getStatus() == OrderStatus.GUIDE_READY) {
            log.info("Pedido {} ya tenía la guía lista, se ignora la llamada repetida", order.getId());
            return order;
        }

        order.markGuideReady(guideUrl); // valida que esté en GUIDE_GENERATING
        return orderRepository.save(order);
    }
}
