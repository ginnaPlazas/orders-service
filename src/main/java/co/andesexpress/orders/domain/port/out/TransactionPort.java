package co.andesexpress.orders.domain.port.out;

import java.util.function.Supplier;

public interface TransactionPort {
    /** Ejecuta {@code work} en una única transacción: todo se guarda o nada. */
    <T> T inTransaction(Supplier<T> work);
}
