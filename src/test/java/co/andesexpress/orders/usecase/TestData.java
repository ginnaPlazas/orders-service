package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.model.*;

import java.math.BigDecimal;
import java.util.List;

final class TestData {

    private TestData() {}

    static Address origin() { return new Address(5, 1); }        // Antioquia
    static Address destination() { return new Address(15, 2); }  // Boyacá

    static List<Product> products() {
        return List.of(new Product("Caja", new BigDecimal("2.5")),
                       new Product("Sobre", new BigDecimal("0.5")));
    }

    static Fare fare() { return new Fare(new BigDecimal("15000"), "COP"); }

    static CreateOrderCommand command(String key) {
        return new CreateOrderCommand(key, origin(), destination(), products());
    }

    /** Pedido en el estado que le pidas. */
    static Order orderIn(OrderStatus status) {
        return Order.reconstruct("order-1", "key-1", origin(), destination(), products(),
                status, null, null, null);
    }
}