package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.model.OutboxEvent;
import co.andesexpress.orders.domain.port.out.EventPublisherPort;

import java.util.ArrayList;
import java.util.List;

class FakeEventPublisher implements EventPublisherPort {

    final List<OutboxEvent> published = new ArrayList<>();
    /** Si no es null, toda publicación falla con esta excepción. */
    RuntimeException failure;
    /** Si no es null, falla solo la publicación de eventos de este pedido. */
    String failForOrderId;

    @Override
    public void publish(OutboxEvent event) {
        if (failure != null) {
            throw failure;
        }
        if (event.getOrderId().equals(failForOrderId)) {
            throw new RuntimeException("SNS rechazó el evento de " + failForOrderId);
        }
        published.add(event);
    }
}
