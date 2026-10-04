package co.andesexpress.orders.entrypoints.web.mapper;

import co.andesexpress.orders.domain.model.*;
import co.andesexpress.orders.entrypoints.web.dto.CreateOrderRequest;
import co.andesexpress.orders.entrypoints.web.dto.OrderResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderWebMapper {

    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        Address origin = new Address(request.origin.departmentId, request.origin.cityId);
        Address destination = new Address(request.destination.departmentId, request.destination.cityId);
        List<Product> products = request.products.stream()
                .map(p -> new Product(p.name, p.weightKg))
                .collect(Collectors.toList());

        return new CreateOrderCommand(request.idempotencyKey, origin, destination, products);
    }

    public OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.id = order.getId();
        response.status = order.getStatus().name();

        response.origin = new OrderResponse.AddressDto();
        response.origin.departmentId = order.getOrigin().getDepartmentId();
        response.origin.cityId = order.getOrigin().getCityId();

        response.destination = new OrderResponse.AddressDto();
        response.destination.departmentId = order.getDestination().getDepartmentId();
        response.destination.cityId = order.getDestination().getCityId();

        if (order.getZone() != null) {
            response.zone = order.getZone().name();
        }
        if (order.getFare() != null) {
            response.fare = new OrderResponse.FareDto();
            response.fare.amount = order.getFare().getAmount();
            response.fare.currency = order.getFare().getCurrency();
        }
        response.guideUrl = order.getGuideUrl();

        return response;
    }
}