package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.CoverageServiceUnavailableException;
import co.andesexpress.orders.domain.exception.DestinationRejectedException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.ShippingType;
import co.andesexpress.orders.domain.model.Zone;
import co.andesexpress.orders.domain.port.out.CoverageClientPort.CoverageValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CreateOrderUseCaseTest {

    FakeOrderRepository repository;
    AtomicInteger llamadasCoverage;
    List<BigDecimal> pesosEnviadosACoverage;
    CoverageValidationResult respuestaCoverage;
    boolean coverageCaido;
    CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new FakeOrderRepository();
        llamadasCoverage = new AtomicInteger();
        pesosEnviadosACoverage = new ArrayList<>();
        coverageCaido = false;
        // Coverage falso: cuenta las llamadas y devuelve la respuesta configurada (o falla si está caído)
        useCase = new CreateOrderUseCase(repository, (origin, destination, weight) -> {
            llamadasCoverage.incrementAndGet();
            pesosEnviadosACoverage.add(weight);
            if (coverageCaido) {
                throw new CoverageServiceUnavailableException("Coverage caído", null);
            }
            return respuestaCoverage;
        });
    }

    private Order guardado(String key) {
        return repository.findByIdempotencyKey(key).orElseThrow();
    }

    @Test
    void destinoValido_quedaValidadoConZonaYTarifa() {
        respuestaCoverage = new CoverageValidationResult(true, Zone.NEIGHBORING_DEPARTMENT, TestData.fare(), null);

        Order order = useCase.createOrder(TestData.command("key-1"));

        assertEquals(OrderStatus.VALIDATED, order.getStatus());
        assertEquals(Zone.NEIGHBORING_DEPARTMENT, order.getZone());
        assertEquals(TestData.fare(), order.getFare());
        assertEquals(0, new BigDecimal("3.0").compareTo(order.getTotalWeightKg()));
        assertEquals(OrderStatus.VALIDATED, guardado("key-1").getStatus());
    }

    @Test
    void destinoInvalido_lanzaExcepcionYDejaElPedidoRechazado() {
        respuestaCoverage = new CoverageValidationResult(false, null, null, "El municipio no existe");

        DestinationRejectedException ex = assertThrows(DestinationRejectedException.class,
                () -> useCase.createOrder(TestData.command("key-1")));

        assertEquals("El municipio no existe", ex.getMessage());
        Order guardado = guardado("key-1");
        assertEquals(OrderStatus.REJECTED, guardado.getStatus());
        assertEquals("El municipio no existe", guardado.getRejectionReason());
    }

    @Test
    void mismaClaveDeIdempotencia_devuelveElMismoPedidoSinLlamarOtraVezACoverage() {
        respuestaCoverage = new CoverageValidationResult(true, Zone.SAME_CITY, TestData.fare(), null);

        Order primero = useCase.createOrder(TestData.command("key-1"));
        Order segundo = useCase.createOrder(TestData.command("key-1"));

        assertEquals(primero.getId(), segundo.getId());
        assertEquals(OrderStatus.VALIDATED, segundo.getStatus());
        assertEquals(1, llamadasCoverage.get());
        assertEquals(1, repository.store.size());
    }

    // --- S1: tipo de envío ---

    @Test
    void sinTipoDeEnvio_quedaStandard() {
        respuestaCoverage = new CoverageValidationResult(true, Zone.SAME_CITY, TestData.fare(), null);

        Order order = useCase.createOrder(TestData.command("key-1", null));

        assertEquals(ShippingType.STANDARD, order.getShippingType());
        assertEquals(ShippingType.STANDARD, guardado("key-1").getShippingType());
    }

    @Test
    void tipoExpress_seGuardaYSeDevuelve() {
        respuestaCoverage = new CoverageValidationResult(true, Zone.SAME_CITY, TestData.fare(), null);

        Order order = useCase.createOrder(TestData.command("key-1", ShippingType.EXPRESS));

        assertEquals(ShippingType.EXPRESS, order.getShippingType());
        assertEquals(ShippingType.EXPRESS, guardado("key-1").getShippingType());
    }

    @Test
    void tipoExpress_noCambiaLaTarifaNiLoQueSeEnviaACoverage() {
        respuestaCoverage = new CoverageValidationResult(true, Zone.SAME_CITY, TestData.fare(), null);

        Order standard = useCase.createOrder(TestData.command("key-std", ShippingType.STANDARD));
        Order express = useCase.createOrder(TestData.command("key-exp", ShippingType.EXPRESS));

        assertEquals(TestData.fare(), express.getFare());
        assertEquals(standard.getFare(), express.getFare());
        assertEquals(2, pesosEnviadosACoverage.size());
        assertEquals(0, pesosEnviadosACoverage.get(0).compareTo(pesosEnviadosACoverage.get(1)));
    }

    // --- S2: reintento idempotente ---

    @Test
    void reintentoDePedidoPendiente_vuelveAValidarYQuedaValidado() {
        coverageCaido = true;
        assertThrows(CoverageServiceUnavailableException.class,
                () -> useCase.createOrder(TestData.command("key-1")));
        String idOriginal = guardado("key-1").getId();
        assertEquals(OrderStatus.PENDING_VALIDATION, guardado("key-1").getStatus());

        coverageCaido = false;
        respuestaCoverage = new CoverageValidationResult(true, Zone.SAME_DEPARTMENT, TestData.fare(), null);
        Order order = useCase.createOrder(TestData.command("key-1"));

        assertEquals(idOriginal, order.getId());
        assertEquals(OrderStatus.VALIDATED, order.getStatus());
        assertEquals(Zone.SAME_DEPARTMENT, order.getZone());
        assertEquals(TestData.fare(), order.getFare());
        assertEquals(OrderStatus.VALIDATED, guardado("key-1").getStatus());
        assertEquals(2, llamadasCoverage.get());
        assertEquals(1, repository.store.size());
    }

    @Test
    void reintentoDePedidoPendiente_destinoInvalido_quedaRechazado() {
        coverageCaido = true;
        assertThrows(CoverageServiceUnavailableException.class,
                () -> useCase.createOrder(TestData.command("key-1")));

        coverageCaido = false;
        respuestaCoverage = new CoverageValidationResult(false, null, null, "El municipio no existe");
        DestinationRejectedException ex = assertThrows(DestinationRejectedException.class,
                () -> useCase.createOrder(TestData.command("key-1")));

        assertEquals("El municipio no existe", ex.getMessage());
        assertEquals(OrderStatus.REJECTED, guardado("key-1").getStatus());
        assertEquals("El municipio no existe", guardado("key-1").getRejectionReason());
        assertEquals(1, repository.store.size());
    }

    @Test
    void reintentoDePedidoPendiente_coverageSigueCaido_lanza503YSiguePendiente() {
        coverageCaido = true;
        assertThrows(CoverageServiceUnavailableException.class,
                () -> useCase.createOrder(TestData.command("key-1")));

        assertThrows(CoverageServiceUnavailableException.class,
                () -> useCase.createOrder(TestData.command("key-1")));

        assertEquals(OrderStatus.PENDING_VALIDATION, guardado("key-1").getStatus());
        assertNull(guardado("key-1").getZone());
        assertNull(guardado("key-1").getFare());
        assertEquals(2, llamadasCoverage.get());
        assertEquals(1, repository.store.size());
    }

    @Test
    void reintentoDePedidoRechazado_lanzaElMensajeOriginalSinLlamarACoverage() {
        respuestaCoverage = new CoverageValidationResult(false, null, null, "El municipio no existe");
        assertThrows(DestinationRejectedException.class,
                () -> useCase.createOrder(TestData.command("key-1")));

        // Aunque Coverage ahora respondiera otra cosa, el rechazo es definitivo
        respuestaCoverage = new CoverageValidationResult(true, Zone.SAME_CITY, TestData.fare(), null);
        DestinationRejectedException ex = assertThrows(DestinationRejectedException.class,
                () -> useCase.createOrder(TestData.command("key-1")));

        assertEquals("El municipio no existe", ex.getMessage());
        assertEquals(OrderStatus.REJECTED, guardado("key-1").getStatus());
        assertEquals(1, llamadasCoverage.get());
        assertEquals(1, repository.store.size());
    }

    @Test
    void reintentoDePedidoConfirmado_devuelveElPedidoSinCambios() {
        repository.save(TestData.orderIn(OrderStatus.CONFIRMED));

        Order order = useCase.createOrder(TestData.command("key-1"));

        assertEquals("order-1", order.getId());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(OrderStatus.CONFIRMED, guardado("key-1").getStatus());
        assertEquals(0, llamadasCoverage.get());
        assertEquals(1, repository.store.size());
    }

    @Test
    void peticionConcurrenteConLaMismaClave_devuelveElPedidoGuardadoPorLaOtra() {
        // La otra petición ya guardó y validó el pedido, pero esta consultó antes de su commit
        respuestaCoverage = new CoverageValidationResult(true, Zone.SAME_CITY, TestData.fare(), null);
        Order ganador = useCase.createOrder(TestData.command("key-1"));
        repository.hiddenIdempotencyLookups = 1;

        Order order = useCase.createOrder(TestData.command("key-1"));

        assertEquals(ganador.getId(), order.getId());
        assertEquals(OrderStatus.VALIDATED, order.getStatus());
        assertEquals(1, llamadasCoverage.get());
        assertEquals(1, repository.store.size());
    }

    @Test
    void peticionConcurrenteConLaMismaClave_pedidoRechazado_lanzaElMensajeOriginal() {
        respuestaCoverage = new CoverageValidationResult(false, null, null, "El municipio no existe");
        assertThrows(DestinationRejectedException.class,
                () -> useCase.createOrder(TestData.command("key-1")));
        repository.hiddenIdempotencyLookups = 1;

        DestinationRejectedException ex = assertThrows(DestinationRejectedException.class,
                () -> useCase.createOrder(TestData.command("key-1")));

        assertEquals("El municipio no existe", ex.getMessage());
        assertEquals(1, llamadasCoverage.get());
        assertEquals(1, repository.store.size());
    }
}
