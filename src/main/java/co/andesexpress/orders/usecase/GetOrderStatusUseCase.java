package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.OrderView;
import co.andesexpress.orders.domain.port.in.GetOrderStatusInputPort;
import co.andesexpress.orders.domain.port.out.GuideUrlSignerPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

public class GetOrderStatusUseCase implements GetOrderStatusInputPort {

    private final OrderRepositoryPort orderRepository;
    private final GuideUrlSignerPort guideUrlSigner;

    public GetOrderStatusUseCase(OrderRepositoryPort orderRepository, GuideUrlSignerPort guideUrlSigner) {
        this.orderRepository = orderRepository;
        this.guideUrlSigner = guideUrlSigner;
    }

    @Override
    public OrderView getOrderStatus(String orderId) {
        return toView(orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId)));
    }

    @Override
    public OrderView toView(Order order) {
        // RF-4: la URL prefirmada caduca, así que se genera en cada respuesta a partir de la clave guardada.
        String guideUrl = (order.getStatus() == OrderStatus.GUIDE_READY && order.getGuideKey() != null)
                ? guideUrlSigner.presignedGetUrl(order.getGuideKey())
                : null;
        return new OrderView(order, guideUrl);
    }
}
