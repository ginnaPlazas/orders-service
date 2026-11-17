package co.andesexpress.orders.entrypoints.web.mapper;

import co.andesexpress.orders.domain.model.Address;
import co.andesexpress.orders.domain.model.CreateOrderCommand;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.model.OrderView;
import co.andesexpress.orders.domain.model.Product;
import co.andesexpress.orders.domain.model.ShippingType;
import co.andesexpress.orders.entrypoints.web.dto.CreateOrderRequest;
import co.andesexpress.orders.entrypoints.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderWebMapperTest {

    private final OrderWebMapper mapper = new OrderWebMapper();

    private static CreateOrderRequest request(ShippingType shippingType) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.idempotencyKey = "key-1";
        request.customerEmail = "cliente@andesexpress.com";
        request.origin = new CreateOrderRequest.AddressDto();
        request.origin.departmentId = 5;
        request.origin.cityId = 1;
        request.destination = new CreateOrderRequest.AddressDto();
        request.destination.departmentId = 15;
        request.destination.cityId = 2;
        CreateOrderRequest.ProductDto product = new CreateOrderRequest.ProductDto();
        product.name = "Caja";
        product.weightKg = new BigDecimal("2.5");
        request.products = List.of(product);
        request.shippingType = shippingType;
        return request;
    }

    @Test
    void peticionSinTipoDeEnvio_generaComandoStandard() {
        CreateOrderCommand command = mapper.toCommand(request(null));

        assertEquals(ShippingType.STANDARD, command.getShippingType());
    }

    @Test
    void peticionExpress_generaComandoExpress() {
        CreateOrderCommand command = mapper.toCommand(request(ShippingType.EXPRESS));

        assertEquals(ShippingType.EXPRESS, command.getShippingType());
    }

    @Test
    void respuesta_incluyeElTipoDeEnvio() {
        Order order = Order.reconstruct("order-1", "key-1", "cliente@andesexpress.com", new Address(5, 1),
                new Address(15, 2), List.of(new Product("Caja", new BigDecimal("2.5"))), ShippingType.EXPRESS,
                OrderStatus.VALIDATED, null, null, null, null);

        OrderResponse response = mapper.toResponse(new OrderView(order, null));

        assertEquals("EXPRESS", response.shippingType);
        assertEquals("VALIDATED", response.status);
    }
}
