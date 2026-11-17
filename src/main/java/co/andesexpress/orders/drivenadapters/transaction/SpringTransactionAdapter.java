package co.andesexpress.orders.drivenadapters.transaction;

import co.andesexpress.orders.domain.port.out.TransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Transacción de base de datos de Spring: el pedido y su evento del outbox se confirman juntos o ninguno. */
@Component
public class SpringTransactionAdapter implements TransactionPort {

    private final TransactionTemplate transactionTemplate;

    public SpringTransactionAdapter(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
