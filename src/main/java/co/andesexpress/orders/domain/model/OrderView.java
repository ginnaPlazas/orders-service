package co.andesexpress.orders.domain.model;

/**
 * Vista de lectura de un pedido: el pedido más la URL prefirmada de la guía,
 * resuelta en el momento de responder (solo en GUIDE_READY; en otro caso {@code null}).
 */
public record OrderView(Order order, String guideUrl) {
}
