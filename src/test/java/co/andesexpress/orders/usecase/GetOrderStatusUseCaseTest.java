package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.OrderView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GetOrderStatusUseCaseTest {

    FakeOrderRepository repository = new FakeOrderRepository();
    FakeGuideUrlSigner signer = new FakeGuideUrlSigner();
    GetOrderStatusUseCase useCase = new GetOrderStatusUseCase(repository, signer);

    @Test
    void pedidoExistente_devuelveSuEstadoYSuRecorrido() {
        repository.save(TestData.orderIn(OrderStatus.CONFIRMED));

        OrderView view = useCase.getOrderStatus("order-1");

        assertEquals(OrderStatus.CONFIRMED, view.order().getStatus());
        assertEquals(5, view.order().getOrigin().getDepartmentId());
        assertEquals(15, view.order().getDestination().getDepartmentId());
    }

    @Test
    void pedidoConGuiaLista_devuelveUrlPrefirmadaDeSuClave() {
        repository.save(TestData.orderReady("guias/order-1.pdf"));

        OrderView view = useCase.getOrderStatus("order-1");

        assertEquals("https://signed.example/guias/order-1.pdf?X-Amz-Signature=abc", view.guideUrl());
        assertEquals("guias/order-1.pdf", view.order().getGuideKey());
        assertEquals(List.of("guias/order-1.pdf"), signer.signedKeys);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "GUIDE_READY", mode = EnumSource.Mode.EXCLUDE)
    void pedidoSinGuiaLista_devuelveGuideUrlNulaSinFirmar(OrderStatus status) {
        repository.save(TestData.orderIn(status));

        OrderView view = useCase.getOrderStatus("order-1");

        assertEquals(status, view.order().getStatus());
        assertNull(view.guideUrl());
        assertTrue(signer.signedKeys.isEmpty());
    }

    @Test
    void toView_firmaLaGuiaDeUnPedidoYaCargado() {
        OrderView view = useCase.toView(TestData.orderReady("guias/x.pdf"));

        assertEquals("https://signed.example/guias/x.pdf?X-Amz-Signature=abc", view.guideUrl());
        assertEquals(List.of("guias/x.pdf"), signer.signedKeys);
    }

    @Test
    void pedidoInexistente_lanzaOrderNotFound() {
        assertThrows(OrderNotFoundException.class, () -> useCase.getOrderStatus("no-existe"));
        assertTrue(signer.signedKeys.isEmpty());
    }
}
