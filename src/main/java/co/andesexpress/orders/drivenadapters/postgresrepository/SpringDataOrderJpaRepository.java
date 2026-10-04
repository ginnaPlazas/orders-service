package co.andesexpress.orders.drivenadapters.postgresrepository;

import co.andesexpress.orders.drivenadapters.postgresrepository.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpringDataOrderJpaRepository extends JpaRepository<OrderEntity, String> {
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}