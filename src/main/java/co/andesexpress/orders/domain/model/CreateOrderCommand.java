package co.andesexpress.orders.domain.model;

import java.util.List;

public final class CreateOrderCommand {

    private final String idempotencyKey;
    private final String customerEmail;
    private final Address origin;
    private final Address destination;
    private final List<Product> products;
    private final ShippingType shippingType;

    public CreateOrderCommand(String idempotencyKey, String customerEmail, Address origin, Address destination, List<Product> products) {
        this(idempotencyKey, customerEmail, origin, destination, products, ShippingType.STANDARD);
    }

    /** Si no llega tipo de envío se usa STANDARD. */
    public CreateOrderCommand(String idempotencyKey, String customerEmail, Address origin, Address destination,
                              List<Product> products, ShippingType shippingType) {
        this.idempotencyKey = idempotencyKey;
        this.customerEmail = customerEmail;
        this.origin = origin;
        this.destination = destination;
        this.products = products;
        this.shippingType = shippingType == null ? ShippingType.STANDARD : shippingType;
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

    public ShippingType getShippingType() {
        return shippingType;
    }
}
