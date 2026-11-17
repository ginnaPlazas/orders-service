package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class FakeOrderRepository implements OrderRepositoryPort {

    final Map<String, Order> store = new HashMap<>();
    /** Por cada escritura, si ocurrió dentro de una transacción. */
    final List<Boolean> writesInTransaction = new ArrayList<>();
    private final FakeTransactionPort transaction;

    FakeOrderRepository() {
        this(null);
    }

    FakeOrderRepository(FakeTransactionPort transaction) {
        this.transaction = transaction;
    }

    @Override
    public Order save(Order order) {
        writesInTransaction.add(transaction != null && transaction.active);
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