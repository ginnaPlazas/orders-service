package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.model.OutboxEvent;
import co.andesexpress.orders.domain.port.out.OutboxRepositoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class FakeOutboxRepository implements OutboxRepositoryPort {

    final Map<String, OutboxEvent> store = new LinkedHashMap<>();
    /** Por cada escritura, si ocurrió dentro de una transacción. */
    final List<Boolean> writesInTransaction = new ArrayList<>();
    private final FakeTransactionPort transaction;

    FakeOutboxRepository() {
        this(null);
    }

    FakeOutboxRepository(FakeTransactionPort transaction) {
        this.transaction = transaction;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        writesInTransaction.add(transaction != null && transaction.active);
        store.put(event.getId(), event);
        return event;
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return store.values().stream()
                .filter(OutboxEvent::isPending)
                .sorted(Comparator.comparing(OutboxEvent::getCreatedAt))
                .limit(limit)
                .toList();
    }

    List<OutboxEvent> all() {
        return List.copyOf(store.values());
    }
}
