package co.andesexpress.orders.entrypoints.web;

import co.andesexpress.orders.domain.model.Address;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.OutboxEvent;
import co.andesexpress.orders.domain.model.Product;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import co.andesexpress.orders.domain.port.out.OutboxRepositoryPort;
import co.andesexpress.orders.domain.port.out.TransactionPort;
import co.andesexpress.orders.entrypoints.web.mapper.OrderWebMapper;
import co.andesexpress.orders.usecase.ConfirmOrderUseCase;
import co.andesexpress.orders.usecase.GetOrderStatusUseCase;
import co.andesexpress.orders.usecase.MarkGuideReadyUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del PUT de la guía (Lambda) y de la URL prefirmada en las respuestas. */
class OrderControllerGuideTest {

    final Map<String, Order> orders = new HashMap<>();
    final List<OutboxEvent> outboxEvents = new ArrayList<>();
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        OrderRepositoryPort repository = new OrderRepositoryPort() {
            @Override
            public Order save(Order order) {
                orders.put(order.getId(), order);
                return order;
            }

            @Override
            public Optional<Order> findById(String orderId) {
                return Optional.ofNullable(orders.get(orderId));
            }

            @Override
            public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
                return Optional.empty();
            }
        };
        OutboxRepositoryPort outbox = new OutboxRepositoryPort() {
            @Override
            public OutboxEvent save(OutboxEvent event) {
                outboxEvents.add(event);
                return event;
            }

            @Override
            public List<OutboxEvent> findPending(int limit) {
                return List.of();
            }
        };
        TransactionPort transaction = new TransactionPort() {
            @Override
            public <T> T inTransaction(Supplier<T> work) {
                return work.get();
            }
        };
        Clock clock = Clock.systemUTC();

        OrderController controller = new OrderController(
                command -> { throw new UnsupportedOperationException(); },
                new ConfirmOrderUseCase(repository, outbox, transaction, clock),
                new GetOrderStatusUseCase(repository, key -> "https://guias.s3.amazonaws.com/" + key + "?X-Amz-Signature=f1rma"),
                new MarkGuideReadyUseCase(repository, outbox, transaction, clock),
                new OrderWebMapper());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    void givenOrder(OrderStatus status, String guideKey) {
        orders.put("order-1", Order.reconstruct("order-1", "key-1", "cliente@andesexpress.com",
                new Address(5, 1), new Address(15, 2), List.of(new Product("Caja", new BigDecimal("2.5"))),
                status, null, null, guideKey));
    }

    @Test
    void putGuia_conGuideKey_guardaLaClaveYDevuelveUrlPrefirmada() throws Exception {
        givenOrder(OrderStatus.GUIDE_GENERATING, null);

        mvc.perform(put("/orders/order-1/guide").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guideKey\":\"guias/order-1.pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GUIDE_READY"))
                .andExpect(jsonPath("$.guideUrl").value("https://guias.s3.amazonaws.com/guias/order-1.pdf?X-Amz-Signature=f1rma"))
                .andExpect(jsonPath("$.guideKey").doesNotExist());

        assertEquals("guias/order-1.pdf", orders.get("order-1").getGuideKey());
        assertEquals(1, outboxEvents.size());
        assertEquals("GuiaLista", outboxEvents.get(0).getEventType().wireName());
    }

    @Test
    void putGuia_conGuideKeyVacia_responde400YNoCambiaElPedido() throws Exception {
        givenOrder(OrderStatus.GUIDE_GENERATING, null);

        mvc.perform(put("/orders/order-1/guide").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guideKey\":\"  \"}"))
                .andExpect(status().isBadRequest());

        assertEquals(OrderStatus.GUIDE_GENERATING, orders.get("order-1").getStatus());
        assertTrue(outboxEvents.isEmpty());
    }

    @Test
    void putGuia_conElCampoAntiguoGuideUrl_responde400() throws Exception {
        givenOrder(OrderStatus.GUIDE_GENERATING, null);

        mvc.perform(put("/orders/order-1/guide").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guideUrl\":\"https://ejemplo.com/guia.pdf\"}"))
                .andExpect(status().isBadRequest());

        assertNull(orders.get("order-1").getGuideKey());
    }

    @Test
    void putGuia_enEstadoInvalido_responde409() throws Exception {
        givenOrder(OrderStatus.CONFIRMED, null);

        mvc.perform(put("/orders/order-1/guide").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guideKey\":\"guias/order-1.pdf\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getPedido_conGuiaLista_devuelveUrlPrefirmadaYSinGuiaDevuelveNull() throws Exception {
        givenOrder(OrderStatus.GUIDE_READY, "guias/order-1.pdf");
        mvc.perform(get("/orders/order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guideUrl").value("https://guias.s3.amazonaws.com/guias/order-1.pdf?X-Amz-Signature=f1rma"));

        givenOrder(OrderStatus.GUIDE_GENERATING, null);
        mvc.perform(get("/orders/order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GUIDE_GENERATING"))
                .andExpect(jsonPath("$.guideUrl").isEmpty());
    }

    @Test
    void confirmar_responde200ConGuideUrlNulaYGuardaElEvento() throws Exception {
        givenOrder(OrderStatus.VALIDATED, null);

        mvc.perform(post("/orders/order-1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GUIDE_GENERATING"))
                .andExpect(jsonPath("$.guideUrl").isEmpty());

        assertEquals(1, outboxEvents.size());
        assertEquals("PedidoConfirmado", outboxEvents.get(0).getEventType().wireName());
    }
}
