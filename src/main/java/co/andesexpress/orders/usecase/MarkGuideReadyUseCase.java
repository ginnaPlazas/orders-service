package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.port.in.MarkGuideReadyInputPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

public class MarkGuideReadyUseCase implements MarkGuideReadyInputPort {

    private final OrderRepositoryPort orderRepository;

    public MarkGuideReadyUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Order markGuideReady(String orderId, String guideUrl) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.markGuideReady(guideUrl); // valida que esté en GUIDE_GENERATING
        return orderRepository.save(order);
    }
}