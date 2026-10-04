package co.andesexpress.orders.drivenadapters.postgresrepository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private int originDepartmentId;
    private int originCityId;
    private int destinationDepartmentId;
    private int destinationCityId;

    private String status;

    private String zone;
    private BigDecimal fareAmount;
    private String fareCurrency;
    private String guideUrl;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductEntity> products;

    public OrderEntity() {} // requerido por JPA

    // Getters y setters (JPA los necesita)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public int getOriginDepartmentId() { return originDepartmentId; }
    public void setOriginDepartmentId(int v) { this.originDepartmentId = v; }
    public int getOriginCityId() { return originCityId; }
    public void setOriginCityId(int v) { this.originCityId = v; }
    public int getDestinationDepartmentId() { return destinationDepartmentId; }
    public void setDestinationDepartmentId(int v) { this.destinationDepartmentId = v; }
    public int getDestinationCityId() { return destinationCityId; }
    public void setDestinationCityId(int v) { this.destinationCityId = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public BigDecimal getFareAmount() { return fareAmount; }
    public void setFareAmount(BigDecimal v) { this.fareAmount = v; }
    public String getFareCurrency() { return fareCurrency; }
    public void setFareCurrency(String v) { this.fareCurrency = v; }
    public String getGuideUrl() { return guideUrl; }
    public void setGuideUrl(String guideUrl) { this.guideUrl = guideUrl; }
    public List<ProductEntity> getProducts() { return products; }
    public void setProducts(List<ProductEntity> products) { this.products = products; }
}