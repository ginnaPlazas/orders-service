package co.andesexpress.orders.entrypoints.web;

import co.andesexpress.orders.domain.exception.DestinationRejectedException;
import co.andesexpress.orders.domain.exception.OrderNotFoundException;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.port.in.ConfirmOrderInputPort;
import co.andesexpress.orders.domain.port.in.CreateOrderInputPort;
import co.andesexpress.orders.domain.port.in.GetOrderStatusInputPort;
import co.andesexpress.orders.entrypoints.web.dto.CreateOrderRequest;
import co.andesexpress.orders.entrypoints.web.dto.OrderResponse;
import co.andesexpress.orders.entrypoints.web.mapper.OrderWebMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CreateOrderInputPort createOrderInputPort;
    private final ConfirmOrderInputPort confirmOrderInputPort;
    private final GetOrderStatusInputPort getOrderStatusInputPort;
    private final OrderWebMapper mapper;

    public OrderController(CreateOrderInputPort createOrderInputPort,
                            ConfirmOrderInputPort confirmOrderInputPort,
                            GetOrderStatusInputPort getOrderStatusInputPort,
                            OrderWebMapper mapper) {
        this.createOrderInputPort = createOrderInputPort;
        this.confirmOrderInputPort = confirmOrderInputPort;
        this.getOrderStatusInputPort = getOrderStatusInputPort;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = createOrderInputPort.createOrder(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(order));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(@PathVariable String orderId) {
        Order order = confirmOrderInputPort.confirmOrder(orderId);
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderStatus(@PathVariable String orderId) {
        Order order = getOrderStatusInputPort.getOrderStatus(orderId);
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @ExceptionHandler(DestinationRejectedException.class)
    public ResponseEntity<String> handleDestinationRejected(DestinationRejectedException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<String> handleOrderNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}