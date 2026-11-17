package co.andesexpress.orders.entrypoints.web;

import co.andesexpress.orders.domain.model.Address;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.Product;
import co.andesexpress.orders.domain.model.ShippingType;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /orders de punta a punta (H2). Coverage apunta a un puerto cerrado: cualquier llamada real falla.
 */
@SpringBootTest(properties = {
        "coverage.base-url=http://127.0.0.1:1",
        "resilience4j.retry.instances.coverageService.wait-duration=10ms"
})
@AutoConfigureMockMvc
class CreateOrderEndpointTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepositoryPort repository;

    private static String body(String key, String shippingTypeField) {
        return """
                {"idempotencyKey":"%s","customerEmail":"cliente@andesexpress.com",
                 "origin":{"departmentId":5,"cityId":1},"destination":{"departmentId":15,"cityId":2},
                 "products":[{"name":"Caja","weightKg":2.5}]%s}
                """.formatted(key, shippingTypeField);
    }

    private static Order storedOrder(String key, ShippingType shippingType, OrderStatus status, String rejectionReason) {
        return Order.reconstruct(UUID.randomUUID().toString(), key, "cliente@andesexpress.com", new Address(5, 1),
                new Address(15, 2), List.of(new Product("Caja", new BigDecimal("2.5"))), shippingType,
                status, null, null, null, rejectionReason);
    }

    @Test
    void tipoDeEnvioDesconocido_responde400() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(key, ",\"shippingType\":\"OVERNIGHT\"")))
                .andExpect(status().isBadRequest());

        assertTrue(repository.findByIdempotencyKey(key).isEmpty());
    }

    @Test
    void reintentoDePedidoValidado_responde201ConElTipoDeEnvioGuardado() throws Exception {
        String key = UUID.randomUUID().toString();
        Order existente = repository.save(storedOrder(key, ShippingType.EXPRESS, OrderStatus.VALIDATED, null));

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body(key, "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(existente.getId()))
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.shippingType").value("EXPRESS"));
    }

    @Test
    void reintentoDePedidoRechazado_responde400ConElMensajeOriginal() throws Exception {
        String key = UUID.randomUUID().toString();
        repository.save(storedOrder(key, ShippingType.STANDARD, OrderStatus.REJECTED, "El municipio no existe"));

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body(key, "")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El municipio no existe"));

        assertEquals(OrderStatus.REJECTED, repository.findByIdempotencyKey(key).orElseThrow().getStatus());
    }

    @Test
    void coverageCaido_responde503YElPedidoQuedaPendienteConTipoStandard() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body(key, "")))
                .andExpect(status().isServiceUnavailable());
        // El reintento vuelve a validar y Coverage sigue caído: otra vez 503, no 201 con un pedido pendiente
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body(key, "")))
                .andExpect(status().isServiceUnavailable());

        Order guardado = repository.findByIdempotencyKey(key).orElseThrow();
        assertEquals(OrderStatus.PENDING_VALIDATION, guardado.getStatus());
        assertEquals(ShippingType.STANDARD, guardado.getShippingType());
    }
}
