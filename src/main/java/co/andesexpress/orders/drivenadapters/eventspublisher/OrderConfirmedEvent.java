package co.andesexpress.orders.drivenadapters.eventspublisher;

import co.andesexpress.orders.domain.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contenido del evento PedidoConfirmado tal como viaja por SNS/SQS (se serializa a JSON).
 *
 * Lleva una "foto" completa del pedido para que la Lambda pueda armar el PDF de la guía
 * (ids, destinos, productos, tarifa) sin depender de que Orders esté disponible en ese momento.
 * Notifications solo usa eventType, orderId y customerEmail e ignora el resto.
 */
public record OrderConfirmedEvent(
        String eventType,
        String orderId,
        String customerEmail,
        String shippingType,
        Place origin,
        Place destination,
        List<Item> products,
        BigDecimal totalWeightKg,
        String zone,
        Money fare,
        String occurredAt) {

    public static final String EVENT_TYPE = "PedidoConfirmado";

    /** Ids de API Colombia (no códigos DANE). */
    public record Place(int departmentId, int cityId) {
    }

    public record Item(String name, BigDecimal weightKg) {
    }

    public record Money(BigDecimal amount, String currency) {
    }

    public static OrderConfirmedEvent from(Order order) {
        return new OrderConfirmedEvent(
                EVENT_TYPE,
                order.getId(),
                order.getCustomerEmail(),
                order.getShippingType().name(),
                new Place(order.getOrigin().getDepartmentId(), order.getOrigin().getCityId()),
                new Place(order.getDestination().getDepartmentId(), order.getDestination().getCityId()),
                order.getProducts().stream()
                        .map(p -> new Item(p.getName(), p.getWeightKg()))
                        .toList(),
                order.getTotalWeightKg(),
                order.getZone() == null ? null : order.getZone().name(),
                order.getFare() == null
                        ? null
                        : new Money(order.getFare().getAmount(), order.getFare().getCurrency()),
                Instant.now().toString());
    }
}
