package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmOrderUseCaseTest {

    FakeOrderRepository repository;
    List<String> eventosPublicados;
    ConfirmOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new FakeOrderRepository();
        eventosPublicados = new ArrayList<>();
        useCase = new ConfirmOrderUseCase(repository, eventosPublicados::add);
    }

    @Test
    void pedidoValidado_seConfirmaYPublicaElEvento() {
        repository.save(TestData.orderIn(OrderStatus.VALIDATED));

        Order order = useCase.confirmOrder("order-1");

        // Si Ginna agrega markGuideGenerating(), el estado final será
        // GUIDE_GENERATING en vez de CONFIRMED; por eso solo comprobamos que ya no es VALIDATED.
        assertEquals(OrderStatus.GUIDE_GENERATING, order.getStatus());
        assertEquals(List.of("order-1"), eventosPublicados);
    }

    @Test
    void pedidoSinValidar_lanzaExcepcionYNoPublicaEvento() {
        repository.save(TestData.orderIn(OrderStatus.PENDING_VALIDATION));

        assertThrows(IllegalStateException.class, () -> useCase.confirmOrder("order-1"));
        assertTrue(eventosPublicados.isEmpty());
    }

    @Test
    void pedidoInexistente_lanzaOrderNotFound() {
        assertThrows(OrderNotFoundException.class, () -> useCase.confirmOrder("no-existe"));
    }
}