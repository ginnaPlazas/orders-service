package co.andesexpress.orders.drivenadapters.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataOutboxJpaRepository extends JpaRepository<OutboxEventEntity, String> {
    List<OutboxEventEntity> findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(Pageable pageable);
}
