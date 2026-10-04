package co.andesexpress.orders.drivenadapters.coverageclient.dto;

import java.math.BigDecimal;

public class CoverageRequestDto {
    public OriginDestination origin;
    public OriginDestination destination;
    public BigDecimal totalWeightKg;

    public static class OriginDestination {
        public int departmentId;
        public int cityId;

        public OriginDestination(int departmentId, int cityId) {
            this.departmentId = departmentId;
            this.cityId = cityId;
        }
    }
}