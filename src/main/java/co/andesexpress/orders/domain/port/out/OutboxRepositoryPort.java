package co.andesexpress.orders.domain.port.out;

import co.andesexpress.orders.domain.model.OutboxEvent;
import java.util.List;

public interface OutboxRepositoryPort {
    OutboxEvent save(OutboxEvent event);

    /** Eventos aún no publicados, en orden de creación (los más antiguos primero). */
    List<OutboxEvent> findPending(int limit);
}
