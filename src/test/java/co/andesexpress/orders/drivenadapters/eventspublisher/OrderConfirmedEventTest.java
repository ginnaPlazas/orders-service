package co.andesexpress.orders.drivenadapters.eventspublisher;

import co.andesexpress.orders.domain.model.Address;
import co.andesexpress.orders.domain.model.Fare;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.Product;
import co.andesexpress.orders.domain.model.ShippingType;
import co.andesexpress.orders.domain.model.Zone;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderConfirmedEventTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private Order pedido(Zone zone, Fare fare) {
        return Order.reconstruct("order-1", "key-1", "cliente@andesexpress.com",
                new Address(5, 1), new Address(15, 2),
                List.of(new Product("Caja \"frágil\"\ncon saltos", new BigDecimal("2.5")),
                        new Product("Sobre", new BigDecimal("0.5"))),
                OrderStatus.GUIDE_GENERATING, zone, fare, null, ShippingType.EXPRESS);
    }

    @SuppressWarnings("unchecked")
    @Test
    void elEventoLlevaLosDatosCompletosDelPedido_yElJsonEsValidoAunConComillasYSaltosDeLinea() {
        Order order = pedido(Zone.NEIGHBORING_DEPARTMENT, new Fare(new BigDecimal("15000"), "COP"));

        String json = mapper.writeValueAsString(OrderConfirmedEvent.from(order));
        Map<String, Object> body = mapper.readValue(json, Map.class);

        assertEquals("PedidoConfirmado", body.get("eventType"));
        assertEquals("order-1", body.get("orderId"));
        assertEquals("cliente@andesexpress.com", body.get("customerEmail"));
        assertEquals("EXPRESS", body.get("shippingType"));
        assertEquals("NEIGHBORING_DEPARTMENT", body.get("zone"));
        assertNotNull(body.get("occurredAt"));

        Map<String, Object> origin = (Map<String, Object>) body.get("origin");
        assertEquals(5, origin.get("departmentId"));
        assertEquals(1, origin.get("cityId"));
        Map<String, Object> destination = (Map<String, Object>) body.get("destination");
        assertEquals(15, destination.get("departmentId"));
        assertEquals(2, destination.get("cityId"));

        Map<String, Object> fare = (Map<String, Object>) body.get("fare");
        assertEquals("COP", fare.get("currency"));
        assertEquals(0, new BigDecimal("15000").compareTo(new BigDecimal(fare.get("amount").toString())));
        assertEquals(0, new BigDecimal("3.0").compareTo(new BigDecimal(body.get("totalWeightKg").toString())));

        List<Map<String, Object>> products = (List<Map<String, Object>>) body.get("products");
        assertEquals(2, products.size());
        // el nombre con comillas y salto de línea sobrevive intacto (con el JSON a mano se rompía)
        assertEquals("Caja \"frágil\"\ncon saltos", products.get(0).get("name"));
        assertEquals("Sobre", products.get(1).get("name"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void pedidoSinZonaNiTarifa_noRevienta() {
        Order order = pedido(null, null);

        String json = mapper.writeValueAsString(OrderConfirmedEvent.from(order));
        Map<String, Object> body = mapper.readValue(json, Map.class);

        assertEquals("order-1", body.get("orderId"));
        assertNull(body.get("zone"));
        assertNull(body.get("fare"));
    }
}
