package co.andesexpress.orders.drivenadapters.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outbox_events",
        indexes = @Index(name = "idx_outbox_pending", columnList = "published_at, created_at"))
public class OutboxEventEntity {

    @Id
    private String id;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // null = pendiente de publicar
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public OutboxEventEntity() {} // requerido por JPA

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrderId() {
        return orderId;
    }
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }
    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
