package co.andesexpress.orders.domain.model;

public enum OrderStatus {
    PENDING_VALIDATION,
    REJECTED,
    VALIDATED,
    CONFIRMED,
    GUIDE_GENERATING,
    GUIDE_READY
}