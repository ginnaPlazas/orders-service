package co.andesexpress.orders.domain.port.in;

public interface RelayOutboxEventsInputPort {
    /** Publica los eventos pendientes del outbox; devuelve cuántos quedaron publicados en esta ejecución. */
    int relayPendingEvents();
}
