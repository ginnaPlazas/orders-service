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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Carrera de dos POST /orders con la misma clave: esta petición consulta antes del commit de la otra,
 * choca con la restricción UNIQUE real (H2) y debe responder con el pedido guardado, nunca 500.
 */
@SpringBootTest(properties = "coverage.base-url=http://127.0.0.1:1")
@AutoConfigureMockMvc
class CreateOrderConcurrentDuplicateTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoSpyBean
    OrderRepositoryPort repository;

    private static String body(String key) {
        return """
                {"idempotencyKey":"%s","customerEmail":"cliente@andesexpress.com",
                 "origin":{"departmentId":5,"cityId":1},"destination":{"departmentId":15,"cityId":2},
                 "products":[{"name":"Caja","weightKg":2.5}]}
                """.formatted(key);
    }

    private Order saveWinner(String key, OrderStatus status, String rejectionReason) {
        return repository.save(Order.reconstruct(UUID.randomUUID().toString(), key, "cliente@andesexpress.com",
                new Address(5, 1), new Address(15, 2), List.of(new Product("Caja", new BigDecimal("2.5"))),
                ShippingType.EXPRESS, status, null, null, null, rejectionReason));
    }

    @Test
    void perdedorDeLaCarrera_responde201ConElPedidoDelGanador() throws Exception {
        String key = UUID.randomUUID().toString();
        Order ganador = saveWinner(key, OrderStatus.VALIDATED, null);
        // La primera consulta no ve al ganador (aún no había hecho commit); las siguientes sí
        doReturn(Optional.empty()).doCallRealMethod().when(repository).findByIdempotencyKey(key);

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body(key)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ganador.getId()))
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.shippingType").value("EXPRESS"));
    }

    @Test
    void perdedorDeLaCarrera_ganadorRechazado_responde400ConElMensajeOriginal() throws Exception {
        String key = UUID.randomUUID().toString();
        saveWinner(key, OrderStatus.REJECTED, "El municipio no existe");
        doReturn(Optional.empty()).doCallRealMethod().when(repository).findByIdempotencyKey(key);

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body(key)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El municipio no existe"));
    }
}
