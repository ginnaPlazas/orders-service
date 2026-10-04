package co.andesexpress.orders.drivenadapters.postgresrepository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private BigDecimal weightKg;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    public ProductEntity() {}

    public ProductEntity(String name, BigDecimal weightKg) {
        this.name = name;
        this.weightKg = weightKg;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getWeightKg() { return weightKg; }
    public OrderEntity getOrder() { return order; }
    public void setOrder(OrderEntity order) { this.order = order; }
}