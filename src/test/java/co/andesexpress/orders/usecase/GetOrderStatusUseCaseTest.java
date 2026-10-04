package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GetOrderStatusUseCaseTest {

    FakeOrderRepository repository = new FakeOrderRepository();
    GetOrderStatusUseCase useCase = new GetOrderStatusUseCase(repository);

    @Test
    void pedidoExistente_devuelveSuEstadoYSuRecorrido() {
        repository.save(TestData.orderIn(OrderStatus.CONFIRMED));

        Order order = useCase.getOrderStatus("order-1");

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(5, order.getOrigin().getDepartmentId());
        assertEquals(15, order.getDestination().getDepartmentId());
    }

    @Test
    void pedidoInexistente_lanzaOrderNotFound() {
        assertThrows(OrderNotFoundException.class, () -> useCase.getOrderStatus("no-existe"));
    }
}