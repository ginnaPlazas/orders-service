package co.andesexpress.orders.domain.model;

import java.util.List;

public final class CreateOrderCommand {

    private final String idempotencyKey;
    private final Address origin;
    private final Address destination;
    private final List<Product> products;

    public CreateOrderCommand(String idempotencyKey, Address origin, Address destination, List<Product> products) {
        this.idempotencyKey = idempotencyKey;
        this.origin = origin;
        this.destination = destination;
        this.products = products;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public Address getOrigin() { return origin; }
    public Address getDestination() { return destination; }
    public List<Product> getProducts() { return products; }
}