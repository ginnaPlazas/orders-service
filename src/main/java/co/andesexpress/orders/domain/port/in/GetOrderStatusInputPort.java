package co.andesexpress.orders.domain.port.in;

import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderView;

public interface GetOrderStatusInputPort {
    OrderView getOrderStatus(String orderId);

    /** Vista de lectura de un pedido ya cargado (resuelve la URL prefirmada de la guía si aplica). */
    OrderView toView(Order order);
}
