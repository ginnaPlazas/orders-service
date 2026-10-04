package co.andesexpress.orders.domain.port.in;

import co.andesexpress.orders.domain.model.CreateOrderCommand;
import co.andesexpress.orders.domain.model.Order;

public interface CreateOrderInputPort {
    Order createOrder(CreateOrderCommand command);
}