package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.DuplicateIdempotencyKeyException;
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

    /**
     * Simula una carrera: las próximas N búsquedas por idempotencyKey no ven los pedidos ya guardados,
     * como le pasa a la petición concurrente que consultó antes de que la otra hiciera commit.
     */
    int hiddenIdempotencyLookups = 0;

    @Override
    public Order save(Order order) {
        // Igual que la restricción UNIQUE de la base de datos sobre idempotencyKey
        boolean keyTakenByAnotherOrder = store.values().stream()
                .anyMatch(o -> o.getIdempotencyKey().equals(order.getIdempotencyKey())
                        && !o.getId().equals(order.getId()));
        if (keyTakenByAnotherOrder) {
            throw new DuplicateIdempotencyKeyException(order.getIdempotencyKey(), null);
        }
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
        if (hiddenIdempotencyLookups > 0) {
            hiddenIdempotencyLookups--;
            return Optional.empty();
        }
        return store.values().stream()
                .filter(o -> o.getIdempotencyKey().equals(idempotencyKey))
                .findFirst();
    }
}
