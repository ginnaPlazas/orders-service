package co.andesexpress.orders.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento guardado en el outbox en la misma transacción que el cambio de estado del pedido.
 * Queda pendiente ({@code publishedAt == null}) hasta que el relay lo publica con éxito.
 */
public final class OutboxEvent {

    static final int MAX_ERROR_LENGTH = 1000;

    private final String id;
    private final EventType eventType;
    private final String orderId;
    private final String customerEmail;
    private final Instant createdAt;
    private Instant publishedAt;
    private int attempts;
    private String lastError;

    private OutboxEvent(String id, EventType eventType, String orderId, String customerEmail, Instant createdAt,
                        Instant publishedAt, int attempts, String lastError) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.customerEmail = customerEmail;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.publishedAt = publishedAt;
        this.attempts = attempts;
        this.lastError = lastError;
    }

    public static OutboxEvent pending(EventType eventType, Order order, Instant createdAt) {
        return new OutboxEvent(UUID.randomUUID().toString(), eventType, order.getId(), order.getCustomerEmail(),
                createdAt, null, 0, null);
    }

    public static OutboxEvent reconstruct(String id, EventType eventType, String orderId, String customerEmail,
                                          Instant createdAt, Instant publishedAt, int attempts, String lastError) {
        return new OutboxEvent(id, eventType, orderId, customerEmail, createdAt, publishedAt, attempts, lastError);
    }

    public void markPublished(Instant when) {
        this.attempts++;
        this.publishedAt = Objects.requireNonNull(when, "when");
        this.lastError = null;
    }

    public void registerFailure(String error) {
        this.attempts++;
        String message = (error == null || error.isBlank()) ? "Error desconocido al publicar" : error;
        this.lastError = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    public boolean isPending() {
        return publishedAt == null;
    }

    public String getId() {
        return id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
