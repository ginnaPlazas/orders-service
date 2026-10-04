package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.DestinationRejectedException;
import co.andesexpress.orders.domain.model.CreateOrderCommand;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.port.in.CreateOrderInputPort;
import co.andesexpress.orders.domain.port.out.CoverageClientPort;
import co.andesexpress.orders.domain.port.out.CoverageClientPort.CoverageValidationResult;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOrderUseCase implements CreateOrderInputPort {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);

    private final OrderRepositoryPort orderRepository;
    private final CoverageClientPort coverageClient;

    public CreateOrderUseCase(OrderRepositoryPort orderRepository, CoverageClientPort coverageClient) {
        this.orderRepository = orderRepository;
        this.coverageClient = coverageClient;
    }

    @Override
    public Order createOrder(CreateOrderCommand command) {
        // HU-04: idempotencia — si ya existe un pedido con esta clave, lo devolvemos tal cual
        var existing = orderRepository.findByIdempotencyKey(command.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Pedido con idempotencyKey={} ya existía, devolviendo pedido existente id={}",
                command.getIdempotencyKey(), existing.get().getId());
            return existing.get();
        }

        // HU-01: crear el pedido en estado PENDING_VALIDATION
        Order order = new Order(
                command.getIdempotencyKey(),
                command.getOrigin(),
                command.getDestination(),
                command.getProducts()
        );
        order = orderRepository.save(order);

        // HU-05/HU-06: validar cobertura y calcular tarifa vía Coverage
        CoverageValidationResult result = coverageClient.validate(
                order.getOrigin(),
                order.getDestination(),
                order.getTotalWeightKg()
        );

        if (!result.valid()) {
             log.warn("Pedido {} rechazado: {}", order.getId(), result.rejectionMessage());
            // HU-02: destino inválido, rechazo con mensaje claro
            order.markRejected();
            orderRepository.save(order);
            throw new DestinationRejectedException(result.rejectionMessage());
        }

        // Destino válido: pasa a VALIDATED con zona y tarifa
        order.markValidated(result.zone(), result.fare());
        log.info("Pedido {} validado correctamente, zona={}", order.getId(), result.zone());
        return orderRepository.save(order);
    }
}