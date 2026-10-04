package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkGuideReadyUseCaseTest {

    FakeOrderRepository repository = new FakeOrderRepository();
    MarkGuideReadyUseCase useCase = new MarkGuideReadyUseCase(repository);

    @Test
    void pedidoGenerandoGuia_quedaConGuiaListaYUrl() {
        repository.save(TestData.orderIn(OrderStatus.GUIDE_GENERATING));

        Order order = useCase.markGuideReady("order-1", "https://ejemplo.com/guia.pdf");

        assertEquals(OrderStatus.GUIDE_READY, order.getStatus());
        assertEquals("https://ejemplo.com/guia.pdf", order.getGuideUrl());
    }

    @Test
    void pedidoSoloConfirmado_lanzaExcepcionDeEstado() {
        repository.save(TestData.orderIn(OrderStatus.CONFIRMED));

        assertThrows(IllegalStateException.class,
                () -> useCase.markGuideReady("order-1", "https://ejemplo.com/guia.pdf"));
    }

    @Test
    void pedidoInexistente_lanzaOrderNotFound() {
        assertThrows(OrderNotFoundException.class,
                () -> useCase.markGuideReady("no-existe", "https://ejemplo.com/guia.pdf"));
    }
}