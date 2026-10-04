package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class FakeOrderRepository implements OrderRepositoryPort {

    final Map<String, Order> store = new HashMap<>();

    @Override
    public Order save(Order order) {
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return store.values().stream()
                .filter(o -> o.getIdempotencyKey().equals(idempotencyKey))
                .findFirst();
    }
}