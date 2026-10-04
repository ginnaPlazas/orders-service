package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.port.in.GetOrderStatusInputPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

public class GetOrderStatusUseCase implements GetOrderStatusInputPort {

    private final OrderRepositoryPort orderRepository;

    public GetOrderStatusUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Order getOrderStatus(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}