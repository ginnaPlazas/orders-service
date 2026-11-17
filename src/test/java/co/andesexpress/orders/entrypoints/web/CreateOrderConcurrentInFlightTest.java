package co.andesexpress.orders.entrypoints.web;

import co.andesexpress.orders.domain.model.Fare;
import co.andesexpress.orders.domain.model.Zone;
import co.andesexpress.orders.domain.port.out.CoverageClientPort;
import co.andesexpress.orders.domain.port.out.CoverageClientPort.CoverageValidationResult;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Two POST /orders with the same key while the first one is still validating against Coverage
 * (double click or client retry with a slow Coverage). Neither may answer 500, and both refer to the same order.
 */
@SpringBootTest(properties = "orders.outbox.relay.enabled=false")
@AutoConfigureMockMvc
class CreateOrderConcurrentInFlightTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepositoryPort repository;

    @MockitoBean
    CoverageClientPort coverage;

    private static String body(String key) {
        return """
                {"idempotencyKey":"%s","customerEmail":"cliente@andesexpress.com",
                 "origin":{"departmentId":5,"cityId":1},"destination":{"departmentId":15,"cityId":2},
                 "products":[{"name":"Caja","weightKg":2.5}]}
                """.formatted(key);
    }

    private void slowValidCoverage() {
        when(coverage.validate(any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(400);
            return new CoverageValidationResult(true, Zone.REST_OF_COUNTRY, new Fare(new BigDecimal("15000"), "COP"), null);
        });
    }

    private Callable<MockHttpServletResponse> postAfter(CyclicBarrier barrier, long delayMs, String key) {
        return () -> {
            barrier.await();
            Thread.sleep(delayMs);
            return mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body(key)))
                    .andReturn().getResponse();
        };
    }

    private void assertBothCreatedSameOrder(long secondDelayMs) throws Exception {
        slowValidCoverage();
        String key = UUID.randomUUID().toString();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<MockHttpServletResponse> first = executor.submit(postAfter(barrier, 0, key));
            Future<MockHttpServletResponse> second = executor.submit(postAfter(barrier, secondDelayMs, key));

            MockHttpServletResponse a = first.get();
            MockHttpServletResponse b = second.get();

            assertEquals(201, a.getStatus(), a.getContentAsString());
            assertEquals(201, b.getStatus(), b.getContentAsString());
            String idA = JsonPath.read(a.getContentAsString(), "$.id");
            String idB = JsonPath.read(b.getContentAsString(), "$.id");
            assertEquals(idA, idB);
            assertEquals("VALIDATED", repository.findByIdempotencyKey(key).orElseThrow().getStatus().name());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void dosPeticionesSimultaneas_mismaClave_ningun500() throws Exception {
        for (int round = 0; round < 3; round++) {
            assertBothCreatedSameOrder(0);
        }
    }

    @Test
    void reintentoMientrasLaPrimeraValida_ningun500() throws Exception {
        assertBothCreatedSameOrder(150);
    }
}
