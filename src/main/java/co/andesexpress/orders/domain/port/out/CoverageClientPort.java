package co.andesexpress.orders.domain.port.out;

import co.andesexpress.orders.domain.model.Address;
import co.andesexpress.orders.domain.model.Fare;
import co.andesexpress.orders.domain.model.Zone;
import java.math.BigDecimal;

public interface CoverageClientPort {
    CoverageValidationResult validate(Address origin, Address destination, BigDecimal totalWeightKg);

    record CoverageValidationResult(boolean valid, Zone zone, Fare fare, String rejectionMessage) {}
}