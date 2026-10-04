package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.DestinationRejectedException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.Zone;
import co.andesexpress.orders.domain.port.out.CoverageClientPort.CoverageValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CreateOrderUseCaseTest {

    FakeOrderRepository repository;
    AtomicInteger llamadasCoverage;
    CoverageValidationResult respuestaCoverage;
    CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new FakeOrderRepository();
        llamadasCoverage = new AtomicInteger();
        // Coverage falso: cuenta las llamadas y devuelve la respuesta configurada
        useCase = new CreateOrderUseCase(repository, (origin, destination, weight) -> {
            llamadasCoverage.incrementAndGet();
            return respuestaCoverage;
        });
    }

    @Test
    void destinoValido_quedaValidadoConZonaYTarifa() {
        respuestaCoverage = new CoverageValidationResult(true, Zone.NEIGHBORING_DEPARTMENT, TestData.fare(), null);

        Order order = useCase.createOrder(TestData.command("key-1"));

        assertEquals(OrderStatus.VALIDATED, order.getStatus());
        assertEquals(Zone.NEIGHBORING_DEPARTMENT, order.getZone());
        assertEquals(TestData.fare(), order.getFare());
        assertEquals(0, new BigDecimal("3.0").compareTo(order.getTotalWeightKg()));
    }

    @Test
    void destinoInvalido_lanzaExcepcionYDejaElPedidoRechazado() {
        respuestaCoverage = new CoverageValidationResult(false, null, null, "El municipio no existe");

        DestinationRejectedException ex = assertThrows(DestinationRejectedException.class,
                () -> useCase.createOrder(TestData.command("key-1")));

        assertEquals("El municipio no existe", ex.getMessage());
        Order guardado = repository.findByIdempotencyKey("key-1").orElseThrow();
        assertEquals(OrderStatus.REJECTED, guardado.getStatus());
    }

    @Test
    void mismaClaveDeIdempotencia_devuelveElMismoPedidoSinLlamarOtraVezACoverage() {
        respuestaCoverage = new CoverageValidationResult(true, Zone.SAME_CITY, TestData.fare(), null);

        Order primero = useCase.createOrder(TestData.command("key-1"));
        Order segundo = useCase.createOrder(TestData.command("key-1"));

        assertEquals(primero.getId(), segundo.getId());
        assertEquals(1, llamadasCoverage.get());
        assertEquals(1, repository.store.size());
    }
}