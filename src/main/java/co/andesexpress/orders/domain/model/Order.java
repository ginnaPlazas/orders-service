package co.andesexpress.orders.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class Order {

    private String id;
    private final String idempotencyKey;
    private final String customerEmail;
    private final Address origin;
    private final Address destination;
    private final List<Product> products;
    private final ShippingType shippingType;
    private OrderStatus status;
    private Fare fare;
    private Zone zone;
    private String guideKey;
    private String rejectionReason;

    public Order(String idempotencyKey, String customerEmail, Address origin, Address destination, List<Product> products) {
        this(idempotencyKey, customerEmail, origin, destination, products, ShippingType.STANDARD);
    }

    /** Si no llega tipo de envío se usa STANDARD. */
    public Order(String idempotencyKey, String customerEmail, Address origin, Address destination, List<Product> products,
                 ShippingType shippingType) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria");
        }
         if (customerEmail == null || customerEmail.isBlank() || !customerEmail.contains("@")) {
        throw new IllegalArgumentException("El correo del cliente es obligatorio y debe ser válido");
        }

        if (origin == null || destination == null) {
            throw new IllegalArgumentException("Origen y destino son obligatorios");
        }
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("El pedido debe tener al menos un producto");
        }
        this.id = UUID.randomUUID().toString();
        this.idempotencyKey = idempotencyKey;
        this.customerEmail = customerEmail;
        this.origin = origin;
        this.destination = destination;
        this.products = List.copyOf(products);
        this.shippingType = shippingType == null ? ShippingType.STANDARD : shippingType;
        this.status = OrderStatus.PENDING_VALIDATION;
    }

    public static Order reconstruct(String id, String idempotencyKey, String customerEmail, Address origin, Address destination,
                                     List<Product> products, OrderStatus status, Zone zone, Fare fare, String guideKey) {
        return reconstruct(id, idempotencyKey, customerEmail, origin, destination, products, ShippingType.STANDARD,
                status, zone, fare, guideKey, null);
    }

    public static Order reconstruct(String id, String idempotencyKey, String customerEmail, Address origin, Address destination,
                                     List<Product> products, ShippingType shippingType, OrderStatus status, Zone zone,
                                     Fare fare, String guideKey, String rejectionReason) {
        Order order = new Order(idempotencyKey, customerEmail ,origin, destination, products, shippingType);
        order.id = id;
        order.status = status;
        order.zone = zone;
        order.fare = fare;
        order.guideKey = guideKey;
        order.rejectionReason = rejectionReason;
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

    public void markRejected(String rejectionReason) {
        if (status != OrderStatus.PENDING_VALIDATION) {
            throw new IllegalStateException("Solo un pedido pendiente de validación puede ser rechazado");
        }
        this.rejectionReason = rejectionReason;
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

    public void markGuideReady(String guideKey) {
        if (status != OrderStatus.GUIDE_GENERATING) {
            throw new IllegalStateException("Solo un pedido generando guía puede marcarse como lista");
        }
        if (guideKey == null || guideKey.isBlank()) {
            throw new IllegalArgumentException("La clave de la guía en S3 es obligatoria");
        }
        this.guideKey = guideKey;
        this.status = OrderStatus.GUIDE_READY;
    }

    public String getId() { 
        return id; 
    }

    public String getIdempotencyKey() { 
        return idempotencyKey; 
    }

    public String getCustomerEmail() { 
        return customerEmail; 
    }

    public Address getOrigin() { 
        return origin; 
    }

    public Address getDestination() {
        return destination; 
    }

    public List<Product> getProducts() { 
        return products; 
    }

    public OrderStatus getStatus() {
        return status; 
    }

    public Fare getFare() { 
        return fare; 
    }

    public Zone getZone() { 
        return zone; 
    }

    /** Clave del objeto PDF de la guía en S3 (nunca una URL: la URL prefirmada se genera al leer). */
    public String getGuideKey() {
        return guideKey;
    }

    public ShippingType getShippingType() {
        return shippingType;
    }

    /** Mensaje con el que Coverage rechazó el destino; null si el pedido no fue rechazado. */
    public String getRejectionReason() {
        return rejectionReason;
    }
}
