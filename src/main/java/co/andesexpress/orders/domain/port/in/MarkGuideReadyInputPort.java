package co.andesexpress.orders.domain.port.in;

import co.andesexpress.orders.domain.model.Order;

public interface MarkGuideReadyInputPort {
    Order markGuideReady(String orderId, String guideUrl);
}