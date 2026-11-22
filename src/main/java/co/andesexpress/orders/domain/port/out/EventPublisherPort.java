package co.andesexpress.orders.domain.port.out;

import co.andesexpress.orders.domain.model.Order;

public interface EventPublisherPort {

    /**
     * Publica el evento PedidoConfirmado con los datos completos del pedido (origen, destino,
     * productos, zona, tarifa...), para que quien lo consuma (Lambda del PDF, Notifications)
     * no tenga que volver a preguntarle nada a Orders.
     */
    void publishOrderConfirmed(Order order);
}
