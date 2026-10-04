package co.andesexpress.orders.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class Order {

    private String id;
    private final String idempotencyKey;
    private final Address origin;
    private final Address destination;
    private final List<Product> products;
    private OrderStatus status;
    private Fare fare;
    private Zone zone;
    private String guideUrl;

    public Order(String idempotencyKey, Address origin, Address destination, List<Product> products) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria");
        }
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("Origen y destino son obligatorios");
        }
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("El pedido debe tener al menos un producto");
        }
        this.id = UUID.randomUUID().toString();
        this.idempotencyKey = idempotencyKey;
        this.origin = origin;
        this.destination = destination;
        this.products = List.copyOf(products);
        this.status = OrderStatus.PENDING_VALIDATION;
    }

    public static Order reconstruct(String id, String idempotencyKey, Address origin, Address destination,
                                     List<Product> products, OrderStatus status, Zone zone, Fare fare, String guideUrl) {
        Order order = new Order(idempotencyKey, origin, destination, products);
        order.id = id;
        order.status = status;
        order.zone = zone;
        order.fare = fare;
        order.guideUrl = guideUrl;
        return order;
    }

    public BigDecimal getTotalWeightKg() {
        return products.stream()
                .map(Product::getWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void markValidated(Zone zone, Fare fare) {
        if (status != OrderStatus.PENDING_VALIDATION) {
            throw new IllegalStateException("Solo un pedido pendiente de validación puede pasar a validado");
        }
        this.zone = zone;
        this.fare = fare;
        this.status = OrderStatus.VALIDATED;
    }

    public void markRejected() {
        if (status != OrderStatus.PENDING_VALIDATION) {
            throw new IllegalStateException("Solo un pedido pendiente de validación puede ser rechazado");
        }
        this.status = OrderStatus.REJECTED;
    }

    public void confirm() {
        if (status != OrderStatus.VALIDATED) {
            throw new IllegalStateException("Solo un pedido validado puede confirmarse");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void markGuideGenerating() {
        if (status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Solo un pedido confirmado puede iniciar generación de guía");
        }
        this.status = OrderStatus.GUIDE_GENERATING;
    }

    public void markGuideReady(String guideUrl) {
        if (status != OrderStatus.GUIDE_GENERATING) {
            throw new IllegalStateException("Solo un pedido generando guía puede marcarse como lista");
        }
        this.guideUrl = guideUrl;
        this.status = OrderStatus.GUIDE_READY;
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Address getOrigin() { return origin; }
    public Address getDestination() { return destination; }
    public List<Product> getProducts() { return products; }
    public OrderStatus getStatus() { return status; }
    public Fare getFare() { return fare; }
    public Zone getZone() { return zone; }
    public String getGuideUrl() { return guideUrl; }
}