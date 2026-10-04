package co.andesexpress.orders.drivenadapters.postgresrepository;

import co.andesexpress.orders.domain.model.*;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import co.andesexpress.orders.drivenadapters.postgresrepository.entity.OrderEntity;
import co.andesexpress.orders.drivenadapters.postgresrepository.entity.ProductEntity;
import org.springframework.stereotype.Component;

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
        OrderEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return jpaRepository.findById(orderId).map(this::toDomain);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    private OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.getId());
        entity.setIdempotencyKey(order.getIdempotencyKey());
        entity.setOriginDepartmentId(order.getOrigin().getDepartmentId());
        entity.setOriginCityId(order.getOrigin().getCityId());
        entity.setDestinationDepartmentId(order.getDestination().getDepartmentId());
        entity.setDestinationCityId(order.getDestination().getCityId());
        entity.setStatus(order.getStatus().name());
        entity.setGuideUrl(order.getGuideUrl());
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
                new Address(entity.getOriginDepartmentId(), entity.getOriginCityId()),
                new Address(entity.getDestinationDepartmentId(), entity.getDestinationCityId()),
                products,
                OrderStatus.valueOf(entity.getStatus()),
                entity.getZone() != null ? Zone.valueOf(entity.getZone()) : null,
                (entity.getFareAmount() != null) ? new Fare(entity.getFareAmount(), entity.getFareCurrency()) : null,
                entity.getGuideUrl()
        );
        return order;
    }
}