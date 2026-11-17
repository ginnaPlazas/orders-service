package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.exception.DestinationRejectedException;
import co.andesexpress.orders.domain.exception.ConcurrentOrderUpdateException;
import co.andesexpress.orders.domain.exception.DuplicateIdempotencyKeyException;
import co.andesexpress.orders.domain.model.CreateOrderCommand;
import co.andesexpress.orders.domain.model.Order;
import co.andesexpress.orders.domain.model.OrderStatus;
import co.andesexpress.orders.domain.port.in.CreateOrderInputPort;
import co.andesexpress.orders.domain.port.out.CoverageClientPort;
import co.andesexpress.orders.domain.port.out.CoverageClientPort.CoverageValidationResult;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOrderUseCase implements CreateOrderInputPort {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);

    // Pedidos rechazados antes de guardar el motivo (filas antiguas)
    private static final String DEFAULT_REJECTION_MESSAGE = "El destino fue rechazado por Coverage";

    private final OrderRepositoryPort orderRepository;
    private final CoverageClientPort coverageClient;

    public CreateOrderUseCase(OrderRepositoryPort orderRepository, CoverageClientPort coverageClient) {
        this.orderRepository = orderRepository;
        this.coverageClient = coverageClient;
    }

    @Override
    public Order createOrder(CreateOrderCommand command) {
        // HU-04: idempotencia — si ya existe un pedido con esta clave, se aplican las reglas de reintento
        var existing = orderRepository.findByIdempotencyKey(command.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Pedido con idempotencyKey={} ya existía (id={}, estado={})",
                command.getIdempotencyKey(), existing.get().getId(), existing.get().getStatus());
            return replay(existing.get());
        }

        // HU-01: crear el pedido en estado PENDING_VALIDATION
        Order order = new Order(
                command.getIdempotencyKey(),
                command.getCustomerEmail(),
                command.getOrigin(),
                command.getDestination(),
                command.getProducts(),
                command.getShippingType()
        );
        try {
            order = orderRepository.save(order);
        } catch (DuplicateIdempotencyKeyException e) {
            // Petición concurrente con la misma clave: la otra guardó primero y sigue (o terminó) validando.
            // No se valida otra vez: se devuelve lo que la otra dejó guardado.
            Order stored = orderRepository.findByIdempotencyKey(command.getIdempotencyKey()).orElseThrow(() -> e);
            log.info("Pedido con idempotencyKey={} creado por una petición concurrente (id={})",
                command.getIdempotencyKey(), stored.getId());
            return resolveStored(stored);
        }

        return validate(order);
    }

    /** Resultado de otra petición que ya escribió el pedido: un rechazo se repite, lo demás se devuelve. */
    private Order resolveStored(Order stored) {
        if (stored.getStatus() == OrderStatus.REJECTED) {
            throw rejection(stored);
        }
        return stored;
    }

    private DestinationRejectedException rejection(Order order) {
        String message = order.getRejectionReason() != null ? order.getRejectionReason() : DEFAULT_REJECTION_MESSAGE;
        return new DestinationRejectedException(message);
    }

    /** Guarda el pedido; si otra petición lo escribió al mismo tiempo, gana lo que ella guardó. */
    private Order saveOrReread(Order order) {
        try {
            return orderRepository.save(order);
        } catch (ConcurrentOrderUpdateException e) {
            Order stored = orderRepository.findById(order.getId()).orElseThrow(() -> e);
            log.info("Pedido {} actualizado por una petición concurrente (estado={})", order.getId(), stored.getStatus());
            return resolveStored(stored);
        }
    }

    private Order replay(Order existing) {
        switch (existing.getStatus()) {
            case PENDING_VALIDATION:
                // Coverage falló antes: se vuelve a validar y sigue el flujo normal
                return validate(existing);
            case REJECTED:
                // El rechazo es definitivo: mismo 400 con el mensaje original
                throw rejection(existing);
            default:
                return existing;
        }
    }

    private Order validate(Order order) {
        // HU-05/HU-06: validar cobertura y calcular tarifa vía Coverage.
        // Si Coverage no responde, la excepción (503) sale y el pedido queda en PENDING_VALIDATION.
        CoverageValidationResult result = coverageClient.validate(
                order.getOrigin(),
                order.getDestination(),
                order.getTotalWeightKg()
        );

        if (!result.valid()) {
            String message = result.rejectionMessage() != null ? result.rejectionMessage() : DEFAULT_REJECTION_MESSAGE;
            log.warn("Pedido {} rechazado: {}", order.getId(), message);
            // HU-02: destino inválido, rechazo con mensaje claro (se guarda para los reintentos)
            order.markRejected(message);
            saveOrReread(order);
            throw new DestinationRejectedException(message);
        }

        // Destino válido: pasa a VALIDATED con zona y tarifa
        order.markValidated(result.zone(), result.fare());
        log.info("Pedido {} validado correctamente, zona={}", order.getId(), result.zone());
        return saveOrReread(order);
    }
}
