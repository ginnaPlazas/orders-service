package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.port.out.TransactionPort;

import java.util.function.Supplier;

/** Transacción falsa: registra cuántas transacciones se abrieron y si hay una activa. */
class FakeTransactionPort implements TransactionPort {

    int transactions;
    boolean active;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        if (active) {
            throw new IllegalStateException("Transacción anidada inesperada");
        }
        active = true;
        transactions++;
        try {
            return work.get();
        } finally {
            active = false;
        }
    }
}
