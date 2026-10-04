package co.andesexpress.orders.domain.model;

import java.math.BigDecimal;

public final class Product {

    private final String name;
    private final BigDecimal weightKg;

    public Product(String name, BigDecimal weightKg) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto no puede ser vacío");
        }
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("El peso del producto debe ser mayor a cero");
        }
        this.name = name;
        this.weightKg = weightKg;
    }

    public String getName() { return name; }
    public BigDecimal getWeightKg() { return weightKg; }
}