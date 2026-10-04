package co.andesexpress.orders.domain.port.in;

import co.andesexpress.orders.domain.model.Order;

public interface GetOrderStatusInputPort {
    Order getOrderStatus(String orderId);
}