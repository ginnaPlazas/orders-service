package co.andesexpress.orders.domain.port.out;

public interface EventPublisherPort {
    void publishOrderConfirmed(String orderId);
}