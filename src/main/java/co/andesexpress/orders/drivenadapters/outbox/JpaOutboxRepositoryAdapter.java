package co.andesexpress.orders.drivenadapters.outbox;

import co.andesexpress.orders.domain.model.EventType;
import co.andesexpress.orders.domain.model.OutboxEvent;
import co.andesexpress.orders.domain.port.out.OutboxRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaOutboxRepositoryAdapter implements OutboxRepositoryPort {

    private final SpringDataOutboxJpaRepository jpaRepository;

    public JpaOutboxRepositoryAdapter(SpringDataOutboxJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return toDomain(jpaRepository.save(toEntity(event)));
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return jpaRepository.findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    private OutboxEventEntity toEntity(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(event.getId());
        entity.setEventType(event.getEventType().name());
        entity.setOrderId(event.getOrderId());
        entity.setCustomerEmail(event.getCustomerEmail());
        entity.setCreatedAt(event.getCreatedAt());
        entity.setPublishedAt(event.getPublishedAt());
        entity.setAttempts(event.getAttempts());
        entity.setLastError(event.getLastError());
        return entity;
    }

    private OutboxEvent toDomain(OutboxEventEntity entity) {
        return OutboxEvent.reconstruct(entity.getId(), EventType.valueOf(entity.getEventType()), entity.getOrderId(),
                entity.getCustomerEmail(), entity.getCreatedAt(), entity.getPublishedAt(), entity.getAttempts(),
                entity.getLastError());
    }
}
