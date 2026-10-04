package co.andesexpress.orders.domain.port.out;

import co.andesexpress.orders.domain.model.Order;
import java.util.Optional;

public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(String orderId);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}