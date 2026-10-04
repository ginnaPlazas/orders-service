package co.andesexpress.orders.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class Fare {

    private final BigDecimal amount;
    private final String currency;

    public Fare(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("El monto de la tarifa no puede ser negativo o nulo");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("La moneda de la tarifa no puede ser nula o vacía");
        }
        this.amount = amount;
        this.currency = currency;
    }

    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fare fare)) return false;
        return amount.compareTo(fare.amount) == 0 && currency.equals(fare.currency);
    }

    @Override
    public int hashCode() { return Objects.hash(amount, currency); }
}