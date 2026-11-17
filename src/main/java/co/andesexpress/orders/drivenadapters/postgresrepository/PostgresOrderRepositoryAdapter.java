package co.andesexpress.orders.drivenadapters.postgresrepository;

import co.andesexpress.orders.domain.model.*;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import co.andesexpress.orders.drivenadapters.postgresrepository.entity.OrderEntity;
import co.andesexpress.orders.drivenadapters.postgresrepository.entity.ProductEntity;
import co.andesexpress.orders.domain.exception.DuplicateIdempotencyKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PostgresOrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderJpaRepository jpaRepository;

    public PostgresOrderRepositoryAdapter(SpringDataOrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = toEntity(order);
        try {
            OrderEntity saved = jpaRepository.save(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(idempotencyKey): una petición concurrente guardó primero otro pedido con la misma clave
            boolean keyTakenByAnotherOrder = jpaRepository.findByIdempotencyKey(order.getIdempotencyKey())
                    .filter(existing -> !existing.getId().equals(order.getId()))
                    .isPresent();
            if (keyTakenByAnotherOrder) {
                throw new DuplicateIdempotencyKeyException(order.getIdempotencyKey(), e);
            }
            throw e;
        }
    }

    // Las lecturas abren su propia transacción para poder cargar los productos (colección lazy)
    // también fuera de una petición HTTP.
    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(String orderId) {
        return jpaRepository.findById(orderId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    private OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.getId());
        entity.setIdempotencyKey(order.getIdempotencyKey());
        entity.setCustomerEmail(order.getCustomerEmail());
        entity.setOriginDepartmentId(order.getOrigin().getDepartmentId());
        entity.setOriginCityId(order.getOrigin().getCityId());
        entity.setDestinationDepartmentId(order.getDestination().getDepartmentId());
        entity.setDestinationCityId(order.getDestination().getCityId());
        entity.setStatus(order.getStatus().name());
        entity.setShippingType(order.getShippingType().name());
        entity.setRejectionReason(order.getRejectionReason());
        entity.setGuideKey(order.getGuideKey());
        if (order.getZone() != null) entity.setZone(order.getZone().name());
        if (order.getFare() != null) {
            entity.setFareAmount(order.getFare().getAmount());
            entity.setFareCurrency(order.getFare().getCurrency());
        }
        List<ProductEntity> productEntities = order.getProducts().stream()
                .map(p -> {
                    ProductEntity pe = new ProductEntity(p.getName(), p.getWeightKg());
                    pe.setOrder(entity);
                    return pe;
                })
                .collect(Collectors.toList());
        entity.setProducts(productEntities);
        return entity;
    }

    private Order toDomain(OrderEntity entity) {
        List<Product> products = entity.getProducts() == null ? List.of() :
                entity.getProducts().stream()
                        .map(pe -> new Product(pe.getName(), pe.getWeightKg()))
                        .collect(Collectors.toList());

        Order order = Order.reconstruct(
                entity.getId(),
                entity.getIdempotencyKey(),
                entity.getCustomerEmail(),
                new Address(entity.getOriginDepartmentId(), entity.getOriginCityId()),
                new Address(entity.getDestinationDepartmentId(), entity.getDestinationCityId()),
                products,
                // Filas anteriores a la columna shippingType: se leen como STANDARD
                entity.getShippingType() != null ? ShippingType.valueOf(entity.getShippingType()) : ShippingType.STANDARD,
                OrderStatus.valueOf(entity.getStatus()),
                entity.getZone() != null ? Zone.valueOf(entity.getZone()) : null,
                (entity.getFareAmount() != null) ? new Fare(entity.getFareAmount(), entity.getFareCurrency()) : null,
                entity.getGuideKey(),
                entity.getRejectionReason()
        );
        return order;
    }
}